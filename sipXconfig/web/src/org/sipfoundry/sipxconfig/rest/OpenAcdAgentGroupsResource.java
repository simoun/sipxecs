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
import java.util.Collections;
import java.util.Comparator;

import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Form;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.springframework.beans.factory.annotation.Required;
import com.thoughtworks.xstream.XStream;
import org.sipfoundry.sipxconfig.openacd.OpenAcdAgentGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdSkill;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;


public class OpenAcdAgentGroupsResource extends UserResource {

    private OpenAcdContext m_openAcdContext;

    private String m_sortDirectionString;
    private String m_sortFieldString;
    private SortDirection m_sortDirection;
    private SortField m_sortField;
    private String m_pageNumberString;
    private String m_resultsPerPageString;

    // default 0 indicates unused
    private int m_pageNumber = 0;
    private int m_resultsPerPage = 0;
    private Boolean m_paginate = false;

    private enum SortDirection {
	FORWARD, REVERSE, NONE
    }

    private enum SortField {
	NAME, DESCRIPTION
    }


    @Override
    public void init(Context context, Request request, Response response) {
        super.init(context, request, response);
        getVariants().add(new Variant(TEXT_XML));
        getVariants().add(new Variant(APPLICATION_JSON));

	// pull parameters from url (get)
	Form form = getRequest().getResourceRef().getQueryAsForm();
        m_sortDirectionString = form.getFirstValue("sortdir");
        m_sortFieldString = form.getFirstValue("sortby");
	m_pageNumberString = form.getFirstValue("page");
	m_resultsPerPageString = form.getFirstValue("pagesize");

	try {
	    m_sortDirection = SortDirection.valueOf(m_sortDirectionString.toUpperCase());
	}
	catch (Exception exception) {
	    // default to no sort
	    m_sortDirection = SortDirection.NONE;
	}

	try {
	    m_sortField = SortField.valueOf(m_sortFieldString.toUpperCase());
	}
	catch (Exception exception) {
	    // default
	    m_sortField = SortField.NAME;
	}

	// must specify both PageNumber and ResultsPerPage together
	try {
	    m_pageNumber = Integer.parseInt(m_pageNumberString);
	    m_resultsPerPage = Integer.parseInt(m_resultsPerPageString);
	}
	catch (Exception exception) {
	    // default 0 for nothing
	    m_pageNumber = 0;
	    m_resultsPerPage = 0;
	}

	// check for outrageous values or lack of parameters
	if ((m_pageNumber < 1) || (m_resultsPerPage < 1)) {
	    m_pageNumber = 0;
	    m_resultsPerPage = 0;
	    m_paginate = false;
	}
	else {
	    m_paginate = true;
	}
    }

    @Override
    public boolean allowGet() {
        return true;
    }

    @Override
    public Representation represent(Variant variant) throws ResourceException {
	List<OpenAcdAgentGroup> agentGroups = m_openAcdContext.getAgentGroups();
        List<OpenAcdAgentGroupRestInfo> agentGroupsRestInfo = new ArrayList<OpenAcdAgentGroupRestInfo>();
	MetadataRestInfo metadataRestInfo;

	// sort groups if specified
	SortGroups(agentGroups);

	// set requested agents groups and get resulting metadata
	metadataRestInfo = AddAgentGroups(agentGroupsRestInfo, agentGroups);

	// create final restinfo
	OpenAcdAgentGroupsBundleRestInfo agentGroupsBundleRestInfo = new OpenAcdAgentGroupsBundleRestInfo(agentGroupsRestInfo, metadataRestInfo);

        return new OpenAcdAgentGroupsRepresentation(variant.getMediaType(), agentGroupsBundleRestInfo);
    }

    private MetadataRestInfo AddAgentGroups(List<OpenAcdAgentGroupRestInfo> agentGroupsRestInfo, List<OpenAcdAgentGroup> agentGroups) {
	int totalResults = agentGroups.size();
	List<OpenAcdSkillRestInfo> skillsRestInfo;

	int startIndex, endIndex;
	int currentPage, totalPages, resultsPerPage;

	// determine pagination
	if (m_paginate) {
	    currentPage = m_pageNumber;
	    resultsPerPage = m_resultsPerPage;
	    totalPages = ((totalResults - 1) / resultsPerPage) + 1;

	    // check if only one page
	    //if (resultsPerPage >= totalResults) {
	    if (totalPages == 1) {
		startIndex = 0;
		endIndex = totalResults - 1;
		currentPage = 1;
		// design decision: should the resultsPerPage actually be set to totalResults?
		// since totalResults are already available preserve call value
	    }
	    else {
		// check if specified page number is on or beyoned last page (then use last page)
		if (currentPage >= totalPages) {
		    currentPage = totalPages;
		    startIndex = (totalPages - 1) * resultsPerPage;
		    endIndex = totalResults - 1;
		}
		else {
		    startIndex = (currentPage - 1) * resultsPerPage;
		    endIndex = startIndex + resultsPerPage - 1;
		}
	    }
	}
	else {
	    // default values assuming no pagination
	    startIndex = 0;
	    endIndex = totalResults - 1;
	    currentPage = 1;
	    totalPages = 1;
	    resultsPerPage = totalResults;
	}

	// create list of group restinfos
        for (int index = startIndex; index <= endIndex; index++) {
	    OpenAcdAgentGroup agentGroup = agentGroups.get(index);

	    // create list of skill restinfos for single group
	    Set<OpenAcdSkill> groupSkills = agentGroup.getSkills();
	    skillsRestInfo = new ArrayList<OpenAcdSkillRestInfo>(groupSkills.size());
	    for (OpenAcdSkill groupSkill : groupSkills) {
		OpenAcdSkillRestInfo skillRestInfo = new OpenAcdSkillRestInfo(groupSkill);
		skillsRestInfo.add(skillRestInfo);
	    }

            OpenAcdAgentGroupRestInfo agentGroupRestInfo = new OpenAcdAgentGroupRestInfo(agentGroup, skillsRestInfo);
            agentGroupsRestInfo.add(agentGroupRestInfo);
        }

	// create metadata about agent groups
	MetadataRestInfo metadata = new MetadataRestInfo(totalResults, currentPage, totalPages, resultsPerPage);
	return metadata;
    }

    private void SortGroups(List<OpenAcdAgentGroup> agentGroups) {

	// sort groups if requested (will simply leave as creation order if unrecognized parameters)
	switch (m_sortDirection) {
	case FORWARD:
	    
	    switch (m_sortField) {
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
	    
	    break;
	    
	case REVERSE:
	    switch (m_sortField) {
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
	    
	    break;
	}
    }

    static class OpenAcdAgentGroupsRepresentation extends XStreamRepresentation<OpenAcdAgentGroupsBundleRestInfo> {

        public OpenAcdAgentGroupsRepresentation(MediaType mediaType, OpenAcdAgentGroupsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdAgentGroupsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            //xstream.alias("openAcdGroups", List.class);
            xstream.alias("openacd-agent-group", OpenAcdAgentGroupsBundleRestInfo.class);
            xstream.alias("group", OpenAcdAgentGroupRestInfo.class);
            xstream.alias("skill", OpenAcdSkillRestInfo.class);
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

	public MetadataRestInfo(int totalResults, int currentPage, int totalPages, int resultsPerPage) {
	    m_totalResults = totalResults;
	    m_currentPage = currentPage;
	    m_totalPages = totalPages;
	    m_resultsPerPage = resultsPerPage;
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


    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
