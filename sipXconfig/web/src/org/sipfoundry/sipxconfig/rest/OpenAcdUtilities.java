/*
 *
 *  OpenAcdUtilities.java - Support functionality for OpenAcd Restlets
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.data.Form;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.ResourceException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OpenAcdUtilities {

    public static int getIntFromAttribute(String attributeString) throws ResourceException {
        int intFromAttribute;

        // attempt to parse attribute provided as an id
        try {
            intFromAttribute = Integer.parseInt(attributeString);
        }
        catch (Exception exception) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Attribute " + attributeString + " invalid.");
        }

        return intFromAttribute;
    }

    public static PaginationInfo calculatePagination(Form form, int totalResults) {
        PaginationInfo paginationInfo = new PaginationInfo();
        paginationInfo.totalResults = totalResults;

        // must specify both PageNumber and ResultsPerPage together
        String pageNumberString = form.getFirstValue("page");
        String resultsPerPageString = form.getFirstValue("pagesize");

        // attempt to parse pagination values from request
        try {
            paginationInfo.pageNumber = Integer.parseInt(pageNumberString);
            paginationInfo.resultsPerPage = Integer.parseInt(resultsPerPageString);
        }
        catch (Exception exception) {
            // default 0 for nothing
            paginationInfo.pageNumber = 0;
            paginationInfo.resultsPerPage = 0;
        }

        // check for outrageous values or lack of parameters
        if ((paginationInfo.pageNumber < 1) || (paginationInfo.resultsPerPage < 1)) {
            paginationInfo.pageNumber = 0;
            paginationInfo.resultsPerPage = 0;
            paginationInfo.paginate = false;
        }
        else {
            paginationInfo.paginate = true;
        }


        // do we have to paginate?
        if (paginationInfo.paginate) {
            paginationInfo.totalPages = ((paginationInfo.totalResults - 1) / paginationInfo.resultsPerPage) + 1;

            // check if only one page
            //if (resultsPerPage >= totalResults) {
            if (paginationInfo.totalPages == 1) {
                paginationInfo.startIndex = 0;
                paginationInfo.endIndex = paginationInfo.totalResults - 1;
                paginationInfo.pageNumber = 1;
                // design decision: should the resultsPerPage actually be set to totalResults?
                // since totalResults are already available preserve call value
            }
            else {
                // check if specified page number is on or beyoned last page (then use last page)
                if (paginationInfo.pageNumber >= paginationInfo.totalPages) {
                    paginationInfo.pageNumber = paginationInfo.totalPages;
                    paginationInfo.startIndex = (paginationInfo.totalPages - 1) * paginationInfo.resultsPerPage;
                    paginationInfo.endIndex = paginationInfo.totalResults - 1;
                }
                else {
                    paginationInfo.startIndex = (paginationInfo.pageNumber - 1) * paginationInfo.resultsPerPage;
                    paginationInfo.endIndex = paginationInfo.startIndex + paginationInfo.resultsPerPage - 1;
                }
            }
        }
        else {
            // default values assuming no pagination
            paginationInfo.startIndex = 0;
            paginationInfo.endIndex = paginationInfo.totalResults - 1;
            paginationInfo.pageNumber = 1;
            paginationInfo.totalPages = 1;
            paginationInfo.resultsPerPage = paginationInfo.totalResults;
        }

        return paginationInfo;
    }

    public static SortInfo calculateSorting(Form form) {
        SortInfo sortInfo = new SortInfo();

        String sortDirectionString = form.getFirstValue("sortdir");
        String sortFieldString = form.getFirstValue("sortby");

        // check for invalid input
        if ((sortDirectionString == null) || (sortFieldString == null)) {
            sortInfo.sort = false;
            return sortInfo;
        }

        if ((sortDirectionString.isEmpty()) || (sortFieldString.isEmpty())) {
            sortInfo.sort = false;
            return sortInfo;
        }

        sortInfo.sort = true;

        // assume forward if get anything else but "reverse"
        if (sortDirectionString.toLowerCase().equals("reverse")) {
            sortInfo.directionForward = false;
        }
        else {
            sortInfo.directionForward = true;
        }

        // tough to type-check this one
        sortInfo.sortField = sortFieldString;

        return sortInfo;
    }

    public static void setResponse(Response response, ResponseCode code, String message) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, code);

            // create root node
            Element elementResponse = doc.createElement("response");
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, code, message);
            
            // no data related to result (create function overloads to modify)

            response.setEntity(new DomRepresentation(MediaType.TEXT_XML, doc));

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void setResponse(Response response, ResponseCode code, int id, String message) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, code);

            // create root node
            Element elementResponse = doc.createElement("response");
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, code, message);
            
            // add data related to result
            Element elementData = doc.createElement("data");
            Element elementId = doc.createElement("id");
            elementId.appendChild(doc.createTextNode(String.valueOf(id)));
            elementData.appendChild(elementId);
            elementResponse.appendChild(elementData);

            response.setEntity(new DomRepresentation(MediaType.TEXT_XML, doc));

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void setResponseError(Response response, ResponseCode code, String message) {
        Representation representation = getResponseError(response, code, message);

        response.setEntity(representation);
    }
    
    public static Representation getResponseError(Response response, ResponseCode code, String message) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, code);

            // create root node
            Element elementResponse = doc.createElement("response");
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, code, message);
            
            return representation; //new DomRepresentation(MediaType.TEXT_XML, doc);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return null;
    }

    public static Representation getResponseException(Response response, Exception exception) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, ResponseCode.EXCEPTION);

            // create root node
            Element elementResponse = doc.createElement("response");
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, ResponseCode.EXCEPTION, "An Exception occurred");

            // add data related to result
            Element elementData = doc.createElement("data");
            Element elementId = doc.createElement("exception");

            // convert exception stack trace output to string
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(outputStream);
            exception.printStackTrace(printStream);
            
            elementId.appendChild(doc.createTextNode(outputStream.toString()));
            elementData.appendChild(elementId);
            elementResponse.appendChild(elementData);
            
            return representation;

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return null;
    }
        
    private static void setResponseStatus(Response response, ResponseCode code) {
        // set response status based on code
        switch (code) {
        case CREATED:
            response.setStatus(Status.SUCCESS_CREATED);
            break;

        case MISSING_INPUT:
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            break;
            
        case BAD_INPUT:
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            break;
            
        case EXCEPTION:
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
            break;
            
        default:
            response.setStatus(Status.SUCCESS_OK);
        }
    }

    private static void setResponseHeader(Document doc, Element elementResponse, ResponseCode code, String message) {

        // add standard elements
        Element elementCode = doc.createElement("code");
        elementCode.appendChild(doc.createTextNode(code.toString()));
        elementResponse.appendChild(elementCode);

        Element elementMessage = doc.createElement("message");
        elementMessage.appendChild(doc.createTextNode(message));
        elementResponse.appendChild(elementMessage);        
    }
    
    // Data objects
    // ------------

    public static class PaginationInfo {
        Boolean paginate = false;
        int pageNumber = 0;
        int resultsPerPage = 0;
        int totalPages = 0;
        int totalResults = 0;
        int startIndex = 0;
        int endIndex = 0;
    }

    public static class SortInfo {
        Boolean sort = false;
        Boolean directionForward = true;
        String sortField = "";
    }

    public static enum ResponseCode {
        CREATED, UPDATED, DELETED, MISSING_INPUT, BAD_INPUT, EXCEPTION
    }
}
