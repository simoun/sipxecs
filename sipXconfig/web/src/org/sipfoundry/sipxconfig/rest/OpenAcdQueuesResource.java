/*
 *
 *  OpenAcdQueuesResource.java - A Restlet to read Skill data from OpenACD within SipXecs
 *  Copyright (C) 2012 PATLive, I. Wesson
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
import java.util.List;
import java.util.Collection;
import java.util.HashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

import org.jfree.chart.axis.QuarterDateFormat;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.springframework.beans.factory.annotation.Required;
import com.thoughtworks.xstream.XStream;

import org.sipfoundry.sipxconfig.openacd.OpenAcdAgentGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueue;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueueGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdQueueRestInfoFull;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdSkillRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdAgentGroupRestInfo;

public class OpenAcdQueuesResource extends UserResource {

    private OpenAcdContext m_openAcdContext;
    private Form m_form;

    // use to define all possible sort fields
    enum SortField
    {
        NAME, DESCRIPTION, NONE;

        public static SortField toSortField(String fieldString)
        {
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
        OpenAcdQueueRestInfoFull queueRestInfo;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                queueRestInfo = createQueueRestInfo(idInt);
            }
            catch (Exception exception) {
                return OpenAcdUtilities.getResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            // return representation
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
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Queue failed");
                return;                                
            }

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_UPDATED, queue.getId(), "Updated Queue");

            return;
        }


        // otherwise add new
        try {
            queue = createQueue(queueRestInfo);
            m_openAcdContext.saveQueue(queue);
        }
        catch (Exception exception) {
            OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Queue failed");
            return;                                
        }

        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_CREATED, queue.getId(), "Created Queue");        
    }


    // DELETE - Delete single Skill
    // ----------------------------

    // deleteQueue() not available from openAcdContext
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

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_DELETED, queue.getId(), "Deleted Queue");

            return;
        }

        // no id string
        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_MISSING_INPUT, "ID value missing");
    }


    // Helper functions
    // ----------------

    private OpenAcdQueueRestInfoFull createQueueRestInfo(int id) throws ResourceException {
        OpenAcdQueueRestInfoFull queueRestInfo;
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        List<OpenAcdAgentGroupRestInfo> agentGroupsRestInfo;

        try {
            OpenAcdQueue queue = m_openAcdContext.getQueueById(id);
            skillsRestInfo = createSkillsRestInfo(queue);
            agentGroupsRestInfo = createAgentGroupsRestInfo(queue); 
            queueRestInfo = new OpenAcdQueueRestInfoFull(queue, skillsRestInfo, agentGroupsRestInfo);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "ID " + id + " not found.");
        }

        return queueRestInfo;
    }

    private List<OpenAcdSkillRestInfo> createSkillsRestInfo(OpenAcdQueue queue) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        OpenAcdSkillRestInfo skillRestInfo;

        // create list of skill restinfos for single group
        Set<OpenAcdSkill> groupSkills = queue.getSkills();
        skillsRestInfo = new ArrayList<OpenAcdSkillRestInfo>(groupSkills.size());

        for (OpenAcdSkill groupSkill : groupSkills) {
            skillRestInfo = new OpenAcdSkillRestInfo(groupSkill);
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

        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, queues.size());

        // create list of queue restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdQueue queue = queues.get(index);

            skillsRestInfo = createSkillsRestInfo(queue);
            agentGroupRestInfo = createAgentGroupsRestInfo(queue); 
            queueRestInfo = new OpenAcdQueueRestInfoFull(queue, skillsRestInfo, agentGroupRestInfo);
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
                Collections.sort(queues, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueue queue1 = (OpenAcdQueue) object1;
                        OpenAcdQueue queue2 = (OpenAcdQueue) object2;
                        return queue1.getName().compareToIgnoreCase(queue2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(queues, new Comparator(){

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
                Collections.sort(queues, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueue queue1 = (OpenAcdQueue) object1;
                        OpenAcdQueue queue2 = (OpenAcdQueue) object2;
                        return queue2.getName().compareToIgnoreCase(queue1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(queues, new Comparator(){

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
    }

    private OpenAcdQueue createQueue(OpenAcdQueueRestInfoFull queueRestInfo) throws ResourceException {
        OpenAcdQueue queue = new OpenAcdQueue();
        OpenAcdQueueGroup queueGroup;

        // copy fields from rest info
        queue.setName(queueRestInfo.getName());
        queueGroup = getQueueGroup(queueRestInfo);

        queue.setGroup(queueGroup);
        queue.setDescription(queueRestInfo.getDescription());

        return queue;
    }

    private OpenAcdQueueGroup getQueueGroup(OpenAcdQueueRestInfoFull queueRestInfo) throws ResourceException {
        OpenAcdQueueGroup queueGroup;
        int groupId = 0;

        try {
            groupId = queueRestInfo.getId();
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
