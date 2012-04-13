/*
 *
 *  UserGroupPermissionsResource.java - A Restlet to read User Group data with Permissions from SipXecs
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
import org.sipfoundry.sipxconfig.permission.PermissionName;
import org.sipfoundry.sipxconfig.rest.RestUtilities.BranchRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SettingBooleanRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.UserGroupPermissionRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.UserGroupRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.sipfoundry.sipxconfig.setting.Group;
import org.sipfoundry.sipxconfig.setting.SettingDao;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class UserGroupPermissionsResource extends UserResource {

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
        UserGroupPermissionRestInfoFull userGroupPermissionRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                userGroupPermissionRestInfo = createUserGroupPermissionRestInfo(idInt);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_READ_FAILED, "Read User Group failed", exception.getLocalizedMessage());
            }

            return new UserGroupPermissionRepresentation(variant.getMediaType(), userGroupPermissionRestInfo);
        }


        // if not single, process request for all
        List<Group> userGroups = getCoreContext().getGroups(); // settingsContext.getGroups() requires Resource string value

        List<UserGroupPermissionRestInfoFull> userGroupPermissionsRestInfo = new ArrayList<UserGroupPermissionRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort if specified
        sortUserGroups(userGroups);

        // set requested items and get resulting metadata
        metadataRestInfo = addUserGroups(userGroupPermissionsRestInfo, userGroups);

        // create final restinfo
        UserGroupPermissionsBundleRestInfo userGroupPermissionsBundleRestInfo = new UserGroupPermissionsBundleRestInfo(userGroupPermissionsRestInfo, metadataRestInfo);

        return new UserGroupPermissionsRepresentation(variant.getMediaType(), userGroupPermissionsBundleRestInfo);
    }

    // PUT - Update or Add single Skill
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        UserGroupPermissionRepresentation representation = new UserGroupPermissionRepresentation(entity);
        UserGroupPermissionRestInfoFull userGroupPermissionRestInfo = representation.getObject();
        Group userGroup = null;

        // validate input for update or create
        ValidationInfo validationInfo = validate(userGroupPermissionRestInfo);

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
                updateUserGroupPermission(userGroup, userGroupPermissionRestInfo);
                m_settingContext.saveGroup(userGroup);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update User Group Permissions failed", exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_UPDATED, "Updated User Group Permissions", userGroup.getId());

            return;
        }


        // otherwise add new
        try {
            userGroup = createUserGroupPermission(userGroupPermissionRestInfo);
            m_settingContext.saveGroup(userGroup);
        }
        catch (Exception exception) {
            RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create User Group Permissions failed", exception.getLocalizedMessage());
            return;
        }

        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_CREATED, "Created User Group permissions", userGroup.getId());
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

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_DELETED, "Deleted User Group Permissions", userGroup.getId());

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
    private ValidationInfo validate(UserGroupPermissionRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        return validationInfo;
    }

    private UserGroupPermissionRestInfoFull createUserGroupPermissionRestInfo(int id) {
        Group group = m_settingContext.getGroup(id);

        return createUserGroupPermissionRestInfo(group);
    }

    private UserGroupPermissionRestInfoFull createUserGroupPermissionRestInfo(Group group) {
        UserGroupPermissionRestInfoFull userGroupPermissionRestInfo = null;
        List<SettingBooleanRestInfo> settings = new ArrayList<SettingBooleanRestInfo>();

        settings.add(createSettingRestInfo(group));

        userGroupPermissionRestInfo = new UserGroupPermissionRestInfoFull(group, settings);

        return userGroupPermissionRestInfo;
    }

    private SettingBooleanRestInfo createSettingRestInfo(Group group) {
        SettingBooleanRestInfo settingRestInfo = null;
        String valueString;
        Boolean value;

        //value = (Boolean) group.getSettingTypedValue(new BooleanSetting(), PermissionName.SUBSCRIBE_TO_PRESENCE.getPath());
        valueString = group.getSettingValue(PermissionName.SUBSCRIBE_TO_PRESENCE.getPath());
        value = Boolean.parseBoolean(valueString);

        settingRestInfo = new SettingBooleanRestInfo(PermissionName.SUBSCRIBE_TO_PRESENCE.getName(), value);

        return settingRestInfo;
    }

    private MetadataRestInfo addUserGroups(List<UserGroupPermissionRestInfoFull> userGroupPermissionsRestInfo, List<Group> userGroups) {
        UserGroupPermissionRestInfoFull userGroupPermissionRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, userGroups.size());

        // create list of restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            Group userGroup = userGroups.get(index);

            userGroupPermissionRestInfo = createUserGroupPermissionRestInfo(userGroup);
            userGroupPermissionsRestInfo.add(userGroupPermissionRestInfo);
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

    private void updateUserGroupPermission(Group userGroup, UserGroupPermissionRestInfoFull userGroupPermissionRestInfo) {
        String tempString;

        // do not allow empty name
        tempString = userGroupPermissionRestInfo.getName();
        if (!tempString.isEmpty()) {
            userGroup.setName(tempString);
        }

        userGroup.setDescription(userGroupPermissionRestInfo.getDescription());
    }

    private Group createUserGroupPermission(UserGroupPermissionRestInfoFull userGroupPermissionRestInfo) {
        Group userGroup = new Group();

        // copy fields from rest info
        userGroup.setName(userGroupPermissionRestInfo.getName());
        userGroup.setDescription(userGroupPermissionRestInfo.getDescription());

        // apparently there is a special Resource value for user groups
        userGroup.setResource(CoreContext.USER_GROUP_RESOURCE_ID);

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

    static class UserGroupPermissionsRepresentation extends XStreamRepresentation<UserGroupPermissionsBundleRestInfo> {

        public UserGroupPermissionsRepresentation(MediaType mediaType, UserGroupPermissionsBundleRestInfo object) {
            super(mediaType, object);
        }

        public UserGroupPermissionsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("user-group-permission", UserGroupPermissionsBundleRestInfo.class);
            xstream.alias("userGroup", UserGroupPermissionRestInfoFull.class);
            xstream.alias("setting", SettingBooleanRestInfo.class);
        }
    }

    static class UserGroupPermissionRepresentation extends XStreamRepresentation<UserGroupPermissionRestInfoFull> {

        public UserGroupPermissionRepresentation(MediaType mediaType, UserGroupPermissionRestInfoFull object) {
            super(mediaType, object);
        }

        public UserGroupPermissionRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("userGroup", UserGroupPermissionRestInfoFull.class);
            xstream.alias("setting", SettingBooleanRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class UserGroupPermissionsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<UserGroupPermissionRestInfoFull> m_userGroupPermissions;

        public UserGroupPermissionsBundleRestInfo(List<UserGroupPermissionRestInfoFull> userGroupPermissions, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_userGroupPermissions = userGroupPermissions;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<UserGroupPermissionRestInfoFull> getUserGroupPermissions() {
            return m_userGroupPermissions;
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
