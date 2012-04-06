/*
 *
 *  OpenAcdLinesResource.java - A Restlet to read Skill data from OpenACD within SipXecs
 *  Copyright (C) 2012 PATLive, I. Wesson, D. Chang
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

import org.apache.commons.lang.StringUtils;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.freeswitch.FreeswitchAction;
import org.sipfoundry.sipxconfig.openacd.OpenAcdClient;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.openacd.OpenAcdLine;
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueue;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdClientRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdQueueRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

// OpenAcdLines are different
// --------------------------
// Lines is inconsistent with other OpenACD objects.
// OpenAcdContext does not contain functions for getLineById(), saveLine() or removeLine().
// It appears the OpenAcdExtension object is used for lines, and it has all the above functions   
// so this API will appear slightly different than other APIs, although attempts have been made to preserve general structure.
public class OpenAcdLinesResource extends UserResource {

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


    // GET - Retrieve all and single item
    // ----------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        int idInt;
        OpenAcdLineRestInfo lineRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return OpenAcdUtilities.getResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                lineRestInfo = createLineRestInfo(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_READ_FAILED, "Read Line failed", exception.getLocalizedMessage());
            }

            return new OpenAcdLineRepresentation(variant.getMediaType(), lineRestInfo);
        }


        // if not single, process request for list
        List<OpenAcdLine> lines = new ArrayList<OpenAcdLine>(m_openAcdContext.getLines());
        List<OpenAcdLineRestInfo> linesRestInfo = new ArrayList<OpenAcdLineRestInfo>();
        MetadataRestInfo metadataRestInfo;

        // sort groups if specified
        sortLines(lines);

        // set requested records and get resulting metadata
        metadataRestInfo = addLines(linesRestInfo, lines);

        // create final restinfo
        OpenAcdLinesBundleRestInfo linesBundleRestInfo = new OpenAcdLinesBundleRestInfo(linesRestInfo, metadataRestInfo);

        return new OpenAcdLinesRepresentation(variant.getMediaType(), linesBundleRestInfo);
    }


    // PUT - Update or Add single item
    // -------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdLineRepresentation representation = new OpenAcdLineRepresentation(entity);
        OpenAcdLineRestInfo lineRestInfo = representation.getObject();
        OpenAcdLine line = null;

        // validate input for update or create
        ValidationInfo validationInfo = validate(lineRestInfo);

        if (!validationInfo.valid) {
            OpenAcdUtilities.setResponseError(getResponse(), validationInfo.responseCode, validationInfo.message);
            return;
        }


        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                line = (OpenAcdLine) m_openAcdContext.getExtensionById(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing
            try {
                updateLine(line, lineRestInfo);
                m_openAcdContext.saveExtension(line);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Line failed", exception.getLocalizedMessage());
                return;
            }

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_UPDATED, "Updated Line", line.getId());

            return;
        }


        // otherwise add new
        try {
            line = createLine(lineRestInfo);
            m_openAcdContext.saveExtension(line);
        }
        catch (Exception exception) {
            OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Line failed", exception.getLocalizedMessage());
            return;
        }

        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_CREATED, "Created Line", line.getId());
    }


    // DELETE - Delete single item
    // ---------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdLine line;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                line = (OpenAcdLine) m_openAcdContext.getExtensionById(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            m_openAcdContext.deleteExtension(line);

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_DELETED, "Deleted Line", line.getId());

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
    private ValidationInfo validate(OpenAcdLineRestInfo restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        return validationInfo;
    }

    // parses OpenAcdLine contents instead of just an id because line is so different
    private OpenAcdLineActionsBundleRestInfo createLineActionsBundleRestInfo(OpenAcdLine line) {
        OpenAcdLineActionsBundleRestInfo lineActionsBundleRestInfo;
        OpenAcdQueueRestInfo queueRestInfo;
        OpenAcdClientRestInfo clientRestInfo;
        List<OpenAcdLineActionRestInfo> customActions = new ArrayList<OpenAcdLineActionRestInfo>();
        OpenAcdLineActionRestInfo customActionRestInfo;

        OpenAcdQueue queue;
        String queueName = "";
        OpenAcdClient client;
        String clientIdentity = "";
        Boolean allowVoicemail = false;
        String allowVoicemailString = "false";
        Boolean isFsSet = false;
        Boolean isAgentSet = false;
        String answerSupervisionType = "";
        String welcomeMessage = "";

        List<FreeswitchAction> actions = line.getLineActions();
        for (FreeswitchAction action : actions) {
            String application = action.getApplication();
            String data = action.getData();

            if (StringUtils.equals(application, FreeswitchAction.PredefinedAction.answer.toString())) {
                isFsSet = true;
            }
            else if (StringUtils.contains(data, OpenAcdLine.ERLANG_ANSWER)) {
                isAgentSet = true;
            }
            else if (StringUtils.contains(data, OpenAcdLine.Q)) {
                queueName = StringUtils.removeStart(data, OpenAcdLine.Q);
            }
            else if (StringUtils.contains(data, OpenAcdLine.BRAND)) {
                clientIdentity = StringUtils.removeStart(data, OpenAcdLine.BRAND);
            }
            else if (StringUtils.contains(data, OpenAcdLine.ALLOW_VOICEMAIL)) {
                allowVoicemailString = StringUtils.removeStart(data, OpenAcdLine.ALLOW_VOICEMAIL);
            }
            else if (StringUtils.equals(application, FreeswitchAction.PredefinedAction.playback.toString())) {
                // web ui only displays filename and appends audio directory
                //welcomeMessage = StringUtils.removeStart(data, m_openAcdContext.getSettings().getAudioDirectory() + "/");
                welcomeMessage = data;
            }
            else {
                customActionRestInfo = new OpenAcdLineActionRestInfo(action);
                customActions.add(customActionRestInfo);
            }
        }

        queue = m_openAcdContext.getQueueByName(queueName);
        queueRestInfo = new OpenAcdQueueRestInfo(queue);

        client = m_openAcdContext.getClientByIdentity(clientIdentity);
        clientRestInfo = new OpenAcdClientRestInfo(client);

        allowVoicemail = Boolean.parseBoolean(allowVoicemailString);

        if (isFsSet) {
            answerSupervisionType = "FS";
        }
        else if (isAgentSet) {
            answerSupervisionType = "AGENT";
        }
        else {
            answerSupervisionType = "ACD";
        }

        lineActionsBundleRestInfo = new OpenAcdLineActionsBundleRestInfo(queueRestInfo, clientRestInfo, allowVoicemail, customActions, answerSupervisionType, welcomeMessage);

        return lineActionsBundleRestInfo;
    }

    private OpenAcdLineRestInfo createLineRestInfo(int id) {
        OpenAcdLineRestInfo lineRestInfo;

        OpenAcdLine line = (OpenAcdLine) m_openAcdContext.getExtensionById(id);
        lineRestInfo = createLineRestInfo(line);

        return lineRestInfo;
    }

    private OpenAcdLineRestInfo createLineRestInfo(OpenAcdLine line) {
        OpenAcdLineRestInfo lineRestInfo;
        OpenAcdLineActionsBundleRestInfo lineActionsBundleRestInfo;

        lineActionsBundleRestInfo = createLineActionsBundleRestInfo(line);
        lineRestInfo = new OpenAcdLineRestInfo(line, lineActionsBundleRestInfo);

        return lineRestInfo;
    }

    private MetadataRestInfo addLines(List<OpenAcdLineRestInfo> linesRestInfo, List<OpenAcdLine> lines) {
        OpenAcdLineRestInfo lineRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, lines.size());

        // create list of line restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdLine line = lines.get(index);

            lineRestInfo = createLineRestInfo(line);
            linesRestInfo.add(lineRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortLines(List<OpenAcdLine> lines) {
        // sort groups if requested
        SortInfo sortInfo = OpenAcdUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(lines, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdLine line1 = (OpenAcdLine) object1;
                        OpenAcdLine line2 = (OpenAcdLine) object2;
                        return line1.getName().compareToIgnoreCase(line2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(lines, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdLine line1 = (OpenAcdLine) object1;
                        OpenAcdLine line2 = (OpenAcdLine) object2;
                        return line1.getDescription().compareToIgnoreCase(line2.getDescription());
                    }

                });
                break;

            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(lines, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdLine line1 = (OpenAcdLine) object1;
                        OpenAcdLine line2 = (OpenAcdLine) object2;
                        return line2.getName().compareToIgnoreCase(line1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(lines, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdLine line1 = (OpenAcdLine) object1;
                        OpenAcdLine line2 = (OpenAcdLine) object2;
                        return line2.getDescription().compareToIgnoreCase(line1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateLine(OpenAcdLine line, OpenAcdLineRestInfo lineRestInfo) throws ResourceException {
        String tempString;

        // do not allow empty name
        tempString = lineRestInfo.getName();
        if (!tempString.isEmpty()) {
            line.setName(tempString);
        }

        line.setDid(lineRestInfo.getDIDNumber());
        line.setDescription(lineRestInfo.getDescription());
        line.setAlias(lineRestInfo.getAlias());

        // set standard actions
        line.getNumberCondition().getActions().clear();

        // answer supervision type is an integer code from OpenAcdLine
        line.getNumberCondition().addAction(OpenAcdLine.createAnswerAction(getAnswerSupervisionCode(lineRestInfo.getActions().getAnswerSupervisionType())));
        line.getNumberCondition().addAction(OpenAcdLine.createVoicemailAction(lineRestInfo.getActions().getAllowVoicemail()));
        line.getNumberCondition().addAction(OpenAcdLine.createQueueAction(lineRestInfo.getActions().getQueue().getName()));
        line.getNumberCondition().addAction(OpenAcdLine.createClientAction(lineRestInfo.getActions().getClient().getIdentity()));

        // web ui only displays filename and appends audio directory
        //line.getNumberCondition().addAction(OpenAcdLine.createPlaybackAction(m_openAcdContext.getSettings().getAudioDirectory() + "/" + lineRestInfo.getActions().getWelcomeMessage()));
        line.getNumberCondition().addAction(OpenAcdLine.createPlaybackAction(lineRestInfo.getActions().getWelcomeMessage()));

        // set custom actions
        for (OpenAcdLineActionRestInfo actionRestInfo : lineRestInfo.getActions().getCustomActions()) {
            line.getNumberCondition().addAction(OpenAcdLine.createAction(actionRestInfo.getApplication(), actionRestInfo.getData()));
        }

        // "Expression" is the extension number, which may be a regular expression if regex is set
        line.getNumberCondition().setExpression(lineRestInfo.getExtension());
        line.getNumberCondition().setRegex(lineRestInfo.getRegex());
    }

    private OpenAcdLine createLine(OpenAcdLineRestInfo lineRestInfo) throws ResourceException {
        // special steps to obtain new line (cannot just "new")
        OpenAcdLine line = m_openAcdContext.newOpenAcdLine();
        line.addCondition(OpenAcdLine.createLineCondition());

        // copy fields from rest info
        line.setName(lineRestInfo.getName());
        line.setDid(lineRestInfo.getDIDNumber());
        line.setDescription(lineRestInfo.getDescription());
        line.setAlias(lineRestInfo.getAlias());

        // set standard actions
        line.getNumberCondition().getActions().clear();

        // answer supervision type is an integer code from OpenAcdLine
        line.getNumberCondition().addAction(OpenAcdLine.createAnswerAction(getAnswerSupervisionCode(lineRestInfo.getActions().getAnswerSupervisionType())));
        line.getNumberCondition().addAction(OpenAcdLine.createVoicemailAction(lineRestInfo.getActions().getAllowVoicemail()));
        line.getNumberCondition().addAction(OpenAcdLine.createQueueAction(lineRestInfo.getActions().getQueue().getName()));
        line.getNumberCondition().addAction(OpenAcdLine.createClientAction(lineRestInfo.getActions().getClient().getIdentity()));

        // web ui only displays filename and appends audio directory
        //line.getNumberCondition().addAction(OpenAcdLine.createPlaybackAction(m_openAcdContext.getSettings().getAudioDirectory() + "/" + lineRestInfo.getActions().getWelcomeMessage()));
        line.getNumberCondition().addAction(OpenAcdLine.createPlaybackAction(lineRestInfo.getActions().getWelcomeMessage()));

        // set custom actions
        for (OpenAcdLineActionRestInfo actionRestInfo : lineRestInfo.getActions().getCustomActions()) {
            line.getNumberCondition().addAction(OpenAcdLine.createAction(actionRestInfo.getApplication(), actionRestInfo.getData()));
        }

        // "Expression" is the extension number, which may be a regular expression if regex is set
        line.getNumberCondition().setExpression(lineRestInfo.getExtension());
        line.getNumberCondition().setRegex(lineRestInfo.getRegex());

        return line;
    }

    private Integer getAnswerSupervisionCode(String answerSupervisionType) {
        Integer answerSupervisionCode;

        if (StringUtils.equalsIgnoreCase(answerSupervisionType, "FS")) {
            answerSupervisionCode = OpenAcdLine.FS;
        }
        else if (StringUtils.equalsIgnoreCase(answerSupervisionType, "AGENT")) {
            answerSupervisionCode = OpenAcdLine.AGENT;
        }
        else {
            answerSupervisionCode = OpenAcdLine.ACD;
        }

        return answerSupervisionCode;
    }


    // REST Representations
    // --------------------

    static class OpenAcdLinesRepresentation extends XStreamRepresentation<OpenAcdLinesBundleRestInfo> {

        public OpenAcdLinesRepresentation(MediaType mediaType, OpenAcdLinesBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdLinesRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-line", OpenAcdLinesBundleRestInfo.class);
            xstream.alias("line", OpenAcdLineRestInfo.class);
            xstream.alias("action", OpenAcdLineActionRestInfo.class);
        }
    }

    static class OpenAcdLineRepresentation extends XStreamRepresentation<OpenAcdLineRestInfo> {

        public OpenAcdLineRepresentation(MediaType mediaType, OpenAcdLineRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdLineRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("line", OpenAcdLineRestInfo.class);
            xstream.alias("action", OpenAcdLineActionRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdLinesBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdLineRestInfo> m_lines;

        public OpenAcdLinesBundleRestInfo(List<OpenAcdLineRestInfo> lines, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_lines = lines;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdLineRestInfo> getLines() {
            return m_lines;
        }
    }

    static class OpenAcdLineRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;
        private final String m_extension;
        private final boolean m_regex;
        private final String m_didnumber;
        private final String m_alias;
        private final OpenAcdLineActionsBundleRestInfo m_actions;

        public OpenAcdLineRestInfo(OpenAcdLine line, OpenAcdLineActionsBundleRestInfo lineActionsRestInfo) {
            m_id = line.getId();
            m_name = line.getName();
            m_description = line.getDescription();
            m_extension = line.getExtension();
            m_regex = line.getRegex();
            m_didnumber = line.getDid();
            m_alias = line.getAlias();
            m_actions = lineActionsRestInfo;
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

        public String getExtension() {
            return m_extension;
        }

        public boolean getRegex() {
            return m_regex;
        }

        public String getDIDNumber() {
            return m_didnumber;
        }

        public String getAlias() {
            return m_alias;
        }

        public OpenAcdLineActionsBundleRestInfo getActions() {
            return m_actions;
        }
    }

    static class OpenAcdLineActionRestInfo {
        private final String m_application;
        private final String m_data;

        public OpenAcdLineActionRestInfo(FreeswitchAction action) {
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

    static class OpenAcdLineActionsBundleRestInfo {
        // standard (required?) actions
        private final OpenAcdQueueRestInfo m_queue;
        private final OpenAcdClientRestInfo m_client;
        private final boolean m_allowVoicemail;
        private final String m_answerSupervisionType;
        private final String m_welcomeMessage;

        // additional (custom) actions
        private final List<OpenAcdLineActionRestInfo> m_customActions;

        public OpenAcdLineActionsBundleRestInfo(OpenAcdQueueRestInfo queueRestInfo, OpenAcdClientRestInfo clientRestInfo, boolean allowVoicemail, List<OpenAcdLineActionRestInfo> customActions, String answerSupervisionType, String welcomeMessage) {
            m_queue = queueRestInfo;
            m_client = clientRestInfo;
            m_allowVoicemail = allowVoicemail;
            m_customActions = customActions;
            m_answerSupervisionType = answerSupervisionType;
            m_welcomeMessage = welcomeMessage;
        }

        public OpenAcdQueueRestInfo getQueue() {
            return m_queue;
        }

        public OpenAcdClientRestInfo getClient() {
            return m_client;
        }

        public boolean getAllowVoicemail() {
            return m_allowVoicemail;
        }

        public String getAnswerSupervisionType() {
            return m_answerSupervisionType;
        }

        public String getWelcomeMessage() {
            return m_welcomeMessage;
        }

        public List<OpenAcdLineActionRestInfo> getCustomActions() {
            return m_customActions;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
