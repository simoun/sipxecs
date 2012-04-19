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
import java.util.Collection;
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
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.permission.Permission;
import org.sipfoundry.sipxconfig.permission.PermissionManager;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SettingBooleanRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class UserPermissionsResource extends UserResource {

    //private SettingDao m_settingContext; // saveGroup is not available through corecontext
    private PermissionManager m_permissionManager;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField {
        LASTNAME, FIRSTNAME, NONE;

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

    // GET - Retrieve all and single User with Permissions
    // ---------------------------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        int idInt;
        UserPermissionRestInfoFull userPermissionRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                userPermissionRestInfo = createUserPermissionRestInfo(idInt);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_READ_FAILED, "Read User failed", exception.getLocalizedMessage());
            }

            return new UserPermissionRepresentation(variant.getMediaType(), userPermissionRestInfo);
        }


        // if not single, process request for all
        List<User> users = getCoreContext().getSharedUsers(); // no GetUsers() in coreContext, instead some subgroups

        List<UserPermissionRestInfoFull> userPermissionsRestInfo = new ArrayList<UserPermissionRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort if specified
        sortUsers(users);

        // set requested items and get resulting metadata
        metadataRestInfo = addUsers(userPermissionsRestInfo, users);

        // create final restinfo
        UserPermissionsBundleRestInfo userPermissionsBundleRestInfo = new UserPermissionsBundleRestInfo(userPermissionsRestInfo, metadataRestInfo);

        return new UserPermissionsRepresentation(variant.getMediaType(), userPermissionsBundleRestInfo);
    }


    // PUT - Update Permissions
    // ------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        UserPermissionRepresentation representation = new UserPermissionRepresentation(entity);
        UserPermissionRestInfoFull userPermissionRestInfo = representation.getObject();
        User user = null;

        // validate input for update or create
        ValidationInfo validationInfo = validate(userPermissionRestInfo);

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
                updateUserPermission(user, userPermissionRestInfo);
                getCoreContext().saveUser(user);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update User Permissions failed", exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_UPDATED, "Updated User Permissions", user.getId());

            return;
        }


        // otherwise error, since no creation of new permissions
        RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "Missing ID");
    }


    // Helper functions
    // ----------------

    // basic interface level validation of data provided through REST interface for creation or
    // update
    // may also contain clean up of input data
    // may create another validation function if different rules needed for update v. create
    private ValidationInfo validate(UserPermissionRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        return validationInfo;
    }

    private UserPermissionRestInfoFull createUserPermissionRestInfo(int id) {
        User user = getCoreContext().getUser(id);

        return createUserPermissionRestInfo(user);
    }

    private UserPermissionRestInfoFull createUserPermissionRestInfo(User user) {
        UserPermissionRestInfoFull userPermissionRestInfo = null;
        List<SettingBooleanRestInfo> settings;

        settings = createSettingsRestInfo(user);
        userPermissionRestInfo = new UserPermissionRestInfoFull(user, settings);

        return userPermissionRestInfo;
    }

    private List<SettingBooleanRestInfo> createSettingsRestInfo(User user) {
        List<SettingBooleanRestInfo> settings = new ArrayList<SettingBooleanRestInfo>();
        SettingBooleanRestInfo settingRestInfo = null;
        Collection<Permission> permissions;
        String permissionName;
        String permissionValue;
        boolean defaultValue;

        permissions = m_permissionManager.getPermissions();

        // settings value for permissions are ENABLE or DISABLE instead of boolean
        for (Permission permission : permissions) {
            permissionName = permission.getName();

            try {
                // empty return means setting is at default (unless error in input to getSettingValue)
                //permissionValue = group.getSettingValue(PermissionName.findByName(permissionName).getPath());
                permissionValue = user.getSettingValue(permission.getSettingPath());
            }
            catch (Exception exception) {
                permissionValue = "GetSettingValue error: " + exception.getLocalizedMessage();
            }

            defaultValue = permission.getDefaultValue();

            settingRestInfo = new SettingBooleanRestInfo(permissionName, permissionValue, defaultValue);
            settings.add(settingRestInfo);
        }

        return settings;
    }

    private MetadataRestInfo addUsers(List<UserPermissionRestInfoFull> userPermissionsRestInfo, List<User> users) {
        UserPermissionRestInfoFull userPermissionRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, users.size());

        // create list of restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            User user = users.get(index);

            userPermissionRestInfo = createUserPermissionRestInfo(user);
            userPermissionsRestInfo.add(userPermissionRestInfo);
        }

        // create metadata about restinfos
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortUsers(List<User> users) {
        // sort if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
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

    private void updateUserPermission(User user, UserPermissionRestInfoFull userPermissionRestInfo) {
        Permission permission;

        // update each permission setting
        for (SettingBooleanRestInfo settingRestInfo : userPermissionRestInfo.getPermissions()) {
            permission = m_permissionManager.getPermissionByName(settingRestInfo.getName());
            user.setSettingValue(permission.getSettingPath(), settingRestInfo.getValue());
        }
    }


    // REST Representations
    // --------------------

    static class UserPermissionsRepresentation extends XStreamRepresentation<UserPermissionsBundleRestInfo> {

        public UserPermissionsRepresentation(MediaType mediaType, UserPermissionsBundleRestInfo object) {
            super(mediaType, object);
        }

        public UserPermissionsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("user-permission", UserPermissionsBundleRestInfo.class);
            xstream.alias("user", UserPermissionRestInfoFull.class);
            xstream.alias("setting", SettingBooleanRestInfo.class);
        }
    }

    static class UserPermissionRepresentation extends XStreamRepresentation<UserPermissionRestInfoFull> {

        public UserPermissionRepresentation(MediaType mediaType, UserPermissionRestInfoFull object) {
            super(mediaType, object);
        }

        public UserPermissionRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("user", UserPermissionRestInfoFull.class);
            xstream.alias("setting", SettingBooleanRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class UserPermissionsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<UserPermissionRestInfoFull> m_users;

        public UserPermissionsBundleRestInfo(List<UserPermissionRestInfoFull> userPermissions, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_users = userPermissions;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<UserPermissionRestInfoFull> getUsers() {
            return m_users;
        }
    }

    static class UserRestInfo {
        private final int m_id;
        private final String m_lastName;
        private final String m_firstName;

        public UserRestInfo(User user) {
            m_id = user.getId();
            m_lastName = user.getLastName();
            m_firstName = user.getFirstName();
        }

        public int getId() {
            return m_id;
        }

        public String getLastName() {
            return m_lastName;
        }

        public String getFirstName() {
            return m_firstName;
        }
    }

    static class UserPermissionRestInfoFull extends UserRestInfo {
        private final List<SettingBooleanRestInfo> m_permissions;

        public UserPermissionRestInfoFull(User user, List<SettingBooleanRestInfo> settingsRestInfo) {
            super(user);

            m_permissions = settingsRestInfo;
        }

        public List<SettingBooleanRestInfo> getPermissions() {
            return m_permissions;
        }
    }


    // Injected objects
    // ----------------

//    @Required
//    public void setSettingDao(SettingDao settingContext) {
//        m_settingContext = settingContext;
//    }

    @Required
    public void setPermissionManager(PermissionManager permissionManager) {
        m_permissionManager = permissionManager;
    }

}
