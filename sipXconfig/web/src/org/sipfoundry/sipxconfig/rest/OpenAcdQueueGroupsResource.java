/*
 *
 *  OpenAcdQueueGroupsResource.java - A Restlet to read group data from OpenACD within SipXecs
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
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Comparator;

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
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueueGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.openacd.OpenAcdAgentGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdSkillRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdAgentGroupRestInfo;

public class OpenAcdQueueGroupsResource extends UserResource {

    private OpenAcdContext m_openAcdContext;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField
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

    // GET - Retrieve Groups and single Group
    // --------------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        OpenAcdQueueGroupRestInfo queueGroupRestInfo;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            queueGroupRestInfo = createQueueGroupRestInfo(idInt);

            // finally return group representation
            return new OpenAcdQueueGroupRepresentation(variant.getMediaType(), queueGroupRestInfo);
        }


        // if not single, process request for all
        List<OpenAcdQueueGroup> queueGroups = m_openAcdContext.getQueueGroups();
        List<OpenAcdQueueGroupRestInfo> queueGroupsRestInfo = new ArrayList<OpenAcdQueueGroupRestInfo>();
        Form form = getRequest().getResourceRef().getQueryAsForm();
        MetadataRestInfo metadataRestInfo;

        // sort if specified
        sortGroups(queueGroups);

        // set requested based on pagination and get resulting metadata
        metadataRestInfo = addQueueGroups(queueGroupsRestInfo, queueGroups);

        // create final restinfo
        OpenAcdQueueGroupsBundleRestInfo queueGroupsBundleRestInfo = new OpenAcdQueueGroupsBundleRestInfo(queueGroupsRestInfo, metadataRestInfo);

        return new OpenAcdQueueGroupsRepresentation(variant.getMediaType(), queueGroupsBundleRestInfo);
    }


    // PUT - Update or Add single Group
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get group from body
        OpenAcdQueueGroupRepresentation representation = new OpenAcdQueueGroupRepresentation(entity);
        OpenAcdQueueGroupRestInfo queueGroupRestInfo = representation.getObject();
        OpenAcdQueueGroup queueGroup;

        // if have id then update a single group
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            queueGroup = m_openAcdContext.getQueueGroupById(idInt);

            // copy values over to existing group
            updateQueueGroup(queueGroup, queueGroupRestInfo);
            m_openAcdContext.saveQueueGroup(queueGroup);

            return;
        }


        // otherwise add new agent group
        queueGroup = createOpenAcdQueueGroup(queueGroupRestInfo);
        m_openAcdContext.saveQueueGroup(queueGroup);
        getResponse().setStatus(Status.SUCCESS_CREATED);
    }


    // DELETE - Delete single Group
    // --------------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdQueueGroup queueGroup;

        // get id then delete a single group
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            queueGroup = m_openAcdContext.getQueueGroupById(idInt);

            m_openAcdContext.deleteQueueGroup(queueGroup);

            return;
        }

        // no id string
        getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
    }


    // Helper functions
    // ----------------

    private OpenAcdQueueGroupRestInfo createQueueGroupRestInfo(int id) throws ResourceException {
        OpenAcdQueueGroupRestInfo queueGroupRestInfo;
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        List<OpenAcdAgentGroupRestInfo> agentGroupRestInfo;

        try {
            OpenAcdQueueGroup queueGroup = m_openAcdContext.getQueueGroupById(id);

            skillsRestInfo = createSkillsRestInfo(queueGroup);
            agentGroupRestInfo = createAgentGroupsRestInfo(queueGroup);
            
            queueGroupRestInfo = new OpenAcdQueueGroupRestInfo(queueGroup, skillsRestInfo, agentGroupRestInfo);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "ID " + id + " not found.");
        }

        return queueGroupRestInfo;
    }
    
    private void updateQueueGroup(OpenAcdQueueGroup queueGroup, OpenAcdQueueGroupRestInfo queueGroupRestInfo) {
        String tempString;

        // do not allow empty name
        tempString = queueGroupRestInfo.getName();
        if (!tempString.isEmpty()) {
            queueGroup.setName(tempString);
        }

        queueGroup.setDescription(queueGroupRestInfo.getDescription());

        // remove all current skills
        queueGroup.getSkills().clear();

        // set skills
        OpenAcdSkill skill;
        List<OpenAcdSkillRestInfo> skillsRestInfo = queueGroupRestInfo.getSkills();
        for (OpenAcdSkillRestInfo skillRestInfo : skillsRestInfo) {
            skill = m_openAcdContext.getSkillById(skillRestInfo.getId());
            queueGroup.addSkill(skill);
        }
    }

    private MetadataRestInfo addQueueGroups(List<OpenAcdQueueGroupRestInfo> queueGroupsRestInfo, List<OpenAcdQueueGroup> queueGroups) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        List<OpenAcdAgentGroupRestInfo> agentGroupRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, queueGroups.size());

        // create list of group restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdQueueGroup queueGroup = queueGroups.get(index);

            skillsRestInfo = createSkillsRestInfo(queueGroup);
            agentGroupRestInfo = createAgentGroupsRestInfo(queueGroup);            
            
            OpenAcdQueueGroupRestInfo queueGroupRestInfo = new OpenAcdQueueGroupRestInfo(queueGroup, skillsRestInfo, agentGroupRestInfo);
            queueGroupsRestInfo.add(queueGroupRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private List<OpenAcdSkillRestInfo> createSkillsRestInfo(OpenAcdQueueGroup queueGroup) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        OpenAcdSkillRestInfo skillRestInfo;

        // create list of skill restinfos for single group
        Set<OpenAcdSkill> groupSkills = queueGroup.getSkills();
        skillsRestInfo = new ArrayList<OpenAcdSkillRestInfo>(groupSkills.size());

        for (OpenAcdSkill groupSkill : groupSkills) {
            skillRestInfo = new OpenAcdSkillRestInfo(groupSkill);
            skillsRestInfo.add(skillRestInfo);
        }

        return skillsRestInfo;
    }

    private List<OpenAcdAgentGroupRestInfo> createAgentGroupsRestInfo(OpenAcdQueueGroup queueGroup) {
        List<OpenAcdAgentGroupRestInfo> agentGroupsRestInfo;
        OpenAcdAgentGroupRestInfo agentGroupRestInfo;

        // create list of agent group restinfos for single group
        Set<OpenAcdAgentGroup> groupAgentGroups = queueGroup.getAgentGroups();
        agentGroupsRestInfo = new ArrayList<OpenAcdAgentGroupRestInfo>(groupAgentGroups.size());

        for (OpenAcdAgentGroup groupAgentGroup : groupAgentGroups) {
            agentGroupRestInfo = new OpenAcdAgentGroupRestInfo(groupAgentGroup);
            agentGroupsRestInfo.add(agentGroupRestInfo);
        }

        return agentGroupsRestInfo;
    }

    private void sortGroups(List<OpenAcdQueueGroup> queueGroups) {
        // sort groups if requested
        SortInfo sortInfo = OpenAcdUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(queueGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueueGroup queueGroup1 = (OpenAcdQueueGroup) object1;
                        OpenAcdQueueGroup queueGroup2 = (OpenAcdQueueGroup) object2;
                        return queueGroup1.getName().compareToIgnoreCase(queueGroup2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(queueGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueueGroup queueGroup1 = (OpenAcdQueueGroup) object1;
                        OpenAcdQueueGroup queueGroup2 = (OpenAcdQueueGroup) object2;
                        return queueGroup1.getDescription().compareToIgnoreCase(queueGroup2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(queueGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueueGroup queueGroup1 = (OpenAcdQueueGroup) object1;
                        OpenAcdQueueGroup queueGroup2 = (OpenAcdQueueGroup) object2;
                        return queueGroup2.getName().compareToIgnoreCase(queueGroup1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(queueGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdQueueGroup queueGroup1 = (OpenAcdQueueGroup) object1;
                        OpenAcdQueueGroup queueGroup2 = (OpenAcdQueueGroup) object2;
                        return queueGroup2.getDescription().compareToIgnoreCase(queueGroup1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private OpenAcdQueueGroup createOpenAcdQueueGroup(OpenAcdQueueGroupRestInfo queueGroupRestInfo) {
        OpenAcdQueueGroup queueGroup = new OpenAcdQueueGroup();

        // copy fields from rest info
        queueGroup.setName(queueGroupRestInfo.getName());
        queueGroup.setDescription(queueGroupRestInfo.getDescription());

        // add skills
        Set<OpenAcdSkill> skills = new LinkedHashSet<OpenAcdSkill>();
        List<OpenAcdSkillRestInfo> skillsRestInfo = queueGroupRestInfo.getSkills();

        for (OpenAcdSkillRestInfo skillRestInfo : skillsRestInfo) {
            skills.add(m_openAcdContext.getSkillById(skillRestInfo.getId()));
        }

        queueGroup.setSkills(skills);

        return queueGroup;
    }


    // REST Representations
    // --------------------

    static class OpenAcdQueueGroupsRepresentation extends XStreamRepresentation<OpenAcdQueueGroupsBundleRestInfo> {

        public OpenAcdQueueGroupsRepresentation(MediaType mediaType, OpenAcdQueueGroupsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdQueueGroupsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-queue-group", OpenAcdQueueGroupsBundleRestInfo.class);
            xstream.alias("group", OpenAcdQueueGroupRestInfo.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
            xstream.alias("agentGroup", OpenAcdAgentGroupRestInfo.class);
        }
    }

    static class OpenAcdQueueGroupRepresentation extends XStreamRepresentation<OpenAcdQueueGroupRestInfo> {

        public OpenAcdQueueGroupRepresentation(MediaType mediaType, OpenAcdQueueGroupRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdQueueGroupRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("group", OpenAcdQueueGroupRestInfo.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
            xstream.alias("agentGroup", OpenAcdAgentGroupRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdQueueGroupsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdQueueGroupRestInfo> m_groups;

        public OpenAcdQueueGroupsBundleRestInfo(List<OpenAcdQueueGroupRestInfo> queueGroups, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_groups = queueGroups;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdQueueGroupRestInfo> getGroups() {
            return m_groups;
        }
    }

    static class OpenAcdQueueGroupRestInfo {
        private final String m_name;
        private final int m_id;
        private final String m_description;
        private final List<OpenAcdSkillRestInfo> m_skills;
        private final List<OpenAcdAgentGroupRestInfo> m_agentGroups;

        public OpenAcdQueueGroupRestInfo(OpenAcdQueueGroup queueGroup, List<OpenAcdSkillRestInfo> skills, List<OpenAcdAgentGroupRestInfo> agentGroups) {
            m_name = queueGroup.getName();
            m_id = queueGroup.getId();
            m_description = queueGroup.getDescription();
            m_skills = skills;
            m_agentGroups = agentGroups;
        }

        public String getName() {
            return m_name;
        }

        public int getId() {
            return m_id;
        }

        public String getDescription() {
            return m_description;
        }

        public List<OpenAcdSkillRestInfo> getSkills() {
            return m_skills;
        }

        public List<OpenAcdAgentGroupRestInfo> getAgentGroups() {
            return m_agentGroups;
        }
    }

    static class MetadataRestInfo {
        private final int m_totalResults;
        private final int m_currentPage;
        private final int m_totalPages;
        private final int m_resultsPerPage;

        public MetadataRestInfo(PaginationInfo paginationInfo) {
            m_totalResults = paginationInfo.totalResults;
            m_currentPage = paginationInfo.pageNumber;
            m_totalPages = paginationInfo.totalPages;
            m_resultsPerPage = paginationInfo.resultsPerPage;
        }

        public int getTotalResults() {
            return m_totalResults;
        }

        public int getCurrentPage() {
            return m_currentPage;
        }

        public int getTotalPages() {
            return m_totalPages;
        }

        public int getResultsPerPage() {
            return m_resultsPerPage;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
