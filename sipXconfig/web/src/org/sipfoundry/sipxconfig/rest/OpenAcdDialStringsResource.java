/*
 *
 *  OpenAcdDialStringsResource.java - A Restlet to read DialString data from OpenACD within SipXecs
 *  Copyright (C) 2012 PATLive, D. Waseem
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
import org.sipfoundry.sipxconfig.freeswitch.FreeswitchAction;
import org.sipfoundry.sipxconfig.openacd.OpenAcdCommand;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class OpenAcdDialStringsResource extends UserResource {

    private OpenAcdContext m_openAcdContext;
    private Form m_form;

    // use to define all possible sort fields
    enum SortField {
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

    // GET - Retrieve all and single Client
    // -----------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        int idInt;
        OpenAcdDialStringRestInfo dialStringRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                dialStringRestInfo = createDialStringRestInfo(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_READ_FAILED, "Read Settings failed", exception.getLocalizedMessage());
            }

            return new OpenAcdDialStringRepresentation(variant.getMediaType(), dialStringRestInfo);
        }

        // if not single, process request for list
        List<OpenAcdCommand> dialStrings = new ArrayList<OpenAcdCommand>(m_openAcdContext.getCommands());
        List<OpenAcdDialStringRestInfo> dialStringsRestInfo = new ArrayList<OpenAcdDialStringRestInfo>();
        MetadataRestInfo metadataRestInfo;

        // sort groups if specified
        sortDialStrings(dialStrings);

        // set requested records and get resulting metadata
        metadataRestInfo = addDialStrings(dialStringsRestInfo, dialStrings);

        // create final restinfo
        OpenAcdDialStringsBundleRestInfo dialStringsBundleRestInfo = new OpenAcdDialStringsBundleRestInfo(dialStringsRestInfo, metadataRestInfo);

        return new OpenAcdDialStringsRepresentation(variant.getMediaType(), dialStringsBundleRestInfo);
    }


    // PUT - Update or Add single Dial String
    // --------------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdDialStringRepresentation representation = new OpenAcdDialStringRepresentation(entity);
        OpenAcdDialStringRestInfo dialStringRestInfo = representation.getObject();
        OpenAcdCommand dialString = null;

        ValidationInfo validationInfo = validate(dialStringRestInfo);

        if (!validationInfo.valid) {
            RestUtilities.setResponseError(getResponse(), validationInfo.responseCode, validationInfo.message);
            return;
        }


        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                dialString = (OpenAcdCommand) m_openAcdContext.getExtensionById(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing
            try {
                updateDialString(dialString, dialStringRestInfo);
                m_openAcdContext.saveExtension(dialString);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Setting failed", exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_UPDATED, "Updated Settings", dialString.getId());

            return;
        }


        // otherwise add new
        try {
            dialString = createDialString(dialStringRestInfo);
            m_openAcdContext.saveExtension(dialString);
        }
        catch (Exception exception) {
            RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Dial String failed", exception.getLocalizedMessage());
            return;
        }

        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_CREATED, "Created Setting", dialString.getId());
    }


    // DELETE - Delete single Dial String
    // ----------------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdCommand dialString;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                dialString = (OpenAcdCommand) m_openAcdContext.getExtensionById(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            m_openAcdContext.deleteExtension(dialString);

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_DELETED, "Deleted Client", dialString.getId());

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
    private ValidationInfo validate(OpenAcdDialStringRestInfo restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        return validationInfo;
    }

    private OpenAcdDialStringRestInfo createDialStringRestInfo(int id) throws ResourceException {
        OpenAcdDialStringRestInfo dialStringRestInfo;

        try {
            OpenAcdCommand dialString = (OpenAcdCommand) m_openAcdContext.getExtensionById(id);
            dialStringRestInfo = createDialStringRestInfo(dialString);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "ID " + id + " not found.");
        }

        return dialStringRestInfo;
    }

    private OpenAcdDialStringRestInfo createDialStringRestInfo(OpenAcdCommand dialString) {
        OpenAcdDialStringRestInfo dialStringRestInfo;
        List<OpenAcdDialStringActionRestInfo> dialStringActionRestInfo;

        dialStringActionRestInfo = createDialStringActionRestInfo(dialString);
        dialStringRestInfo = new OpenAcdDialStringRestInfo(dialString, dialStringActionRestInfo);

        return dialStringRestInfo;

    }

    private List<OpenAcdDialStringActionRestInfo> createDialStringActionRestInfo(OpenAcdCommand dialString) {
        OpenAcdDialStringActionRestInfo dialStringActionRestInfo;
        List<OpenAcdDialStringActionRestInfo> customActions = new ArrayList<OpenAcdDialStringActionRestInfo>();

        List<FreeswitchAction> actions = dialString.getLineActions();

        for (FreeswitchAction action : actions) {
            dialStringActionRestInfo = new OpenAcdDialStringActionRestInfo(action);
            customActions.add(dialStringActionRestInfo);
        }

        return customActions;
    }

    private MetadataRestInfo addDialStrings(List<OpenAcdDialStringRestInfo> dialStringsRestInfo, List<OpenAcdCommand> dialStrings) {
        OpenAcdDialStringRestInfo dialStringRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, dialStrings.size());

        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdCommand dialString = dialStrings.get(index);

            dialStringRestInfo = createDialStringRestInfo(dialString);
            dialStringsRestInfo.add(dialStringRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortDialStrings(List<OpenAcdCommand> dialStrings) {
        // sort groups if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(dialStrings, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdCommand dialString1 = (OpenAcdCommand) object1;
                        OpenAcdCommand dialString2 = (OpenAcdCommand) object2;
                        return dialString1.getName().compareToIgnoreCase(dialString2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(dialStrings, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdCommand dialString1 = (OpenAcdCommand) object1;
                        OpenAcdCommand dialString2 = (OpenAcdCommand) object2;
                        return dialString1.getDescription().compareToIgnoreCase(dialString2.getDescription());
                    }

                });
                break;

            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(dialStrings, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdCommand dialString1 = (OpenAcdCommand) object1;
                        OpenAcdCommand dialString2 = (OpenAcdCommand) object2;
                        return dialString2.getName().compareToIgnoreCase(dialString1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(dialStrings, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdCommand dialString1 = (OpenAcdCommand) object1;
                        OpenAcdCommand dialString2 = (OpenAcdCommand) object2;
                        return dialString2.getDescription().compareToIgnoreCase(dialString1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateDialString(OpenAcdCommand dialString, OpenAcdDialStringRestInfo dialStringRestInfo) throws ResourceException {

        dialString.setName(dialStringRestInfo.getName());
        dialString.setEnabled(dialStringRestInfo.getEnabled());
        dialString.setDescription(dialStringRestInfo.getDescription());
        dialString.getNumberCondition().setExpression(dialStringRestInfo.getExtension());

        for (OpenAcdDialStringActionRestInfo actionRestInfo : dialStringRestInfo.getActions()) {
            dialString.getNumberCondition().addAction(OpenAcdCommand.createAction(actionRestInfo.getApplication(), actionRestInfo.getApplication()));
        }
    }

    private OpenAcdCommand createDialString(OpenAcdDialStringRestInfo dialStringRestInfo) throws ResourceException {
        OpenAcdCommand dialString = new OpenAcdCommand();
        dialString.addCondition(OpenAcdCommand.createLineCondition());
        String tempString;

        if (!(tempString = dialStringRestInfo.getName()).isEmpty()) {
            dialString.setName(tempString);
        }

        dialString.setEnabled(dialStringRestInfo.getEnabled());
        dialString.setDescription(dialStringRestInfo.getDescription());
        dialString.getNumberCondition().setExpression(dialStringRestInfo.getExtension());

        for (OpenAcdDialStringActionRestInfo actionRestInfo : dialStringRestInfo.getActions()) {
            dialString.getNumberCondition().addAction(OpenAcdCommand.createAction(actionRestInfo.getApplication(), actionRestInfo.getApplication()));
        }

        return dialString;
    }


    // REST Representations
    // --------------------

    static class OpenAcdDialStringsRepresentation extends XStreamRepresentation<OpenAcdDialStringsBundleRestInfo> {

        public OpenAcdDialStringsRepresentation(MediaType mediaType, OpenAcdDialStringsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdDialStringsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-dial-string", OpenAcdDialStringsBundleRestInfo.class);
            xstream.alias("dial-string", OpenAcdDialStringRestInfo.class);
            xstream.alias("action", OpenAcdDialStringActionRestInfo.class);
        }
    }

    static class OpenAcdDialStringRepresentation extends XStreamRepresentation<OpenAcdDialStringRestInfo> {

        public OpenAcdDialStringRepresentation(MediaType mediaType, OpenAcdDialStringRestInfo dialStringsBundleRestInfo) {
            super(mediaType, dialStringsBundleRestInfo);
        }

        public OpenAcdDialStringRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("dialString", OpenAcdDialStringRestInfo.class);
            xstream.alias("action", OpenAcdDialStringActionRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdDialStringsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdDialStringRestInfo> m_dialStrings;

        public OpenAcdDialStringsBundleRestInfo(List<OpenAcdDialStringRestInfo> dialStrings, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_dialStrings = dialStrings;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdDialStringRestInfo> getDialStrings() {
            return m_dialStrings;
        }
    }

    static class OpenAcdDialStringRestInfo {
        private final int m_id;
        private final String m_name;
        private final boolean m_enabled;
        private final String m_description;
        private final String m_extension;
        private final List<OpenAcdDialStringActionRestInfo> m_actions;

        public OpenAcdDialStringRestInfo(OpenAcdCommand dial, List<OpenAcdDialStringActionRestInfo> dialStringActionsRestInfo) {
            m_id = dial.getId();
            m_name = dial.getName();
            m_enabled = dial.isEnabled();
            m_description = dial.getDescription();
            m_extension = dial.getExtension();
            m_actions = dialStringActionsRestInfo;
        }

        public int getId() {
            return m_id;
        }

        public String getName() {
            return m_name;
        }

        public boolean getEnabled() {
            return m_enabled;
        }

        public String getDescription() {
            return m_description;
        }

        public String getExtension() {
            return m_extension;
        }

        public List<OpenAcdDialStringActionRestInfo> getActions() {
            return m_actions;
        }
    }

    static class OpenAcdDialStringActionRestInfo {
        private final String m_application;
        private final String m_data;

        public OpenAcdDialStringActionRestInfo(FreeswitchAction action) {
            m_application = action.getApplication();
            m_data = action.getData();
        }

        public String getApplication() {
            return m_application;
        }

        public String getData() {
            return m_data;
        }
    }

    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }
}
