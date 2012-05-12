/*
 *
 *  BranchesResource.java - A Restlet to read Branch data from SipXecs
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.branch.Branch;
import org.sipfoundry.sipxconfig.branch.BranchManager;
import org.sipfoundry.sipxconfig.phonebook.Address;
import org.sipfoundry.sipxconfig.rest.RestUtilities.BranchRestInfoFull;
import org.sipfoundry.sipxconfig.rest.RestUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.RestUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class BranchesResource extends UserResource {

    private BranchManager m_branchManager;
    private Form m_form;

    // use to define all possible sort fields
    private enum SortField {
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

    // GET - Retrieve all and single Branch
    // ------------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        int idInt;
        BranchRestInfoFull branchRestInfo = null;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            try {
                branchRestInfo = createBranchRestInfo(idInt);
            }
            catch (Exception exception) {
                return RestUtilities.getResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_READ_FAILED, "Read Skills failed", exception.getLocalizedMessage());
            }

            return new BranchRepresentation(variant.getMediaType(), branchRestInfo);
        }


        // if not single, process request for all
        List<Branch> branches = m_branchManager.getBranches();
        List<BranchRestInfoFull> branchesRestInfo = new ArrayList<BranchRestInfoFull>();
        MetadataRestInfo metadataRestInfo;

        // sort if specified
        sortBranches(branches);

        // set requested agents groups and get resulting metadata
        metadataRestInfo = addBranches(branchesRestInfo, branches);

        // create final restinfo
        BranchesBundleRestInfo branchesBundleRestInfo = new BranchesBundleRestInfo(branchesRestInfo, metadataRestInfo);

        return new BranchesRepresentation(variant.getMediaType(), branchesBundleRestInfo);
    }


    // PUT - Update or Add single Branch
    // ---------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        BranchRepresentation representation = new BranchRepresentation(entity);
        BranchRestInfoFull branchRestInfo = representation.getObject();
        Branch branch = null;

        // validate input for update or create
        ValidationInfo validationInfo = validate(branchRestInfo);

        if (!validationInfo.valid) {
            RestUtilities.setResponseError(getResponse(), validationInfo.responseCode, validationInfo.message);
            return;
        }


        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = RestUtilities.getIntFromAttribute(idString);
                branch = m_branchManager.getBranch(idInt);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing
            try {
                updateBranch(branch, branchRestInfo);
                m_branchManager.saveBranch(branch);
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Branch failed", exception.getLocalizedMessage());
                return;
            }

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_UPDATED, "Updated Branch", branch.getId());

            return;
        }


        // otherwise add new
        try {
            branch = createBranch(branchRestInfo);
            m_branchManager.saveBranch(branch);
        }
        catch (Exception exception) {
            RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Branch failed", exception.getLocalizedMessage());
            return;
        }

        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_CREATED, "Created Branch", branch.getId());
    }


    // DELETE - Delete single Branch
    // -----------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        Branch branch;
        int idInt;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                idInt = RestUtilities.getIntFromAttribute(idString);
                branch = m_branchManager.getBranch(idInt); // just obtain to make sure exists, use int id for actual delete
            }
            catch (Exception exception) {
                RestUtilities.setResponseError(getResponse(), RestUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            List<Integer> branchIds = new ArrayList<Integer>();
            branchIds.add(idInt);
            m_branchManager.deleteBranches(branchIds);

            RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.SUCCESS_DELETED, "Deleted Branch", branch.getId());

            return;
        }

        // no id string
        RestUtilities.setResponse(getResponse(), RestUtilities.ResponseCode.ERROR_MISSING_INPUT, "ID value missing");
    }

    // Helper functions
    // ----------------

    // basic interface level validation of data provided through REST interface for creation or
    // update
    // may also contain clean up of input data
    // may create another validation function if different rules needed for update v. create
    private ValidationInfo validate(BranchRestInfoFull restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        return validationInfo;
    }

    private BranchRestInfoFull createBranchRestInfo(int id) throws ResourceException {
        BranchRestInfoFull branchRestInfo = null;

        Branch branch = m_branchManager.getBranch(id);
        branchRestInfo = new BranchRestInfoFull(branch);

        return branchRestInfo;
    }

    private MetadataRestInfo addBranches(List<BranchRestInfoFull> branchesRestInfo, List<Branch> branches) {
        BranchRestInfoFull branchRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = RestUtilities.calculatePagination(m_form, branches.size());

        // create list of skill restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            Branch branch = branches.get(index);

            branchRestInfo = new BranchRestInfoFull(branch);
            branchesRestInfo.add(branchRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortBranches(List<Branch> branches) {
        // sort if requested
        SortInfo sortInfo = RestUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(branches, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Branch branch1 = (Branch) object1;
                        Branch branch2 = (Branch) object2;
                        return branch1.getName().compareToIgnoreCase(branch2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(branches, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Branch branch1 = (Branch) object1;
                        Branch branch2 = (Branch) object2;
                        return branch1.getDescription().compareToIgnoreCase(branch2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(branches, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Branch branch1 = (Branch) object1;
                        Branch branch2 = (Branch) object2;
                        return branch2.getName().compareToIgnoreCase(branch1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(branches, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        Branch branch1 = (Branch) object1;
                        Branch branch2 = (Branch) object2;
                        return branch2.getDescription().compareToIgnoreCase(branch1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateBranch(Branch branch, BranchRestInfoFull branchRestInfo) {
        Address address;
        String tempString;

        // do not allow empty name
        tempString = branchRestInfo.getName();
        if (!tempString.isEmpty()) {
            branch.setName(tempString);
        }

        branch.setDescription(branchRestInfo.getDescription());
        branch.setPhoneNumber(branchRestInfo.getPhoneNumber());
        branch.setFaxNumber(branchRestInfo.getFaxNumber());

        address = getAddress(branchRestInfo);
        branch.setAddress(address);
    }

    private Branch createBranch(BranchRestInfoFull branchRestInfo) throws ResourceException {
        Address address;
        Branch branch = new Branch();

        // copy fields from rest info
        branch.setName(branchRestInfo.getName());
        branch.setDescription(branchRestInfo.getDescription());
        branch.setPhoneNumber(branchRestInfo.getPhoneNumber());
        branch.setFaxNumber(branchRestInfo.getFaxNumber());

        address = getAddress(branchRestInfo);
        branch.setAddress(address);

        return branch;
    }

    private Address getAddress(BranchRestInfoFull branchRestInfo) {
        Address address = new Address();

        address.setCity(branchRestInfo.getAddress().getCity());
        address.setCountry(branchRestInfo.getAddress().getCountry());
        address.setOfficeDesignation(branchRestInfo.getAddress().getOfficeDesignation());
        address.setState(branchRestInfo.getAddress().getState());
        address.setStreet(branchRestInfo.getAddress().getStreet());
        address.setZip(branchRestInfo.getAddress().getZip());

        return address;
    }


    // REST Representations
    // --------------------

    static class BranchesRepresentation extends XStreamRepresentation<BranchesBundleRestInfo> {

        public BranchesRepresentation(MediaType mediaType, BranchesBundleRestInfo object) {
            super(mediaType, object);
        }

        public BranchesRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("branch", BranchesBundleRestInfo.class);
            xstream.alias("branch", BranchRestInfoFull.class);
        }
    }

    static class BranchRepresentation extends XStreamRepresentation<BranchRestInfoFull> {

        public BranchRepresentation(MediaType mediaType, BranchRestInfoFull object) {
            super(mediaType, object);
        }

        public BranchRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("branch", BranchRestInfoFull.class);
        }
    }


    // REST info objects
    // -----------------

    static class BranchesBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<BranchRestInfoFull> m_branches;

        public BranchesBundleRestInfo(List<BranchRestInfoFull> branches, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_branches = branches;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<BranchRestInfoFull> getBranches() {
            return m_branches;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setBranchManager(BranchManager branchManager) {
        m_branchManager = branchManager;
    }

}
