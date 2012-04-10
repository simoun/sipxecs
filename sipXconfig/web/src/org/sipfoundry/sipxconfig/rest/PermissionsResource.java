/*
 *
 *  PermissionsResource.java - A Restlet to read Skill data from SipXecs
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
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkillGroup;
import org.sipfoundry.sipxconfig.permission.Permission;
import org.sipfoundry.sipxconfig.permission.PermissionManager;
import org.sipfoundry.sipxconfig.rest.OpenAcdSkillsResource.OpenAcdSkillRepresentation;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdSkillRestInfoFull;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.ResponseCode;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class PermissionsResource extends UserResource {

    private OpenAcdContext m_openAcdContext;
    private PermissionManager m_permissionManager;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField {
        NAME, DESCRIPTION, ATOM, NONE;

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
        PermissionRestInfoFull permissionRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return OpenAcdUtilities.getResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                permissionRestInfo = createPermissionRestInfo(idInt);
            }
            catch (Exception exception) {
                return OpenAcdUtilities.getResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_READ_FAILED, "Read permissions failed", exception.getLocalizedMessage());
            }

            return new PermissionRepresentation(variant.getMediaType(), permissionRestInfo);
        }


        // if not single, process request for all
        List<Permission> permissions = (List<Permission>) m_permissionManager.getPermissions();
        List<PermissionRestInfoFull> permissionsRestInfo = new ArrayList<PermissionRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort groups if specified
        sortPermissions(permissions);

        // set requested agents groups and get resulting metadata
        metadataRestInfo = addPermissions(permissionsRestInfo, permissions);

        // create final restinfo
        PermissionsBundleRestInfo permissionsBundleRestInfo = new PermissionsBundleRestInfo(permissionsRestInfo, metadataRestInfo);

        return new PermissionsRepresentation(variant.getMediaType(), permissionsBundleRestInfo);
    }


    // PUT - Update or Add single Skill
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdSkillRepresentation representation = new OpenAcdSkillRepresentation(entity);
        OpenAcdSkillRestInfoFull skillRestInfo = representation.getObject();
        OpenAcdSkill skill = null;

        // validate input for update or create
        ValidationInfo validationInfo = validate(skillRestInfo);

        if (!validationInfo.valid) {
            OpenAcdUtilities.setResponseError(getResponse(), validationInfo.responseCode, validationInfo.message);
            return;
        }


        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                skill = m_openAcdContext.getSkillById(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing
            try {
                updateSkill(skill, skillRestInfo);
                m_openAcdContext.saveSkill(skill);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Skill failed", exception.getLocalizedMessage());
                return;
            }

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_UPDATED, "Updated Skill", skill.getId());

            return;
        }


        // otherwise add new
        try {
            skill = createSkill(skillRestInfo);
            m_openAcdContext.saveSkill(skill);
        }
        catch (Exception exception) {
            OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Skill failed", exception.getLocalizedMessage());
            return;
        }

        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_CREATED, "Created Skill", skill.getId());
    }


    // DELETE - Delete single Skill
    // ----------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdSkill skill;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                skill = m_openAcdContext.getSkillById(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            m_openAcdContext.deleteSkill(skill);

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_DELETED, "Deleted Skill", skill.getId());

            return;
        }

        // no id string
        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_MISSING_INPUT, "ID value missing");
    }


    // Helper functions
    // ----------------

    // basic interface level validation of data provided through REST interface for creation or
    // update
    // may also contain clean up of input data
    // may create another validation function if different rules needed for update v. create
    private ValidationInfo validate(OpenAcdSkillRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        String name = restInfo.getName();
        String atom = restInfo.getAtom();

        for (int i = 0; i < name.length(); i++) {
            if ((!Character.isLetterOrDigit(name.charAt(i)) && !(Character.getType(name.charAt(i)) == Character.CONNECTOR_PUNCTUATION)) && name.charAt(i) != '-') {
                validationInfo.valid = false;
                validationInfo.message = "Validation Error: Skill Group 'Name' must only contain letters, numbers, dashes, and underscores";
                validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
            }
        }

        for (int i = 0; i < atom.length(); i++) {
            if ((!Character.isLetterOrDigit(atom.charAt(i)) && !(Character.getType(atom.charAt(i)) == Character.CONNECTOR_PUNCTUATION)) && atom.charAt(i) != '-') {
                validationInfo.valid = false;
                validationInfo.message = "Validation Error: 'Atom' must only contain letters, numbers, dashes, and underscores";
                validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
            }
        }

        return validationInfo;
    }

    private Permission getPermissionById(int id) throws ResourceException {
        Permission foundPermission = null;

        for (Permission permission : m_permissionManager.getPermissions()) {
            if (permission.getId().equals(id)) {
                foundPermission = permission;
            }
        }

        if (foundPermission == null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Id " + id + "not found.");
        }

        return foundPermission;
    }

    private PermissionRestInfoFull createPermissionRestInfo(int id) throws ResourceException {
        PermissionRestInfoFull permissionRestInfo = null;

        Permission permission = getPermissionById(id);
        permissionRestInfo = new PermissionRestInfoFull(permission);

        return permissionRestInfo;
    }

    private MetadataRestInfo addPermissions(List<PermissionRestInfoFull> permissionsRestInfo, List<Permission> permissions) {
        PermissionRestInfoFull permissionRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, permissions.size());

        // create list of restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            Permission permission = permissions.get(index);

            permissionRestInfo = new PermissionRestInfoFull(permission);
            permissionsRestInfo.add(permissionRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortPermissions(List<Permission> permissions) {
        // sort if requested
        SortInfo sortInfo = OpenAcdUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return permission1.getName().compareToIgnoreCase(permission2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return permission1.getDescription().compareToIgnoreCase(permission2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return permission2.getName().compareToIgnoreCase(permission1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(permissions, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Permission permission1 = (Permission) object1;
                        Permission permission2 = (Permission) object2;
                        return permission2.getDescription().compareToIgnoreCase(permission1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateSkill(OpenAcdSkill skill, OpenAcdSkillRestInfoFull skillRestInfo) {
        OpenAcdSkillGroup skillGroup;
        String tempString;

        // do not allow empty name
        tempString = skillRestInfo.getName();
        if (!tempString.isEmpty()) {
            skill.setName(tempString);
        }

        skill.setDescription(skillRestInfo.getDescription());

        skillGroup = getSkillGroup(skillRestInfo);
        skill.setGroup(skillGroup);
    }

    private OpenAcdSkill createSkill(OpenAcdSkillRestInfoFull skillRestInfo) throws ResourceException {
        OpenAcdSkillGroup skillGroup;
        OpenAcdSkill skill = new OpenAcdSkill();

        // copy fields from rest info
        skill.setName(skillRestInfo.getName());
        skill.setDescription(skillRestInfo.getDescription());
        skill.setAtom(skillRestInfo.getAtom());

        skillGroup = getSkillGroup(skillRestInfo);
        skill.setGroup(skillGroup);

        return skill;
    }

    private OpenAcdSkillGroup getSkillGroup(OpenAcdSkillRestInfoFull skillRestInfo) {
        OpenAcdSkillGroup skillGroup;
        int groupId = skillRestInfo.getGroupId();
        skillGroup = m_openAcdContext.getSkillGroupById(groupId);

        return skillGroup;
    }


    // REST Representations
    // --------------------

    static class PermissionsRepresentation extends XStreamRepresentation<PermissionsBundleRestInfo> {

        public PermissionsRepresentation(MediaType mediaType, PermissionsBundleRestInfo object) {
            super(mediaType, object);
        }

        public PermissionsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("permissions", PermissionsBundleRestInfo.class);
            xstream.alias("permission", PermissionRestInfoFull.class);
        }
    }

    static class PermissionRepresentation extends XStreamRepresentation<PermissionRestInfoFull> {

        public PermissionRepresentation(MediaType mediaType, PermissionRestInfoFull object) {
            super(mediaType, object);
        }

        public PermissionRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("permission", PermissionRestInfoFull.class);
        }
    }


    // REST info objects
    // -----------------

    static class PermissionsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<PermissionRestInfoFull> m_permissions;

        public PermissionsBundleRestInfo(List<PermissionRestInfoFull> permissions, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_permissions = permissions;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<PermissionRestInfoFull> getPermissions() {
            return m_permissions;
        }
    }

    static class PermissionRestInfoFull {
        private final int m_id;
        private final String m_name;
        private final String m_description;
        private final String m_label;
        private final boolean m_defaultValue;

        public PermissionRestInfoFull(Permission permission) {
            m_id = permission.getId();
            m_name = permission.getName();
            m_description = permission.getDescription();
            m_label = permission.getLabel();
            m_defaultValue = permission.getDefaultValue();
        }

        public int getId() {
            return m_id;
        }

        public String getName() {
            return m_name;
        }

        public String getDescription() {
            return m_description;
        }

        public boolean getDefaultValue() {
            return m_defaultValue;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

    @Required
    public void setPermissionManager(PermissionManager permissionManager) {
        m_permissionManager = permissionManager;
    }
}
