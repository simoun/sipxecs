/*
 *
 *  OpenAcdSettingsResource.java - A Restlet to read Skill data from OpenACD within SipXecs
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
import org.sipfoundry.sipxconfig.openacd.OpenAcdSettings;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdSettingRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.PaginationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class OpenAcdSettingsResource extends UserResource {

    private OpenAcdContext m_openAcdContext;
    private Form m_form;

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
        OpenAcdSettingRestInfo settingRestInfo;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                settingRestInfo = createSettingRestInfo(idInt);
            }
            catch (Exception exception) {
                return OpenAcdUtilities.getResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            // return representation
            return new OpenAcdSettingRepresentation(variant.getMediaType(), settingRestInfo);
        }


        // if not single, process request for list
        OpenAcdSettings settings = m_openAcdContext.getSettings();
        OpenAcdSettingRestInfo settingsRestInfo = new OpenAcdSettingRestInfo(settings);
        MetadataRestInfo metadataRestInfo;

        // set requested records and get resulting metadata
        metadataRestInfo = addSettings(settingsRestInfo, settings);

        // create final restinfo
        OpenAcdSettingsBundleRestInfo settingsBundleRestInfo = new OpenAcdSettingsBundleRestInfo(settingsRestInfo, metadataRestInfo);

        return new OpenAcdSettingsRepresentation(variant.getMediaType(), settingsBundleRestInfo);
    }


    // PUT - Update or Add single Skill
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdSettingRepresentation representation = new OpenAcdSettingRepresentation(entity);
        OpenAcdSettingRestInfo settingRestInfo = representation.getObject();
        OpenAcdSettings setting;

        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                setting = m_openAcdContext.getSettings();
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing
            try {
                updateSetting(setting, settingRestInfo);
                m_openAcdContext.saveSettings(setting);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Setting failed");
                return;
            }

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_UPDATED, setting.getId(), "Updated Settings");

            return;
        }


        // otherwise add new
        try {
            setting = createSetting(settingRestInfo);
            m_openAcdContext.saveSettings(setting);
        }
        catch (Exception exception) {
            OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Client failed");
            return;
        }

        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_CREATED, setting.getId(), "Created Client");
    }


    // DELETE - Delete single Skill
    // ----------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdSettings setting;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                setting = m_openAcdContext.getSettings();
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            m_openAcdContext.saveSettings(setting);

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_DELETED, setting.getId(), "Deleted Client");

            return;
        }

        // no id string
        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_MISSING_INPUT, "ID value missing");
    }


    // Helper functions
    // ----------------

    private OpenAcdSettingRestInfo createSettingRestInfo(int id) throws ResourceException {
        OpenAcdSettingRestInfo settingRestInfo;

        try {
            OpenAcdSettings setting = m_openAcdContext.getSettings();
            settingRestInfo = new OpenAcdSettingRestInfo(setting);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "ID " + id + " not found.");
        }

        return settingRestInfo;
    }

    private MetadataRestInfo addSettings(OpenAcdSettingRestInfo settingsRestInfo, OpenAcdSettings settings) {
        OpenAcdSettingRestInfo settingRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, 1);

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void updateSetting(OpenAcdSettings setting, OpenAcdSettingRestInfo settingRestInfo) throws ResourceException {
        String tempString;

        // do not allow empty name
        setting.setSettingValue("openacd-config/log_level", setting.getLogLevel());

    }

    private OpenAcdSettings createSetting(OpenAcdSettingRestInfo settingRestInfo) throws ResourceException {
        OpenAcdSettings setting = new OpenAcdSettings();

        // copy fields from rest info
        setting.setSettingValue("openacd-config/log_level", setting.getLogLevel());

        return setting;
    }


    // REST Representations
    // --------------------

    static class OpenAcdSettingsRepresentation extends XStreamRepresentation<OpenAcdSettingsBundleRestInfo> {

        public OpenAcdSettingsRepresentation(MediaType mediaType, OpenAcdSettingsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdSettingsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-setting", OpenAcdSettingsBundleRestInfo.class);
            xstream.alias("setting", OpenAcdSettingRestInfo.class);
        }
    }

    static class OpenAcdSettingRepresentation extends XStreamRepresentation<OpenAcdSettingRestInfo> {

        public OpenAcdSettingRepresentation(MediaType mediaType, OpenAcdSettingRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdSettingRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("setting", OpenAcdSettingRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdSettingsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final OpenAcdSettingRestInfo m_settings;

        public OpenAcdSettingsBundleRestInfo(OpenAcdSettingRestInfo settings, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_settings = settings;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public OpenAcdSettingRestInfo getSettings() {
            return m_settings;
        }
    }

    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }
}
