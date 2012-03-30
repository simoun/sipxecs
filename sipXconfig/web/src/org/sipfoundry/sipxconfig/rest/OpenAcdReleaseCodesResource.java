/*
 *
 *  OpenAcdReleaseCodesResource.java - A Restlet to read Release Code data from OpenACD within SipXecs
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

import org.sipfoundry.sipxconfig.openacd.OpenAcdReleaseCode;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdReleaseCodeRestInfo;

public class OpenAcdReleaseCodesResource extends UserResource {

    private OpenAcdContext m_openAcdContext;
    private Form m_form;

    // use to define all possible sort fields
    enum SortField
    {
        LABEL, DESCRIPTION, NONE;

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

    // GET - Retrieve all and single Release Code
    // -------------------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        OpenAcdReleaseCodeRestInfo releaseCodeRestInfo;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                releaseCodeRestInfo = createReleaseCodeRestInfo(idInt);
            }
            catch (Exception exception) {
                return OpenAcdUtilities.getResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            // return representation
            return new OpenAcdReleaseCodeRepresentation(variant.getMediaType(), releaseCodeRestInfo);
        }


        // if not single, process request for list
        List<OpenAcdReleaseCode> releaseCodes = m_openAcdContext.getReleaseCodes();
        List<OpenAcdReleaseCodeRestInfo> releaseCodesRestInfo = new ArrayList<OpenAcdReleaseCodeRestInfo>();
        MetadataRestInfo metadataRestInfo;

        // sort groups if specified
        sortReleaseCodes(releaseCodes);

        // set requested records and get resulting metadata
        metadataRestInfo = addReleaseCodes(releaseCodesRestInfo, releaseCodes);

        // create final restinfo
        OpenAcdReleaseCodesBundleRestInfo releaseCodesBundleRestInfo = new OpenAcdReleaseCodesBundleRestInfo(releaseCodesRestInfo, metadataRestInfo);

        return new OpenAcdReleaseCodesRepresentation(variant.getMediaType(), releaseCodesBundleRestInfo);
    }


    // PUT - Update or Add single Skill
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdReleaseCodeRepresentation representation = new OpenAcdReleaseCodeRepresentation(entity);
        OpenAcdReleaseCodeRestInfo releaseCodeRestInfo = representation.getObject();
        OpenAcdReleaseCode releaseCode;

        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                releaseCode = m_openAcdContext.getReleaseCodeById(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;                
            }

            // copy values over to existing
            try {
                updateReleaseCode(releaseCode, releaseCodeRestInfo);
                m_openAcdContext.saveReleaseCode(releaseCode);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Release Code failed");
                return;                                
            }

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_UPDATED, releaseCode.getId(), "Updated Release Code");

            return;
        }


        // otherwise add new
        try {
            releaseCode = createReleaseCode(releaseCodeRestInfo);
            m_openAcdContext.saveReleaseCode(releaseCode);
        }
        catch (Exception exception) {
            OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Release Code failed");
            return;                                
        }

        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_CREATED, releaseCode.getId(), "Created Release Code");        
    }


    // DELETE - Delete single Skill
    // ----------------------------

    // deleteReleaseCode() not available from openAcdContext
    @Override
    public void removeRepresentations() throws ResourceException {
        // for some reason release codes are deleted by providing collection of ids, not by providing release code object
        Collection<Integer> releaseCodeIds = new HashSet<Integer>();

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                releaseCodeIds.add(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;                
            }

            m_openAcdContext.removeReleaseCodes(releaseCodeIds);

            return;
        }

        // no id string
        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_MISSING_INPUT, "ID value missing");
    }


    // Helper functions
    // ----------------

    private OpenAcdReleaseCodeRestInfo createReleaseCodeRestInfo(int id) throws ResourceException {
        OpenAcdReleaseCodeRestInfo releaseCodeRestInfo;

        try {
            OpenAcdReleaseCode releaseCode = m_openAcdContext.getReleaseCodeById(id);
            releaseCodeRestInfo = new OpenAcdReleaseCodeRestInfo(releaseCode);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "ID " + id + " not found.");
        }

        return releaseCodeRestInfo;
    }

    private MetadataRestInfo addReleaseCodes(List<OpenAcdReleaseCodeRestInfo> releaseCodesRestInfo, List<OpenAcdReleaseCode> releaseCodes) {
        OpenAcdReleaseCodeRestInfo releaseCodeRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, releaseCodes.size());

        // create list of releaseCode restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdReleaseCode releaseCode = releaseCodes.get(index);

            releaseCodeRestInfo = new OpenAcdReleaseCodeRestInfo(releaseCode);
            releaseCodesRestInfo.add(releaseCodeRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortReleaseCodes(List<OpenAcdReleaseCode> releaseCodes) {
        // sort groups if requested
        SortInfo sortInfo = OpenAcdUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case LABEL:
                Collections.sort(releaseCodes, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdReleaseCode releaseCode1 = (OpenAcdReleaseCode) object1;
                        OpenAcdReleaseCode releaseCode2 = (OpenAcdReleaseCode) object2;
                        return releaseCode1.getLabel().compareToIgnoreCase(releaseCode2.getLabel());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(releaseCodes, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdReleaseCode releaseCode1 = (OpenAcdReleaseCode) object1;
                        OpenAcdReleaseCode releaseCode2 = (OpenAcdReleaseCode) object2;
                        return releaseCode1.getDescription().compareToIgnoreCase(releaseCode2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case LABEL:
                Collections.sort(releaseCodes, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdReleaseCode releaseCode1 = (OpenAcdReleaseCode) object1;
                        OpenAcdReleaseCode releaseCode2 = (OpenAcdReleaseCode) object2;
                        return releaseCode2.getLabel().compareToIgnoreCase(releaseCode1.getLabel());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(releaseCodes, new Comparator(){

                    public int compare(Object object1, Object object2) {
                        OpenAcdReleaseCode releaseCode1 = (OpenAcdReleaseCode) object1;
                        OpenAcdReleaseCode releaseCode2 = (OpenAcdReleaseCode) object2;
                        return releaseCode2.getDescription().compareToIgnoreCase(releaseCode1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateReleaseCode(OpenAcdReleaseCode releaseCode, OpenAcdReleaseCodeRestInfo releaseCodeRestInfo) throws ResourceException {
        String tempString;

        // do not allow empty name
        tempString = releaseCodeRestInfo.getLabel();
        if (!tempString.isEmpty()) {
            releaseCode.setName(tempString);
        }

        releaseCode.setLabel(releaseCodeRestInfo.getLabel());
        releaseCode.setDescription(releaseCodeRestInfo.getDescription());
        releaseCode.setBias(releaseCodeRestInfo.getBias());
    }

    private OpenAcdReleaseCode createReleaseCode(OpenAcdReleaseCodeRestInfo releaseCodeRestInfo) throws ResourceException {
        OpenAcdReleaseCode releaseCode = new OpenAcdReleaseCode();

        // copy fields from rest info
        releaseCode.setLabel(releaseCodeRestInfo.getLabel());
        releaseCode.setDescription(releaseCodeRestInfo.getDescription());
        releaseCode.setBias(releaseCodeRestInfo.getBias());

        return releaseCode;
    }


    // REST Representations
    // --------------------

    static class OpenAcdReleaseCodesRepresentation extends XStreamRepresentation<OpenAcdReleaseCodesBundleRestInfo> {

        public OpenAcdReleaseCodesRepresentation(MediaType mediaType, OpenAcdReleaseCodesBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdReleaseCodesRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-release-code", OpenAcdReleaseCodesBundleRestInfo.class);
            xstream.alias("release-code", OpenAcdReleaseCodeRestInfo.class);
        }
    }

    static class OpenAcdReleaseCodeRepresentation extends XStreamRepresentation<OpenAcdReleaseCodeRestInfo> {

        public OpenAcdReleaseCodeRepresentation(MediaType mediaType, OpenAcdReleaseCodeRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdReleaseCodeRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("release-code", OpenAcdReleaseCodeRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdReleaseCodesBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdReleaseCodeRestInfo> m_releaseCodes;

        public OpenAcdReleaseCodesBundleRestInfo(List<OpenAcdReleaseCodeRestInfo> releaseCodes, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_releaseCodes = releaseCodes;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdReleaseCodeRestInfo> getReleaseCodes() {
            return m_releaseCodes;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}