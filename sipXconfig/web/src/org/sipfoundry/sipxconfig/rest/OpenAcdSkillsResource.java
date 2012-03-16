/*
 *
 *  OpenAcdAgentGroupsResource.java - A Restlet to read Skill data from OpenACD within SipXecs
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
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkillGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.SortInfo;

public class OpenAcdSkillsResource extends UserResource {

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

    // GET - Retrieve all and single Skill
    // -----------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for a single skill
        OpenAcdSkillRestInfo skillRestInfo;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            skillRestInfo = getSkillRestInfoById(idInt);

            // finally return group representation
            return new OpenAcdSkillRepresentation(variant.getMediaType(), skillRestInfo);
        }


        // if not single, process request for all
        List<OpenAcdSkill> skills = m_openAcdContext.getSkills();
        List<OpenAcdSkillRestInfo> skillsRestInfo = new ArrayList<OpenAcdSkillRestInfo>();
        Form form = getRequest().getResourceRef().getQueryAsForm();
        MetadataRestInfo metadataRestInfo;

        // sort groups if specified
        sortSkills(skills);

        // set requested agents groups and get resulting metadata
        metadataRestInfo = addSkills(skillsRestInfo, skills);

        // create final restinfo
        OpenAcdSkillsBundleRestInfo skillsBundleRestInfo = new OpenAcdSkillsBundleRestInfo(skillsRestInfo, metadataRestInfo);

        return new OpenAcdSkillsRepresentation(variant.getMediaType(), skillsBundleRestInfo);
    }


    // PUT - Update or Add single Skill
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdSkillRepresentation representation = new OpenAcdSkillRepresentation(entity);
        OpenAcdSkillRestInfo skillRestInfo = representation.getObject();
        OpenAcdSkill skill;

        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            skill = m_openAcdContext.getSkillById(idInt);

            // copy values over to existing
            updateSkill(skill, skillRestInfo);
            m_openAcdContext.saveSkill(skill);

            return;
        }


        // otherwise add new
        skill = createSkill(skillRestInfo);
        m_openAcdContext.saveSkill(skill);
        getResponse().setStatus(Status.SUCCESS_CREATED);
    }


    // DELETE - Delete single Skill
    // ----------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdSkill skill;

        // get id then delete a single group
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            skill = m_openAcdContext.getSkillById(idInt);

            m_openAcdContext.deleteSkill(skill);

            return;
        }

        // no id string
        getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
    }


    // Helper functions
    // ----------------

    private OpenAcdSkillRestInfo getSkillRestInfoById(int id) throws ResourceException {
        OpenAcdSkillRestInfo skillRestInfo;

        try {
            skillRestInfo = createSkillRestInfo(id);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "Group ID " + id + " not found.");
        }

        return skillRestInfo;
    }

    private OpenAcdSkillRestInfo createSkillRestInfo(int id) {
        OpenAcdSkillRestInfo skillRestInfo;
        OpenAcdSkill skill = m_openAcdContext.getSkillById(id);

        skillRestInfo = new OpenAcdSkillRestInfo(skill);

        return skillRestInfo;
    }

    private MetadataRestInfo addSkills(List<OpenAcdSkillRestInfo> skillsRestInfo, List<OpenAcdSkill> skills) {
        OpenAcdSkillRestInfo skillRestInfo;
        
        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, skills.size());

        // create list of skill restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdSkill skill = skills.get(index);

            skillRestInfo = new OpenAcdSkillRestInfo(skill);
            skillsRestInfo.add(skillRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortSkills(List<OpenAcdSkill> skills) {
        // sort groups if requested
        SortInfo sortInfo = OpenAcdUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(skills, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return skill1.getName().compareToIgnoreCase(skill2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(skills, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return skill1.getDescription().compareToIgnoreCase(skill2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(skills, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return skill2.getName().compareToIgnoreCase(skill1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(skills, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return skill2.getDescription().compareToIgnoreCase(skill1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateSkill(OpenAcdSkill skill, OpenAcdSkillRestInfo skillRestInfo) throws ResourceException {
        OpenAcdSkillGroup skillGroup;
        String tempString;
        int groupId = 0;
        
        // do not allow empty name
        tempString = skillRestInfo.getName();
        if (!tempString.isEmpty()) {
            skill.setName(tempString);
        }

        skill.setDescription(skillRestInfo.getDescription());

        skillGroup = getSkillGroup(skillRestInfo);
        skill.setGroup(skillGroup);
    }

    private OpenAcdSkill createSkill(OpenAcdSkillRestInfo skillRestInfo) throws ResourceException {
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

    private OpenAcdSkillGroup getSkillGroup(OpenAcdSkillRestInfo skillRestInfo) throws ResourceException {
        OpenAcdSkillGroup skillGroup;
        int groupId = 0;
        
        try {
            groupId = skillRestInfo.getId();
            skillGroup = m_openAcdContext.getSkillGroupById(groupId);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "Skill Group ID " + groupId + " not found.");
        }
        
        return skillGroup;
    }

    
    // REST Representations
    // --------------------

    static class OpenAcdSkillsRepresentation extends XStreamRepresentation<OpenAcdSkillsBundleRestInfo> {

        public OpenAcdSkillsRepresentation(MediaType mediaType, OpenAcdSkillsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdSkillsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-skill", OpenAcdSkillsBundleRestInfo.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
        }
    }

    static class OpenAcdSkillRepresentation extends XStreamRepresentation<OpenAcdSkillRestInfo> {

        public OpenAcdSkillRepresentation(MediaType mediaType, OpenAcdSkillRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdSkillRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdSkillsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdSkillRestInfo> m_skills;

        public OpenAcdSkillsBundleRestInfo(List<OpenAcdSkillRestInfo> skills, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_skills = skills;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
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
        private final String m_atom;
        private final String m_groupName;
        private final int m_groupId;

        public OpenAcdSkillRestInfo(OpenAcdSkill skill) {
            m_id = skill.getId();
            m_name = skill.getName();
            m_description = skill.getDescription();
            m_atom = skill.getAtom();
            m_groupName = skill.getGroupName();
            m_groupId = skill.getGroup().getId();
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

        public String getAtom() {
            return m_atom;
        }

        public String getGroupName() {
            return m_groupName;
        }

        public int getGroupId() {
            return m_groupId;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
