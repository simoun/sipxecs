/*
 *
 *  OpenAcdQueuesResource.java - A Restlet to read Skill data from OpenACD within SipXecs
 *  Copyright (C) 2012 PATLive, I. Wesson, D. Waseem, D. Chang
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
import java.util.Set;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.openacd.OpenAcdAgentGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueue;
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueueGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdRecipeAction;
import org.sipfoundry.sipxconfig.openacd.OpenAcdRecipeCondition;
import org.sipfoundry.sipxconfig.openacd.OpenAcdRecipeStep;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdAgentGroupRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdQueueRestInfoFull;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdRecipeActionRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdRecipeConditionRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdRecipeStepRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdSkillRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class OpenAcdQueuesResource extends UserResource {

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

    // GET - Retrieve all and single Queue
    // -----------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        int idInt;
        OpenAcdQueueRestInfoFull queueRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return OpenAcdUtilities.getResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                queueRestInfo = createQueueRestInfo(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_READ_FAILED, "Read Queue failed", exception.getLocalizedMessage());
            }

            return new OpenAcdQueueRepresentation(variant.getMediaType(), queueRestInfo);
        }


        // if not single, process request for list
        List<OpenAcdQueue> queues = m_openAcdContext.getQueues();
        List<OpenAcdQueueRestInfoFull> queuesRestInfo = new ArrayList<OpenAcdQueueRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort groups if specified
        sortQueues(queues);

        // set requested records and get resulting metadata
        metadataRestInfo = addQueues(queuesRestInfo, queues);

        // create final restinfo
        OpenAcdQueuesBundleRestInfo queuesBundleRestInfo = new OpenAcdQueuesBundleRestInfo(queuesRestInfo, metadataRestInfo);

        return new OpenAcdQueuesRepresentation(variant.getMediaType(), queuesBundleRestInfo);
    }


    // PUT - Update or Add single Skill
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdQueueRepresentation representation = new OpenAcdQueueRepresentation(entity);
        OpenAcdQueueRestInfoFull queueRestInfo = representation.getObject();
        OpenAcdQueue queue;

        // validate input for update or create
        ValidationInfo validationInfo = validate(queueRestInfo);

        if (!validationInfo.valid) {
            OpenAcdUtilities.setResponseError(getResponse(), validationInfo.responseCode, validationInfo.message);
            return;
        }


        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                queue = m_openAcdContext.getQueueById(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing
            try {
                updateQueue(queue, queueRestInfo);
                m_openAcdContext.saveQueue(queue);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Queue failed", exception.getLocalizedMessage());
                return;
            }

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_UPDATED, "Updated Queue", queue.getId());

            return;
        }


        // otherwise add new
        try {
            queue = createQueue(queueRestInfo);
            m_openAcdContext.saveQueue(queue);
        }
        catch (Exception exception) {
            OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Queue failed", exception.getLocalizedMessage());
            return;
        }

        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_CREATED, "Created Queue", queue.getId());
    }


    // DELETE - Delete single Skill
    // ----------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdQueue queue;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                queue = m_openAcdContext.getQueueById(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            m_openAcdContext.deleteQueue(queue);

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_DELETED, "Deleted Queue", queue.getId());

            return;
        }

        // no id string
        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_MISSING_INPUT, "ID value missing");
    }


    // Helper functions
    // ----------------

    // basic interface level validation of data provided through REST interface for creation or update
    // may also contain clean up of input data
    // may create another validation function if different rules needed for update v. create
    private ValidationInfo validate(OpenAcdQueueRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        return validationInfo;
    }

    private OpenAcdQueueRestInfoFull createQueueRestInfo(int id) throws ResourceException {
        OpenAcdQueueRestInfoFull queueRestInfo;
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        List<OpenAcdAgentGroupRestInfo> agentGroupsRestInfo;
        List<OpenAcdRecipeStepRestInfo> recipeStepRestInfo;

        OpenAcdQueue queue = m_openAcdContext.getQueueById(id);
        skillsRestInfo = createSkillsRestInfo(queue.getSkills());
        agentGroupsRestInfo = createAgentGroupsRestInfo(queue);
        recipeStepRestInfo = createRecipeStepsRestInfo(queue);
        queueRestInfo = new OpenAcdQueueRestInfoFull(queue, skillsRestInfo, agentGroupsRestInfo, recipeStepRestInfo);

        return queueRestInfo;
    }

    private List<OpenAcdRecipeStepRestInfo> createRecipeStepsRestInfo(OpenAcdQueue queue) {
        List<OpenAcdRecipeStepRestInfo> recipeStepsRestInfo;
        OpenAcdRecipeStepRestInfo recipeStepRestInfo;

        Set<OpenAcdRecipeStep> groupRecipeSteps = queue.getSteps();
        recipeStepsRestInfo = new ArrayList<OpenAcdRecipeStepRestInfo>(groupRecipeSteps.size());

        for (OpenAcdRecipeStep groupRecipeStep : groupRecipeSteps) {
            recipeStepRestInfo = new OpenAcdRecipeStepRestInfo(groupRecipeStep, createRecipeActionRestInfo(groupRecipeStep), createRecipeConditionsRestInfo(groupRecipeStep));
            recipeStepsRestInfo.add(recipeStepRestInfo);
        }
        return recipeStepsRestInfo;
    }

    private OpenAcdRecipeActionRestInfo createRecipeActionRestInfo(OpenAcdRecipeStep step) {
        OpenAcdRecipeActionRestInfo recipeActionRestInfo;
        List<OpenAcdSkillRestInfo> skillsRestInfo;

        // get skills associated with action
        skillsRestInfo = createSkillsRestInfo(step.getAction().getSkills());
        recipeActionRestInfo = new OpenAcdRecipeActionRestInfo(step.getAction(), skillsRestInfo);

        return recipeActionRestInfo;
    }

    private List<OpenAcdRecipeConditionRestInfo> createRecipeConditionsRestInfo(OpenAcdRecipeStep step) {
        List<OpenAcdRecipeConditionRestInfo> recipeConditionsRestInfo;
        OpenAcdRecipeConditionRestInfo recipeConditionRestInfo;


        List<OpenAcdRecipeCondition> groupRecipeConditions = step.getConditions();
        recipeConditionsRestInfo = new ArrayList<OpenAcdRecipeConditionRestInfo>(groupRecipeConditions.size());

        for (OpenAcdRecipeCondition groupRecipeCondition : groupRecipeConditions) {
            recipeConditionRestInfo = new OpenAcdRecipeConditionRestInfo(groupRecipeCondition);
            recipeConditionsRestInfo.add(recipeConditionRestInfo);
        }

        return recipeConditionsRestInfo;
    }

    private List<OpenAcdSkillRestInfo> createSkillsRestInfo(Set<OpenAcdSkill> skills) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        OpenAcdSkillRestInfo skillRestInfo;

        // create list of skill restinfos from set of skills
        skillsRestInfo = new ArrayList<OpenAcdSkillRestInfo>(skills.size());

        for (OpenAcdSkill skill : skills) {
            skillRestInfo = new OpenAcdSkillRestInfo(skill);
            skillsRestInfo.add(skillRestInfo);
        }

        return skillsRestInfo;
    }

    private List<OpenAcdAgentGroupRestInfo> createAgentGroupsRestInfo(OpenAcdQueue queue) {
        List<OpenAcdAgentGroupRestInfo> agentGroupsRestInfo;
        OpenAcdAgentGroupRestInfo agentGroupRestInfo;

        // create list of agent group restinfos for single group
        Set<OpenAcdAgentGroup> groupAgentGroups = queue.getAgentGroups();
        agentGroupsRestInfo = new ArrayList<OpenAcdAgentGroupRestInfo>(groupAgentGroups.size());

        for (OpenAcdAgentGroup groupAgentGroup : groupAgentGroups) {
            agentGroupRestInfo = new OpenAcdAgentGroupRestInfo(groupAgentGroup);
            agentGroupsRestInfo.add(agentGroupRestInfo);
        }

        return agentGroupsRestInfo;
    }

    private MetadataRestInfo addQueues(List<OpenAcdQueueRestInfoFull> queuesRestInfo, List<OpenAcdQueue> queues) {
        OpenAcdQueueRestInfoFull queueRestInfo;
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        List<OpenAcdAgentGroupRestInfo> agentGroupRestInfo;
        List<OpenAcdRecipeStepRestInfo> recipeStepRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, queues.size());

        // create list of queue restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdQueue queue = queues.get(index);

            skillsRestInfo = createSkillsRestInfo(queue.getSkills());
            agentGroupRestInfo = createAgentGroupsRestInfo(queue);
            recipeStepRestInfo = createRecipeStepsRestInfo(queue);
            queueRestInfo = new OpenAcdQueueRestInfoFull(queue, skillsRestInfo, agentGroupRestInfo, recipeStepRestInfo);
            queuesRestInfo.add(queueRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortQueues(List<OpenAcdQueue> queues) {
        // sort groups if requested
        SortInfo sortInfo = OpenAcdUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(queues, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueue queue1 = (OpenAcdQueue) object1;
                        OpenAcdQueue queue2 = (OpenAcdQueue) object2;
                        return queue1.getName().compareToIgnoreCase(queue2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(queues, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueue queue1 = (OpenAcdQueue) object1;
                        OpenAcdQueue queue2 = (OpenAcdQueue) object2;
                        return queue1.getDescription().compareToIgnoreCase(queue2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(queues, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueue queue1 = (OpenAcdQueue) object1;
                        OpenAcdQueue queue2 = (OpenAcdQueue) object2;
                        return queue2.getName().compareToIgnoreCase(queue1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(queues, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueue queue1 = (OpenAcdQueue) object1;
                        OpenAcdQueue queue2 = (OpenAcdQueue) object2;
                        return queue2.getDescription().compareToIgnoreCase(queue1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateQueue(OpenAcdQueue queue, OpenAcdQueueRestInfoFull queueRestInfo) throws ResourceException {
        String tempString;
        OpenAcdQueueGroup queueGroup;

        // do not allow empty name
        tempString = queueRestInfo.getName();
        if (!tempString.isEmpty()) {
            queue.setName(tempString);
        }
        queueGroup = getQueueGroup(queueRestInfo);

        queue.setGroup(queueGroup);
        queue.setDescription(queueRestInfo.getDescription());

        addLists(queue, queueRestInfo);
    }

    private OpenAcdQueue createQueue(OpenAcdQueueRestInfoFull queueRestInfo) throws ResourceException {
        OpenAcdQueue queue = new OpenAcdQueue();
        OpenAcdQueueGroup queueGroup;

        // copy fields from rest info
        queue.setName(queueRestInfo.getName());
        queueGroup = getQueueGroup(queueRestInfo);

        queue.setGroup(queueGroup);
        queue.setDescription(queueRestInfo.getDescription());

        addLists(queue, queueRestInfo);

        return queue;
    }

    private void addLists(OpenAcdQueue queue, OpenAcdQueueRestInfoFull queueRestInfo) {
        // remove all skills
        queue.getSkills().clear();

        // set skills
        OpenAcdSkill skill;
        List<OpenAcdSkillRestInfo> skillsRestInfo = queueRestInfo.getSkills();
        for (OpenAcdSkillRestInfo skillRestInfo : skillsRestInfo) {
            skill = m_openAcdContext.getSkillById(skillRestInfo.getId());
            queue.addSkill(skill);
        }

        // remove all agent groups
        queue.getAgentGroups().clear();

        // set agent groups
        OpenAcdAgentGroup agentGroup;
        List<OpenAcdAgentGroupRestInfo> agentGroupsRestInfo = queueRestInfo.getAgentGroups();
        for (OpenAcdAgentGroupRestInfo agentGroupRestInfo : agentGroupsRestInfo) {
            agentGroup = m_openAcdContext.getAgentGroupById(agentGroupRestInfo.getId());
            queue.addAgentGroup(agentGroup);
        }

        // remove all current steps
        queue.getSteps().clear();

        // set steps
        OpenAcdRecipeStep step;
        OpenAcdRecipeCondition condition;
        OpenAcdRecipeAction action;
        OpenAcdRecipeActionRestInfo actionRestInfo;

        List<OpenAcdRecipeStepRestInfo> recipeStepsRestInfo = queueRestInfo.getSteps();
        for (OpenAcdRecipeStepRestInfo recipeStepRestInfo : recipeStepsRestInfo) {
            //step = m_openAcdContext.getRecipeStepById(recipeStepRestInfo.getId());
            step = new OpenAcdRecipeStep();

            // add conditions
            for (OpenAcdRecipeConditionRestInfo recipeConditionRestInfo : recipeStepRestInfo.getConditions()) {
                condition = new OpenAcdRecipeCondition();
                condition.setCondition(recipeConditionRestInfo.getCondition());
                condition.setRelation(recipeConditionRestInfo.getRelation());
                condition.setValueCondition(recipeConditionRestInfo.getValueCondition());

                step.addCondition(condition);
            }

            // add action
            action = new OpenAcdRecipeAction();
            actionRestInfo = recipeStepRestInfo.getAction();
            action.setAction(actionRestInfo.getAction());
            action.setActionValue(actionRestInfo.getActionValue());

            // set action skills (separate from skills assigned to queue
            for (OpenAcdSkillRestInfo skillRestInfo : actionRestInfo.getSkills()) {
                skill = m_openAcdContext.getSkillById(skillRestInfo.getId());
                action.addSkill(skill);
            }

            step.setAction(action);

            queue.addStep(step);
        }
    }

    private OpenAcdQueueGroup getQueueGroup(OpenAcdQueueRestInfoFull queueRestInfo) throws ResourceException {
        OpenAcdQueueGroup queueGroup;
        int groupId = 0;

        try {
            groupId = queueRestInfo.getGroupId();
            queueGroup = m_openAcdContext.getQueueGroupById(groupId);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "Queue Group ID " + groupId + " not found.");
        }

        return queueGroup;
    }


    // REST Representations
    // --------------------

    static class OpenAcdQueuesRepresentation extends XStreamRepresentation<OpenAcdQueuesBundleRestInfo> {

        public OpenAcdQueuesRepresentation(MediaType mediaType, OpenAcdQueuesBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdQueuesRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-queue", OpenAcdQueuesBundleRestInfo.class);
            xstream.alias("queue", OpenAcdQueueRestInfoFull.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
            xstream.alias("agentGroup", OpenAcdAgentGroupRestInfo.class);
            xstream.alias("step", OpenAcdRecipeStepRestInfo.class);
            xstream.alias("condition", OpenAcdRecipeConditionRestInfo.class);
            xstream.alias("action", OpenAcdRecipeActionRestInfo.class);
        }
    }

    static class OpenAcdQueueRepresentation extends XStreamRepresentation<OpenAcdQueueRestInfoFull> {

        public OpenAcdQueueRepresentation(MediaType mediaType, OpenAcdQueueRestInfoFull object) {
            super(mediaType, object);
        }

        public OpenAcdQueueRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("queue", OpenAcdQueueRestInfoFull.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
            xstream.alias("agentGroup", OpenAcdAgentGroupRestInfo.class);
            xstream.alias("step", OpenAcdRecipeStepRestInfo.class);
            xstream.alias("condition", OpenAcdRecipeConditionRestInfo.class);
            xstream.alias("action", OpenAcdRecipeActionRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdQueuesBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdQueueRestInfoFull> m_queues;

        public OpenAcdQueuesBundleRestInfo(List<OpenAcdQueueRestInfoFull> queues, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_queues = queues;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdQueueRestInfoFull> getQueues() {
            return m_queues;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
