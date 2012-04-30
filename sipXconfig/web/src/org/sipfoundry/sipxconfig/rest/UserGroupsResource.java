/*
 *
 *  UserGroupsResource.java - A Restlet to read User Group data from SipXecs
 *  Copyright (C) 2012 PATLive, D. Chang
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.

 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.sipfoundry.sipxconfig.rest;

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.TEXT_XML;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.branch.Branch;
import org.sipfoundry.sipxconfig.branch.BranchManager;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.rest.RestUtilities.BranchRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.UserGroupRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.sipfoundry.sipxconfig.setting.Group;
import org.sipfoundry.sipxconfig.setting.SettingDao;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class UserGroupsResource extends UserResource {

    private SettingDao m_settingContext; // saveGroup is not available through corecontext
    private BranchManager m_branchManager;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField {
        NAME, DESCRIPTION, NONE;

        public static SortField toSortField(String fieldString) {
            if (fieldString == null) {
                return NONE;
            }

            try {
                return valueOf(fieldString.toUpperCase());
            }
            catch (Exception ex) {
                return NONE;
            }
        }
    }


    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        getVariants().add(new Variant(TEXT_XML));
        getVariants().add(new Variant(APPLICATION_JSON));

        // pull parameters from url
        m_form = getRequest().getResourceRef().getQueryAsForm();
    }


    // Allowed REST operations
    // -----------------------

    @Override
    public boolean allowGet() {
        return true;
    }

    @Override
    public boolean allowPut() {
        return true;
    }

    @Override
    public boolean allowDelete() {
        return true;
    }

    // GET - Retrieve all and single Skill
    // -----------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        int idInt;
        UserGroupRestInfoFull userGroupRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                userGroupRestInfo = createUserGroupRestInfo(idInt);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_READ_FAILED, "Read User Group failed", exception.getLocalizedMessage());
            }

            return new UserGroupRepresentation(variant.getMediaType(), userGroupRestInfo);
        }


        // if not single, process request for all
        List<Group> userGroups = getCoreContext().getGroups(); // settingsContext.getGroups() requires Resource string value

        List<UserGroupRestInfoFull> userGroupsRestInfo = new ArrayList<UserGroupRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort if specified
        sortUserGroups(userGroups);

        // set requested items and get resulting metadata
        metadataRestInfo = addUserGroups(userGroupsRestInfo, userGroups);

        // create final restinfo
        UserGroupsBundleRestInfo userGroupsBundleRestInfo = new UserGroupsBundleRestInfo(userGroupsRestInfo, metadataRestInfo);

        return new UserGroupsRepresentation(variant.getMediaType(), userGroupsBundleRestInfo);
    }

    // PUT - Update or Add single Skill
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        UserGroupRepresentation representation = new UserGroupRepresentation(entity);
        UserGroupRestInfoFull userGroupRestInfo = representation.getObject();
        Group userGroup = null;

        // validate input for update or create
        ValidationInfo validationInfo = validate(userGroupRestInfo);

        if (!validationInfo.valid) {
            RestUtilities.setResponseError(getResponse(), validationInfo.responseCode, validationInfo.message);
            return;
        }


        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                userGroup = m_settingContext.getGroup(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing
            try {
                updateUserGroup(userGroup, userGroupRestInfo);
                m_settingContext.saveGroup(userGroup);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update User Group failed", exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_UPDATED, "Updated User Group", userGroup.getId());

            return;
        }


        // otherwise add new
        try {
            userGroup = createUserGroup(userGroupRestInfo);
            m_settingContext.saveGroup(userGroup);
        }
        catch (Exception exception) {
            RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create User Group failed", exception.getLocalizedMessage());
            return;
        }

        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_CREATED, "Created User Group", userGroup.getId());
    }


    // DELETE - Delete single Skill
    // ----------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        Group userGroup;
        int idInt;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
                userGroup = m_settingContext.getGroup(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            List<Integer> userGroupIds = new ArrayList<Integer>();
            userGroupIds.add(idInt);
            m_settingContext.deleteGroups(userGroupIds);

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_DELETED, "Deleted User Group", userGroup.getId());

            return;
        }

        // no id string
        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.ERROR_MISSING_INPUT, "ID value missing");
    }


    // Helper functions
    // ----------------

    // basic interface level validation of data provided through REST interface for creation or
    // update
    // may also contain clean up of input data
    // may create another validation function if different rules needed for update v. create
    private ValidationInfo validate(UserGroupRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        return validationInfo;
    }

    private UserGroupRestInfoFull createUserGroupRestInfo(int id) {
        Group group = m_settingContext.getGroup(id);

        return createUserGroupRestInfo(group);
    }

    private UserGroupRestInfoFull createUserGroupRestInfo(Group group) {
        UserGroupRestInfoFull userGroupRestInfo = null;
        BranchRestInfoFull branchRestInfo = null;
        Branch branch = null;

        // group may not have branch assigned
        branch = group.getBranch();
        if (branch != null) {
            branchRestInfo = createBranchRestInfo(branch.getId());
        }

        userGroupRestInfo = new UserGroupRestInfoFull(group, branchRestInfo);

        return userGroupRestInfo;
    }

    private BranchRestInfoFull createBranchRestInfo(int id) {
        BranchRestInfoFull branchRestInfo = null;

        Branch branch = m_branchManager.getBranch(id);
        branchRestInfo = new BranchRestInfoFull(branch);

        return branchRestInfo;
    }

    private MetadataRestInfo addUserGroups(List<UserGroupRestInfoFull> userGroupsRestInfo, List<Group> userGroups) {
        UserGroupRestInfoFull userGroupRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, userGroups.size());

        // create list of restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            Group userGroup = userGroups.get(index);

            userGroupRestInfo = createUserGroupRestInfo(userGroup);
            userGroupsRestInfo.add(userGroupRestInfo);
        }

        // create metadata about restinfos
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortUserGroups(List<Group> userGroups) {
        // sort if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(userGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Group group1 = (Group) object1;
                        Group group2 = (Group) object2;
                        return group1.getName().compareToIgnoreCase(group2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(userGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Group group1 = (Group) object1;
                        Group group2 = (Group) object2;
                        return group1.getDescription().compareToIgnoreCase(group2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(userGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Group group1 = (Group) object1;
                        Group group2 = (Group) object2;
                        return group2.getName().compareToIgnoreCase(group1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(userGroups, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Group group1 = (Group) object1;
                        Group group2 = (Group) object2;
                        return group2.getDescription().compareToIgnoreCase(group1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateUserGroup(Group userGroup, UserGroupRestInfoFull userGroupRestInfo) {
        Branch branch;
        String tempString;

        // do not allow empty name
        tempString = userGroupRestInfo.getName();
        if (!tempString.isEmpty()) {
            userGroup.setName(tempString);
        }

        userGroup.setDescription(userGroupRestInfo.getDescription());

        branch = getBranch(userGroupRestInfo);
        userGroup.setBranch(branch);
    }

    private Group createUserGroup(UserGroupRestInfoFull userGroupRestInfo) {
        Branch branch = null;
        Group userGroup = new Group();

        // copy fields from rest info
        userGroup.setName(userGroupRestInfo.getName());
        userGroup.setDescription(userGroupRestInfo.getDescription());

        // apparently there is a special Resource value for user groups
        userGroup.setResource(CoreContext.USER_GROUP_RESOURCE_ID);

        branch = getBranch(userGroupRestInfo);
        userGroup.setBranch(branch);

        return userGroup;
    }

    private Branch getBranch(UserGroupRestInfoFull userGroupRestInfo) {
        Branch branch = null;
        BranchRestInfoFull branchRestInfo = userGroupRestInfo.getBranch();

        if (branchRestInfo != null) {
            branch = m_branchManager.getBranch(branchRestInfo.getId());
        }

        return branch;
    }


    // REST Representations
    // --------------------

    static class UserGroupsRepresentation extends XStreamRepresentation<UserGroupsBundleRestInfo> {

        public UserGroupsRepresentation(MediaType mediaType, UserGroupsBundleRestInfo object) {
            super(mediaType, object);
        }

        public UserGroupsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("user-group", UserGroupsBundleRestInfo.class);
            xstream.alias("group", UserGroupRestInfoFull.class);
        }
    }

    static class UserGroupRepresentation extends XStreamRepresentation<UserGroupRestInfoFull> {

        public UserGroupRepresentation(MediaType mediaType, UserGroupRestInfoFull object) {
            super(mediaType, object);
        }

        public UserGroupRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("group", UserGroupRestInfoFull.class);
        }
    }


    // REST info objects
    // -----------------

    static class UserGroupsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<UserGroupRestInfoFull> m_groups;

        public UserGroupsBundleRestInfo(List<UserGroupRestInfoFull> userGroups, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_groups = userGroups;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<UserGroupRestInfoFull> getGroups() {
            return m_groups;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setSettingDao(SettingDao settingContext) {
        m_settingContext = settingContext;
    }

    @Required
    public void setBranchManager(BranchManager branchManager) {
        m_branchManager = branchManager;
    }

}
