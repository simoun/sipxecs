/*
 *
 *  OpenAcdAgentGroupsResource.java - A Restlet to read group data from OpenACD within SipXecs
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
import org.sipfoundry.sipxconfig.openacd.OpenAcdAgentGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.SortInfo;

public class OpenAcdAgentGroupsResource extends UserResource {

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
        OpenAcdAgentGroupRestInfo agentGroupRestInfo;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            agentGroupRestInfo = getAgentGroupRestInfoById(idInt);

            // finally return group representation
            return new OpenAcdAgentGroupRepresentation(variant.getMediaType(), agentGroupRestInfo);
        }


        // if not single, process request for all
        List<OpenAcdAgentGroup> agentGroups = m_openAcdContext.getAgentGroups();
        List<OpenAcdAgentGroupRestInfo> agentGroupsRestInfo = new ArrayList<OpenAcdAgentGroupRestInfo>();
        Form form = getRequest().getResourceRef().getQueryAsForm();
        MetadataRestInfo metadataRestInfo;

        // sort if specified
        sortGroups(agentGroups);

        // set requested based on pagination and get resulting metadata
        metadataRestInfo = addAgentGroups(agentGroupsRestInfo, agentGroups);

        // create final restinfo
        OpenAcdAgentGroupsBundleRestInfo agentGroupsBundleRestInfo = new OpenAcdAgentGroupsBundleRestInfo(agentGroupsRestInfo, metadataRestInfo);

        return new OpenAcdAgentGroupsRepresentation(variant.getMediaType(), agentGroupsBundleRestInfo);
    }


    // PUT - Update or Add single Group
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get group from body
        OpenAcdAgentGroupRepresentation representation = new OpenAcdAgentGroupRepresentation(entity);
        OpenAcdAgentGroupRestInfo agentGroupRestInfo = representation.getObject();
        OpenAcdAgentGroup agentGroup;

        // if have id then update a single group
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            agentGroup = m_openAcdContext.getAgentGroupById(idInt);

            // copy values over to existing group
            updateAgentGroup(agentGroup, agentGroupRestInfo);
            m_openAcdContext.saveAgentGroup(agentGroup);

            return;
        }


        // otherwise add new agent group
        agentGroup = createOpenAcdAgentGroup(agentGroupRestInfo);
        m_openAcdContext.saveAgentGroup(agentGroup);
        getResponse().setStatus(Status.SUCCESS_CREATED);
    }


    // DELETE - Delete single Group
    // --------------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdAgentGroup agentGroup;

        // get id then delete a single group
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            agentGroup = m_openAcdContext.getAgentGroupById(idInt);

            m_openAcdContext.deleteAgentGroup(agentGroup);

            return;
        }

        // no id string
        getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
    }


    // Helper functions
    // ----------------

    private OpenAcdAgentGroupRestInfo getAgentGroupRestInfoById(int id) throws ResourceException {
        OpenAcdAgentGroupRestInfo agentGroupRestInfo;

        try {
            agentGroupRestInfo = createAgentGroupRestInfo(id);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "ID " + id + " not found.");
        }

        return agentGroupRestInfo;
    }

    private OpenAcdAgentGroupRestInfo createAgentGroupRestInfo(int groupId) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        OpenAcdAgentGroupRestInfo agentGroupRestInfo;
        OpenAcdAgentGroup agentGroup = m_openAcdContext.getAgentGroupById(groupId);

        skillsRestInfo = createSkillsRestInfo(agentGroup);
        agentGroupRestInfo = new OpenAcdAgentGroupRestInfo(agentGroup, skillsRestInfo);

        return agentGroupRestInfo;
    }

    private void updateAgentGroup(OpenAcdAgentGroup agentGroup, OpenAcdAgentGroupRestInfo agentGroupRestInfo) {
        String tempString;

        // do not allow empty name
        tempString = agentGroupRestInfo.getName();
        if (!tempString.isEmpty()) {
            agentGroup.setName(tempString);
        }

        agentGroup.setDescription(agentGroupRestInfo.getDescription());

        // remove all current skills
        agentGroup.getSkills().clear();

        // set skills
        OpenAcdSkill skill;
        List<OpenAcdSkillRestInfo> skillsRestInfo = agentGroupRestInfo.getSkills();
        for (OpenAcdSkillRestInfo skillRestInfo : skillsRestInfo) {
            skill = m_openAcdContext.getSkillById(skillRestInfo.getId());
            agentGroup.addSkill(skill);
        }
    }

    private MetadataRestInfo addAgentGroups(List<OpenAcdAgentGroupRestInfo> agentGroupsRestInfo, List<OpenAcdAgentGroup> agentGroups) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, agentGroups.size());

        // create list of group restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdAgentGroup agentGroup = agentGroups.get(index);
            skillsRestInfo = createSkillsRestInfo(agentGroup);

            OpenAcdAgentGroupRestInfo agentGroupRestInfo = new OpenAcdAgentGroupRestInfo(agentGroup, skillsRestInfo);
            agentGroupsRestInfo.add(agentGroupRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private List<OpenAcdSkillRestInfo> createSkillsRestInfo(OpenAcdAgentGroup agentGroup) {
        List<OpenAcdSkillRestInfo> skillsRestInfo;
        OpenAcdSkillRestInfo skillRestInfo;

        // create list of skill restinfos for single group
        Set<OpenAcdSkill> groupSkills = agentGroup.getSkills();
        skillsRestInfo = new ArrayList<OpenAcdSkillRestInfo>(groupSkills.size());

        for (OpenAcdSkill groupSkill : groupSkills) {
            skillRestInfo = new OpenAcdSkillRestInfo(groupSkill);
            skillsRestInfo.add(skillRestInfo);
        }

        return skillsRestInfo;
    }

    private void sortGroups(List<OpenAcdAgentGroup> agentGroups) {
        // sort groups if requested
        SortInfo sortInfo = OpenAcdUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(agentGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdAgentGroup agentGroup1 = (OpenAcdAgentGroup) object1;
                        OpenAcdAgentGroup agentGroup2 = (OpenAcdAgentGroup) object2;
                        return agentGroup1.getName().compareToIgnoreCase(agentGroup2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(agentGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdAgentGroup agentGroup1 = (OpenAcdAgentGroup) object1;
                        OpenAcdAgentGroup agentGroup2 = (OpenAcdAgentGroup) object2;
                        return agentGroup1.getDescription().compareToIgnoreCase(agentGroup2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(agentGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdAgentGroup agentGroup1 = (OpenAcdAgentGroup) object1;
                        OpenAcdAgentGroup agentGroup2 = (OpenAcdAgentGroup) object2;
                        return agentGroup2.getName().compareToIgnoreCase(agentGroup1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(agentGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdAgentGroup agentGroup1 = (OpenAcdAgentGroup) object1;
                        OpenAcdAgentGroup agentGroup2 = (OpenAcdAgentGroup) object2;
                        return agentGroup2.getDescription().compareToIgnoreCase(agentGroup1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private OpenAcdAgentGroup createOpenAcdAgentGroup(OpenAcdAgentGroupRestInfo agentGroupRestInfo) {
        OpenAcdAgentGroup agentGroup = new OpenAcdAgentGroup();

        // copy fields from rest info
        agentGroup.setName(agentGroupRestInfo.getName());
        agentGroup.setDescription(agentGroupRestInfo.getDescription());

        // add skills
        Set<OpenAcdSkill> skills = new LinkedHashSet<OpenAcdSkill>();
        List<OpenAcdSkillRestInfo> skillsRestInfo = agentGroupRestInfo.getSkills();

        for (OpenAcdSkillRestInfo skillRestInfo : skillsRestInfo) {
            skills.add(m_openAcdContext.getSkillById(skillRestInfo.getId()));
        }

        agentGroup.setSkills(skills);

        return agentGroup;
    }


    // REST Representations
    // --------------------

    static class OpenAcdAgentGroupsRepresentation extends XStreamRepresentation<OpenAcdAgentGroupsBundleRestInfo> {

        public OpenAcdAgentGroupsRepresentation(MediaType mediaType, OpenAcdAgentGroupsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdAgentGroupsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-agent-group", OpenAcdAgentGroupsBundleRestInfo.class);
            xstream.alias("group", OpenAcdAgentGroupRestInfo.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
        }
    }

    static class OpenAcdAgentGroupRepresentation extends XStreamRepresentation<OpenAcdAgentGroupRestInfo> {

        public OpenAcdAgentGroupRepresentation(MediaType mediaType, OpenAcdAgentGroupRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdAgentGroupRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("group", OpenAcdAgentGroupRestInfo.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdAgentGroupsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdAgentGroupRestInfo> m_groups;

        public OpenAcdAgentGroupsBundleRestInfo(List<OpenAcdAgentGroupRestInfo> agentGroups, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_groups = agentGroups;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdAgentGroupRestInfo> getGroups() {
            return m_groups;
        }
    }

    static class OpenAcdAgentGroupRestInfo {
        private final String m_name;
        private final int m_id;
        private final String m_description;
        private final List<OpenAcdSkillRestInfo> m_skills;

        public OpenAcdAgentGroupRestInfo(OpenAcdAgentGroup agentGroup, List<OpenAcdSkillRestInfo> skills) {
            m_name = agentGroup.getName();
            m_id = agentGroup.getId();
            m_description = agentGroup.getDescription();
            m_skills = skills;
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

    static class OpenAcdSkillRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;
        private final String m_groupName;

        public OpenAcdSkillRestInfo(OpenAcdSkill skill) {
            m_id = skill.getId();
            m_name = skill.getName();
            m_description = skill.getDescription();
            m_groupName = skill.getGroupName();
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

        public String getGroupName() {
            return m_groupName;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
