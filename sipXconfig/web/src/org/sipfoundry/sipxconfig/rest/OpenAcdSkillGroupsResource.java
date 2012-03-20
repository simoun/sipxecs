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
import java.util.Collection;
import java.util.HashSet;
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

public class OpenAcdSkillGroupsResource extends UserResource {

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

    // GET - Retrieve all and single Skill
    // -----------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        OpenAcdSkillGroupRestInfo skillGroupRestInfo;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            skillGroupRestInfo = getSkillGroupRestInfoById(idInt);

            // return representation
            return new OpenAcdSkillGroupRepresentation(variant.getMediaType(), skillGroupRestInfo);
        }


        // if not single, process request for list
        List<OpenAcdSkillGroup> skillGroups = m_openAcdContext.getSkillGroups();
        List<OpenAcdSkillGroupRestInfo> skillGroupsRestInfo = new ArrayList<OpenAcdSkillGroupRestInfo>();
        Form form = getRequest().getResourceRef().getQueryAsForm();
        MetadataRestInfo metadataRestInfo;

        // sort groups if specified
        sortSkillGroups(skillGroups);

        // set requested records and get resulting metadata
        metadataRestInfo = addSkillGroups(skillGroupsRestInfo, skillGroups);

        // create final restinfo
        OpenAcdSkillGroupsBundleRestInfo skillGroupsBundleRestInfo = new OpenAcdSkillGroupsBundleRestInfo(skillGroupsRestInfo, metadataRestInfo);

        return new OpenAcdSkillGroupsRepresentation(variant.getMediaType(), skillGroupsBundleRestInfo);
    }


    // PUT - Update or Add single Skill
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdSkillGroupRepresentation representation = new OpenAcdSkillGroupRepresentation(entity);
        OpenAcdSkillGroupRestInfo skillGroupRestInfo = representation.getObject();
        OpenAcdSkillGroup skillGroup;
        
        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            skillGroup = m_openAcdContext.getSkillGroupById(idInt);

            // copy values over to existing
            updateSkillGroup(skillGroup, skillGroupRestInfo);
            m_openAcdContext.saveSkillGroup(skillGroup);

            return;
        }


        // otherwise add new
        skillGroup = createSkillGroup(skillGroupRestInfo);
        m_openAcdContext.saveSkillGroup(skillGroup);
        getResponse().setStatus(Status.SUCCESS_CREATED);
    }


    // DELETE - Delete single Skill
    // ----------------------------

    // deleteSkillGroup() not available from openAcdContext
    @Override
    public void removeRepresentations() throws ResourceException {
        Collection<Integer> skillGroupIds = new HashSet<Integer>();
        
        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
            
            skillGroupIds.add(idInt);
            m_openAcdContext.removeSkillGroups(skillGroupIds);

            return;
        }

        // no id string
        getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
    }

    // Helper functions
    // ----------------

    private OpenAcdSkillGroupRestInfo getSkillGroupRestInfoById(int id) throws ResourceException {
        OpenAcdSkillGroupRestInfo skillGroupRestInfo;

        try {
            skillGroupRestInfo = createSkillGroupRestInfo(id);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "ID " + id + " not found.");
        }

        return skillGroupRestInfo;
    }

    private OpenAcdSkillGroupRestInfo createSkillGroupRestInfo(int id) {
        OpenAcdSkillGroupRestInfo skillRestInfo;
        OpenAcdSkillGroup skillGroup = m_openAcdContext.getSkillGroupById(id);

        skillRestInfo = new OpenAcdSkillGroupRestInfo(skillGroup);

        return skillRestInfo;
    }

    private MetadataRestInfo addSkillGroups(List<OpenAcdSkillGroupRestInfo> skillsRestInfo, List<OpenAcdSkillGroup> skillGroups) {
        OpenAcdSkillGroupRestInfo skillRestInfo;
        
        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, skillGroups.size());

        // create list of skill restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdSkillGroup skillGroup = skillGroups.get(index);

            skillRestInfo = new OpenAcdSkillGroupRestInfo(skillGroup);
            skillsRestInfo.add(skillRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortSkillGroups(List<OpenAcdSkillGroup> skillGroups) {
        // sort groups if requested
        SortInfo sortInfo = OpenAcdUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(skillGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkillGroup skillGroup1 = (OpenAcdSkillGroup) object1;
                        OpenAcdSkillGroup skillGroup2 = (OpenAcdSkillGroup) object2;
                        return skillGroup1.getName().compareToIgnoreCase(skillGroup2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(skillGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkillGroup skillGroup1 = (OpenAcdSkillGroup) object1;
                        OpenAcdSkillGroup skillGroup2 = (OpenAcdSkillGroup) object2;
                        return skillGroup1.getDescription().compareToIgnoreCase(skillGroup2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(skillGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkill skill1 = (OpenAcdSkill) object1;
                        OpenAcdSkill skill2 = (OpenAcdSkill) object2;
                        return skill2.getName().compareToIgnoreCase(skill1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(skillGroups, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdSkillGroup skillGroup1 = (OpenAcdSkillGroup) object1;
                        OpenAcdSkillGroup skillGroup2 = (OpenAcdSkillGroup) object2;
                        return skillGroup2.getDescription().compareToIgnoreCase(skillGroup1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateSkillGroup(OpenAcdSkillGroup skillGroup, OpenAcdSkillGroupRestInfo skillGroupRestInfo) throws ResourceException {
        String tempString;
        
        // do not allow empty name
        tempString = skillGroupRestInfo.getName();
        if (!tempString.isEmpty()) {
            skillGroup.setName(tempString);
        }

        skillGroup.setDescription(skillGroupRestInfo.getDescription());
    }

    private OpenAcdSkillGroup createSkillGroup(OpenAcdSkillGroupRestInfo skillGroupRestInfo) throws ResourceException {
        OpenAcdSkillGroup skillGroup = new OpenAcdSkillGroup();

        // copy fields from rest info
        skillGroup.setName(skillGroupRestInfo.getName());
        skillGroup.setDescription(skillGroupRestInfo.getDescription());

        return skillGroup;
    }

    
    // REST Representations
    // --------------------

    static class OpenAcdSkillGroupsRepresentation extends XStreamRepresentation<OpenAcdSkillGroupsBundleRestInfo> {

        public OpenAcdSkillGroupsRepresentation(MediaType mediaType, OpenAcdSkillGroupsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdSkillGroupsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-skill-group", OpenAcdSkillGroupsBundleRestInfo.class);
            xstream.alias("group", OpenAcdSkillGroupRestInfo.class);
        }
    }

    static class OpenAcdSkillGroupRepresentation extends XStreamRepresentation<OpenAcdSkillGroupRestInfo> {

        public OpenAcdSkillGroupRepresentation(MediaType mediaType, OpenAcdSkillGroupRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdSkillGroupRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("group", OpenAcdSkillGroupRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdSkillGroupsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdSkillGroupRestInfo> m_skills;

        public OpenAcdSkillGroupsBundleRestInfo(List<OpenAcdSkillGroupRestInfo> skills, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_skills = skills;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdSkillGroupRestInfo> getSkills() {
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

    static class OpenAcdSkillGroupRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;

        public OpenAcdSkillGroupRestInfo(OpenAcdSkillGroup skillGroup) {
            m_id = skillGroup.getId();
            m_name = skillGroup.getName();
            m_description = skillGroup.getDescription();
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
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
