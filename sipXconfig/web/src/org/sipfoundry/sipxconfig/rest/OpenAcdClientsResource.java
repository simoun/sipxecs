/*
 *
 *  OpenAcdClientsResource.java - A Restlet to read Skill data from OpenACD within SipXecs
 *  Copyright (C) 2012 PATLive, I. Wesson, D. Chang
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
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.openacd.OpenAcdClient;
import org.sipfoundry.sipxconfig.openacd.OpenAcdContext;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.MetadataRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.OpenAcdClientRestInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.PaginationInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.ResponseCode;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.SortInfo;
import org.sipfoundry.sipxconfig.rest.OpenAcdUtilities.ValidationInfo;
import org.springframework.beans.factory.annotation.Required;

import com.thoughtworks.xstream.XStream;

public class OpenAcdClientsResource extends UserResource {

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

    // GET - Retrieve all and single Client
    // -----------------------------------

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        // process request for single
        OpenAcdClientRestInfo clientRestInfo;
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                clientRestInfo = createClientRestInfo(idInt);
            }
            catch (Exception exception) {
                return OpenAcdUtilities.getResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
            }

            // return representation
            return new OpenAcdClientRepresentation(variant.getMediaType(), clientRestInfo);
        }


        // if not single, process request for list
        List<OpenAcdClient> clients = m_openAcdContext.getClients();
        List<OpenAcdClientRestInfo> clientsRestInfo = new ArrayList<OpenAcdClientRestInfo>();
        MetadataRestInfo metadataRestInfo;

        // sort groups if specified
        sortClients(clients);

        // set requested records and get resulting metadata
        metadataRestInfo = addClients(clientsRestInfo, clients);

        // create final restinfo
        OpenAcdClientsBundleRestInfo clientsBundleRestInfo = new OpenAcdClientsBundleRestInfo(clientsRestInfo, metadataRestInfo);

        return new OpenAcdClientsRepresentation(variant.getMediaType(), clientsBundleRestInfo);
    }


    // PUT - Update or Add single Skill
    // --------------------------------

    @Override
    public void storeRepresentation(Representation entity) throws ResourceException {
        // get from request body
        OpenAcdClientRepresentation representation = new OpenAcdClientRepresentation(entity);
        OpenAcdClientRestInfo clientRestInfo = representation.getObject();
        OpenAcdClient client;

        // validate input for update or create
        ValidationInfo validationInfo = validate(clientRestInfo);

        if (!validationInfo.valid) {
            OpenAcdUtilities.setResponseError(getResponse(), validationInfo.responseCode, validationInfo.message);
            return;
        }


        // if have id then update single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                client = m_openAcdContext.getClientById(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            // copy values over to existing
            try {
                updateClient(client, clientRestInfo);
                m_openAcdContext.saveClient(client);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Update Client failed", exception.getLocalizedMessage());
                return;
            }

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_UPDATED, "Updated Client", client.getId());

            return;
        }


        // otherwise add new
        try {
            client = createClient(clientRestInfo);
            m_openAcdContext.saveClient(client);
        }
        catch (Exception exception) {
            OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_WRITE_FAILED, "Create Client failed", exception.getLocalizedMessage());
            return;
        }

        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_CREATED, "Created Client", client.getId());
    }


    // DELETE - Delete single Skill
    // ----------------------------

    @Override
    public void removeRepresentations() throws ResourceException {
        OpenAcdClient client;

        // get id then delete single
        String idString = (String) getRequest().getAttributes().get("id");

        if (idString != null) {
            try {
                int idInt = OpenAcdUtilities.getIntFromAttribute(idString);
                client = m_openAcdContext.getClientById(idInt);
            }
            catch (Exception exception) {
                OpenAcdUtilities.setResponseError(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_BAD_INPUT, "ID " + idString + " not found.");
                return;
            }

            m_openAcdContext.deleteClient(client);

            OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.SUCCESS_DELETED, "Deleted Client", client.getId());

            return;
        }

        // no id string
        OpenAcdUtilities.setResponse(getResponse(), OpenAcdUtilities.ResponseCode.ERROR_MISSING_INPUT, "ID value missing");
    }


    // Helper functions
    // ----------------

    // basic interface level validation of data provided through REST interface for creation or
    // update
    // may also contain clean up of input data
    // may create another validation function if different rules needed for update v. create
    private ValidationInfo validate(OpenAcdClientRestInfo restInfo) {
        ValidationInfo validationInfo = new ValidationInfo();

        String identity = restInfo.getIdentity();

        for (int i = 0; i < identity.length(); i++) {
            if ((!Character.isLetterOrDigit(identity.charAt(i)) && !(Character.getType(identity.charAt(i)) == Character.CONNECTOR_PUNCTUATION)) && identity.charAt(i) != '-') {
                validationInfo.valid = false;
                validationInfo.message = "Validation Error: 'Identity' must only contain letters, numbers, dashes, and underscores";
                validationInfo.responseCode = ResponseCode.ERROR_BAD_INPUT;
            }
        }

        return validationInfo;
    }

    private OpenAcdClientRestInfo createClientRestInfo(int id) throws ResourceException {
        OpenAcdClientRestInfo clientRestInfo;

        try {
            OpenAcdClient client = m_openAcdContext.getClientById(id);
            clientRestInfo = new OpenAcdClientRestInfo(client);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "ID " + id + " not found.");
        }

        return clientRestInfo;
    }

    private MetadataRestInfo addClients(List<OpenAcdClientRestInfo> clientsRestInfo, List<OpenAcdClient> clients) {
        OpenAcdClientRestInfo clientRestInfo;

        // determine pagination
        PaginationInfo paginationInfo = OpenAcdUtilities.calculatePagination(m_form, clients.size());

        // create list of client restinfos
        for (int index = paginationInfo.startIndex; index <= paginationInfo.endIndex; index++) {
            OpenAcdClient client = clients.get(index);

            clientRestInfo = new OpenAcdClientRestInfo(client);
            clientsRestInfo.add(clientRestInfo);
        }

        // create metadata about agent groups
        MetadataRestInfo metadata = new MetadataRestInfo(paginationInfo);
        return metadata;
    }

    private void sortClients(List<OpenAcdClient> clients) {
        // sort groups if requested
        SortInfo sortInfo = OpenAcdUtilities.calculateSorting(m_form);

        if (!sortInfo.sort) {
            return;
        }

        SortField sortField = SortField.toSortField(sortInfo.sortField);

        if (sortInfo.directionForward) {

            switch (sortField) {
            case NAME:
                Collections.sort(clients, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdClient client1 = (OpenAcdClient) object1;
                        OpenAcdClient client2 = (OpenAcdClient) object2;
                        return client1.getName().compareToIgnoreCase(client2.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(clients, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdClient client1 = (OpenAcdClient) object1;
                        OpenAcdClient client2 = (OpenAcdClient) object2;
                        return client1.getDescription().compareToIgnoreCase(client2.getDescription());
                    }

                });
                break;
            }
        }
        else {
            // must be reverse
            switch (sortField) {
            case NAME:
                Collections.sort(clients, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdClient client1 = (OpenAcdClient) object1;
                        OpenAcdClient client2 = (OpenAcdClient) object2;
                        return client2.getName().compareToIgnoreCase(client1.getName());
                    }

                });
                break;

            case DESCRIPTION:
                Collections.sort(clients, new Comparator() {

                    public int compare(Object object1, Object object2) {
                        OpenAcdClient client1 = (OpenAcdClient) object1;
                        OpenAcdClient client2 = (OpenAcdClient) object2;
                        return client2.getDescription().compareToIgnoreCase(client1.getDescription());
                    }

                });
                break;
            }
        }
    }

    private void updateClient(OpenAcdClient client, OpenAcdClientRestInfo clientRestInfo) throws ResourceException {
        String tempString;

        // do not allow empty name
        tempString = clientRestInfo.getName();
        if (!tempString.isEmpty()) {
            client.setName(tempString);
        }

        client.setDescription(clientRestInfo.getDescription());
    }

    private OpenAcdClient createClient(OpenAcdClientRestInfo clientRestInfo) throws ResourceException {
        OpenAcdClient client = new OpenAcdClient();

        // copy fields from rest info
        client.setName(clientRestInfo.getName());
        client.setDescription(clientRestInfo.getDescription());
        client.setIdentity(clientRestInfo.getIdentity());

        return client;
    }


    // REST Representations
    // --------------------

    static class OpenAcdClientsRepresentation extends XStreamRepresentation<OpenAcdClientsBundleRestInfo> {

        public OpenAcdClientsRepresentation(MediaType mediaType, OpenAcdClientsBundleRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdClientsRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("openacd-client", OpenAcdClientsBundleRestInfo.class);
            xstream.alias("client", OpenAcdClientRestInfo.class);
        }
    }

    static class OpenAcdClientRepresentation extends XStreamRepresentation<OpenAcdClientRestInfo> {

        public OpenAcdClientRepresentation(MediaType mediaType, OpenAcdClientRestInfo object) {
            super(mediaType, object);
        }

        public OpenAcdClientRepresentation(Representation representation) {
            super(representation);
        }

        @Override
        protected void configureXStream(XStream xstream) {
            xstream.alias("client", OpenAcdClientRestInfo.class);
        }
    }


    // REST info objects
    // -----------------

    static class OpenAcdClientsBundleRestInfo {
        private final MetadataRestInfo m_metadata;
        private final List<OpenAcdClientRestInfo> m_clients;

        public OpenAcdClientsBundleRestInfo(List<OpenAcdClientRestInfo> clients, MetadataRestInfo metadata) {
            m_metadata = metadata;
            m_clients = clients;
        }

        public MetadataRestInfo getMetadata() {
            return m_metadata;
        }

        public List<OpenAcdClientRestInfo> getClients() {
            return m_clients;
        }
    }


    // Injected objects
    // ----------------

    @Required
    public void setOpenAcdContext(OpenAcdContext openAcdContext) {
        m_openAcdContext = openAcdContext;
    }

}
