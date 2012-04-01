/*
 *
 *  OpenAcdLinesResource.java - A Restlet to read Skill data from OpenACD within SipXecs
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

import org.sipfoundry.sipxconfig.freeswitch.FreeswitchCondition;
import org.sipfoundry.sipxconfig.openacd.OpenAcdLine;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.openacd.OpenAcdQueueGroup;
import org.sipfoundry.sipxconfig.openacd.OpenAcdRecipeStep;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.MetadataRestInfo;

// OpenAcdLines are different
// --------------------------
// Lines is inconsistent with other OpenACD objects.
// OpenAcdContext does not contain functions for getLineById(), saveLine() or removeLine().
// A Set of Lines is obtained using getLines() and only set operations are available  
// so this API will appear slightly different than other APIs, although attempts have been made to preserve general structure.
public class OpenAcdLinesResource extends UserResource {
    
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

   
    // GET - Retrieve all and single item
    // ----------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        OpenAcdLineRestInfo lineRestInfo;
        String idString = (String) getRequest().getAttributes().get("id");

        
        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                lineRestInfo = createLineRestInfo(idInt);
            }
            catch (Exception exception) {
                return OpenAcdUtilities.getResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            // return representation
            return new OpenAcdLineRepresentation(variant.getMediaType(), lineRestInfo);
        }


        // if not single, process request for list
        List<OpenAcdLine> lines = new ArrayList<OpenAcdLine> (m_openAcdContext.getLines());
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

        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                line = getLineById(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;                
            }

            // copy values over to existing
            try {
                updateLine(line, lineRestInfo);
                saveLine(line);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Line failed");
                return;                                
            }
            
            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_UPDATED, line.getId(), "Updated Line");

            return;
        }


        // otherwise add new
        try {
            // lines are created using a function without passing an object to it, so perform creation and assignment within createLine
            createLine(lineRestInfo);
        }
        catch (Exception exception) {
            OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Line failed");
            return;                                
        }
        
        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_CREATED, line.getId(), "Created Line");        
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
                line = getLineById(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;                
            }

            // deleteLine() not available from openAcdContext
            m_openAcdContext.getLines().remove(line);

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_DELETED, line.getId(), "Deleted Line");
            
            return;
        }

        // no id string
        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_MISSING_INPUT, "ID value missing");
    }

    
    // Helper functions
    // ----------------

    private OpenAcdLine getLineById(int id) throws ResourceException {
        OpenAcdLine line = null;
        
        for (OpenAcdLine currentLine : m_openAcdContext.getLines()) {
            if(currentLine.getId() == id)
            {
                line = currentLine;
                break;
            }
        }
        
        // duplicate behavior of standard OpenAcdContext.getXById() functions
        if (line == null) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "No row with the given identifier exists: " + id);
        }
        
        return line;
    }
    
    private void saveLine(OpenAcdLine line) throws ResourceException {
        m_openAcdContext.getLines().remove(getLineById(line.getId()));
        m_openAcdContext.getLines().add(line);
    }
    
    private OpenAcdLineRestInfo createLineRestInfo(int id) throws ResourceException {
        OpenAcdLineRestInfo lineRestInfo;

        OpenAcdLine line = getLineById(id);
        lineRestInfo = new OpenAcdLineRestInfo(line);

        return lineRestInfo;
    }

    private MetadataRestInfo addLines(List<OpenAcdLineRestInfo> linesRestInfo, List<OpenAcdLine> lines) {
        OpenAcdLineRestInfo lineRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, lines.size());

        // create list of line restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdLine line = lines.get(index);

            lineRestInfo = new OpenAcdLineRestInfo(line);
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
                Collections.sort(lines, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdLine line1 = (OpenAcdLine) object1;
                        OpenAcdLine line2 = (OpenAcdLine) object2;
                        return line1.getName().compareToIgnoreCase(line2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(lines, new Comparator(){

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
                Collections.sort(lines, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdLine line1 = (OpenAcdLine) object1;
                        OpenAcdLine line2 = (OpenAcdLine) object2;
                        return line2.getName().compareToIgnoreCase(line1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(lines, new Comparator(){

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

        //line.setExtension(lineRestInfo.getExtension());
        //line.setRegex(lineRestInfo.getRegex());
        line.getNumberCondition().setExpression(lineRestInfo.getExtension());
        line.getNumberCondition().setRegex(lineRestInfo.getRegex());
        
        line.setDid(lineRestInfo.getDIDNumber());
        line.setDescription(lineRestInfo.getDescription());
        line.setAlias(lineRestInfo.getAlias());
    }

    private void createLine(OpenAcdLineRestInfo lineRestInfo) throws ResourceException {
        OpenAcdLine line = m_openAcdContext.newOpenAcdLine();

        // copy fields from rest info
        line.setName(lineRestInfo.getName());

        //line.setExtension(lineRestInfo.getExtension());
        //line.setRegex(lineRestInfo.getRegex());
        line.getNumberCondition().setExpression(lineRestInfo.getExtension());
        line.getNumberCondition().setRegex(lineRestInfo.getRegex());        
        
        line.setDid(lineRestInfo.getDIDNumber());
        line.setDescription(lineRestInfo.getDescription());
        line.setAlias(lineRestInfo.getAlias());
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

        public OpenAcdLineRestInfo(OpenAcdLine line) {
            m_id = line.getId();
            m_name = line.getName();
            m_description = line.getDescription();
            m_extension = line.getExtension();
            m_regex = line.getRegex();
            m_didnumber = line.getDid();
            m_alias = line.getAlias();
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
        
        public String getExtension(){
        	return m_extension;
        }
        
        public boolean getRegex(){
        	return m_regex;
        }
        
        public String getDIDNumber(){
        	return m_didnumber;
        }
        
        public String getAlias() {
        	return m_alias;
        }
        
        // need queue, client, allow voicemail, answer supervision mode, welcome message, options (list)
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
