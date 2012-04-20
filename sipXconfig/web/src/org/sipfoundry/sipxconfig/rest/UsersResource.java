/*
 *
 *  UsersResource.java - A Restlet to read User data from SipXecs
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.branch.Branch;
import org.sipfoundry.sipxconfig.branch.BranchManager;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.rest.RestUtilities.AliasRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.BranchRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.BranchRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.UserGroupRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.UserRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.sipfoundry.sipxconfig.setting.Group;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class UsersResource extends UserResource {

    private BranchManager m_branchManager;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField {
        USERNAME, LASTNAME, FIRSTNAME, NONE;

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

    // GET - Retrieve all and single User
    // ----------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        int idInt;
        UserRestInfoFull userRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                userRestInfo = createUserRestInfo(idInt);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_READ_FAILED, "Read User failed", exception.getLocalizedMessage());
            }

            return new UserRepresentation(variant.getMediaType(), userRestInfo);
        }


        // if not single, check if need to filter list
        List<User> users;
        Collection<Integer> userIds;

        String branchIdString = m_form.getFirstValue("branch");
        String idListString = m_form.getFirstValue("ids");
        int branchId;

        // check if searching by branch
        if ((branchIdString != null) && (!branchIdString.isEmpty())) {
            try {
                branchId = RestUtilities.getIntFromAttribute(branchIdString);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "Branch ID " + branchIdString + " not found.");
            }

            userIds = getCoreContext().getBranchMembersByPage(branchId, 0, getCoreContext().getBranchMembersCount(branchId));
            users = getUsers(userIds);
        }
        else if ((idListString != null) && (!idListString.isEmpty())) {
            // searching by id list
            String[] idArray = idListString.split(",");

            users = new ArrayList<User>();
            User user;
            for (String id : idArray) {
                try {
                    idInt = RestUtilities.getIntFromAttribute(id);
                }
                catch (Exception exception) {
                    return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + id + " not found.");
                }

                user = getCoreContext().getUser(idInt);
                users.add(user);
            }
        }
        else {
            // process request for all
            users = getCoreContext().loadUsersByPage(1, getCoreContext().getAllUsersCount()); // no GetUsers() in coreContext, instead some subgroups
        }

        List<UserRestInfoFull> usersRestInfo = new ArrayList<UserRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort if specified
        sortUsers(users);

        // set requested items and get resulting metadata
        metadataRestInfo = addUsers(usersRestInfo, users);

        // create final restinfo
        UsersBundleRestInfo usersBundleRestInfo = new UsersBundleRestInfo(usersRestInfo, metadataRestInfo);

        return new UsersRepresentation(variant.getMediaType(), usersBundleRestInfo);
    }

    // PUT - Update or Add single User
    // -------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        UserRepresentation representation = new UserRepresentation(entity);
        UserRestInfoFull userRestInfo = representation.getObject();
        User user = null;

        // validate input for update or create
        ValidationInfo validationInfo = validate(userRestInfo);

        if (!validationInfo.valid) {
            RestUtilities.setResponseError(getResponse(), validationInfo.responseCode, validationInfo.message);
            return;
        }


        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                user = getCoreContext().getUser(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing
            try {
                updateUser(user, userRestInfo);
                getCoreContext().saveUser(user);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update User failed", exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_UPDATED, "Updated User", user.getId());

            return;
        }


        // otherwise add new
        try {
            user = createUser(userRestInfo);
            getCoreContext().saveUser(user);
        }
        catch (Exception exception) {
            RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create User failed", exception.getLocalizedMessage());
            return;
        }

        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_CREATED, "Created User", user.getId());
    }


    // DELETE - Delete single User
    // ---------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        User user;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                user = getCoreContext().getUser(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            getCoreContext().deleteUser(user);

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_DELETED, "Deleted User", user.getId());

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
    private ValidationInfo validate(UserRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        return validationInfo;
    }

    private UserRestInfoFull createUserRestInfo(int id) throws ResourceException {
        User user = getCoreContext().getUser(id);

        if (user == null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "No user with id " + id);
        }

        return createUserRestInfo(user);
    }

    private UserRestInfoFull createUserRestInfo(User user) {
        UserRestInfoFull userRestInfo = null;
        UserGroupRestInfo userGroupRestInfo = null;
        List<UserGroupRestInfo> userGroupsRestInfo = new ArrayList<UserGroupRestInfo>();
        Set<Group> groups = null;
        BranchRestInfo branchRestInfo = null;
        Branch branch = null;
        AliasRestInfo aliasRestInfo = null;
        List<AliasRestInfo> aliasesRestInfo = new ArrayList<AliasRestInfo>();
        Set<String> aliases = null;

        groups = user.getGroups();

        // user does not necessarily have any groups
        if ((groups != null) && (!groups.isEmpty())) {
            for (Group group : groups) {
                userGroupRestInfo = new UserGroupRestInfo(group);
                userGroupsRestInfo.add(userGroupRestInfo);
            }
        }

        branch = user.getBranch();

        // user does not necessarily have branch
        if (branch != null) {
            branchRestInfo = new BranchRestInfo(branch);
        }

        aliases = user.getAliases();

        // user does not necessarily have any aliases
        if (aliases != null) {
            for (String alias : aliases) {
                aliasRestInfo = new AliasRestInfo(alias);
                aliasesRestInfo.add(aliasRestInfo);
            }
        }

        userRestInfo = new UserRestInfoFull(user, userGroupsRestInfo, branchRestInfo, aliasesRestInfo);

        return userRestInfo;
    }

    private MetadataRestInfo addUsers(List<UserRestInfoFull> usersRestInfo, List<User> users) {
        UserRestInfoFull userRestInfo;
        User user;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, users.size());


        // create list of skill restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            user = users.get(index);

            userRestInfo = createUserRestInfo(user);
            usersRestInfo.add(userRestInfo);
        }


        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortUsers(List<User> users) {
        // sort groups if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case USERNAME:
                Collections.sort(users, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        User user1 = (User) object1;
                        User user2 = (User) object2;
                        return user1.getUserName().compareToIgnoreCase(user2.getUserName());
                    }

                });
                break;

            case LASTNAME:
                Collections.sort(users, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        User user1 = (User) object1;
                        User user2 = (User) object2;
                        return user1.getLastName().compareToIgnoreCase(user2.getLastName());
                    }

                });
                break;


            case FIRSTNAME:
                Collections.sort(users, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        User user1 = (User) object1;
                        User user2 = (User) object2;
                        return user1.getFirstName().compareToIgnoreCase(user2.getFirstName());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case USERNAME:
                Collections.sort(users, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        User user1 = (User) object1;
                        User user2 = (User) object2;
                        return user2.getUserName().compareToIgnoreCase(user1.getUserName());
                    }

                });
                break;

            case LASTNAME:
                Collections.sort(users, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        User user1 = (User) object1;
                        User user2 = (User) object2;
                        return user2.getLastName().compareToIgnoreCase(user1.getLastName());
                    }

                });
                break;

            case FIRSTNAME:
                Collections.sort(users, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        User user1 = (User) object1;
                        User user2 = (User) object2;
                        return user2.getFirstName().compareToIgnoreCase(user1.getFirstName());
                    }

                });
                break;
            }
        }
    }

    private List<User> getUsers(Collection<Integer> userIds) {
        List<User> users;

        users = new ArrayList<User>();
        for (int userId : userIds) {
            users.add(getCoreContext().getUser(userId));
        }

        return users;
    }

    private void updateUser(User user, UserRestInfoFull userRestInfo) {
        Branch branch;
        String tempString;

        // do not allow empty username
        tempString = userRestInfo.getUserName();
        if (!tempString.isEmpty()) {
            user.setUserName(tempString);
        }

        user.setLastName(userRestInfo.getLastName());
        user.setFirstName(userRestInfo.getFirstName());
        user.setSipPassword(userRestInfo.getSipPassword());

        // if pin is empty do not save
        if (!userRestInfo.getPin().isEmpty()) {
            user.setPin(userRestInfo.getPin(), getCoreContext().getAuthorizationRealm());
        }

        // user may not have any groups
        List<UserGroupRestInfo> userGroupsRestInfo = userRestInfo.getGroups();
        if (userGroupsRestInfo != null) {
            user.setGroups(createUserGroups(userRestInfo));
        }
        else {
            user.setGroups(null);
        }

        // user may not have a branch
        if (userRestInfo.getBranch() != null) {
            branch = m_branchManager.getBranch(userRestInfo.getBranch().getId());
            user.setBranch(branch);
        }
        else {
            user.setBranch(null);
        }

        // user may not have any aliases
        if (userRestInfo.getAliases() != null) {
            user.setAliases(createAliases(userRestInfo));
        }
        else {
            user.setAliases(null);
        }
    }

    private User createUser(UserRestInfoFull userRestInfo) {
        User user = getCoreContext().newUser();
        Branch branch;

        user.setUserName(userRestInfo.getUserName());
        user.setLastName(userRestInfo.getLastName());
        user.setFirstName(userRestInfo.getFirstName());
        user.setSipPassword(userRestInfo.getSipPassword());

        // if pin is empty do not save
        if (!userRestInfo.getPin().isEmpty()) {
            user.setPin(userRestInfo.getPin(), getCoreContext().getAuthorizationRealm());
        }

        // user may not have any groups
        List<UserGroupRestInfo> userGroupsRestInfo = userRestInfo.getGroups();
        if (userGroupsRestInfo != null) {
            user.setGroups(createUserGroups(userRestInfo));
        }

        // user may not have a branch
        if (userRestInfo.getBranch() != null) {
            branch = m_branchManager.getBranch(userRestInfo.getBranch().getId());
            user.setBranch(branch);
        }

        // user may not have any aliases
        if (userRestInfo.getAliases() != null) {
            user.setAliases(createAliases(userRestInfo));
        }

        return user;
    }

    private Set<Group> createUserGroups(UserRestInfoFull userRestInfo) {
        Set<Group> userGroups = new TreeSet<Group>();
        Group userGroup;

        for (UserGroupRestInfo userGroupRestInfo : userRestInfo.getGroups()) {
            userGroup = getCoreContext().getGroupById(userGroupRestInfo.getId());
            userGroups.add(userGroup);
        }

        return userGroups;
    }

    private Set<String> createAliases(UserRestInfoFull userRestInfo) {
        Set<String> aliases = new LinkedHashSet<String>();

        for (AliasRestInfo aliasRestInfo : userRestInfo.getAliases()) {
            aliases.add(aliasRestInfo.getAlias());
        }

        return aliases;
    }


    // REST Representations
    // --------------------

    static class UsersRepresentation extends XStreamRepresentation<UsersBundleRestInfo> {

        public UsersRepresentation(MediaType mediaType, UsersBundleRestInfo object) {
            super(mediaType, object);
        }

        public UsersRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("user", UsersBundleRestInfo.class);
            xstream.alias("user", UserRestInfoFull.class);
            xstream.alias("group", UserGroupRestInfo.class);
            xstream.alias("branch", BranchRestInfoFull.class);
            xstream.alias("alias", AliasRestInfo.class);
        }
    }

    static class UserRepresentation extends XStreamRepresentation<UserRestInfoFull> {

        public UserRepresentation(MediaType mediaType, UserRestInfoFull object) {
            super(mediaType, object);
        }

        public UserRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("group", UserGroupRestInfo.class);
            xstream.alias("user", UserRestInfoFull.class);
            xstream.alias("branch", BranchRestInfoFull.class);
            xstream.alias("alias", AliasRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class UsersBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<UserRestInfoFull> m_users;

        public UsersBundleRestInfo(List<UserRestInfoFull> users, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_users = users;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<UserRestInfoFull> getSkills() {
            return m_users;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setBranchManager(BranchManager branchManager) {
        m_branchManager = branchManager;
    }

}
