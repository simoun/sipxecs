/*
 *
l *  OpenAcdUtilities.java - Support functionality for OpenAcd Restlets
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

import java.io.IOException;
import java.util.List;

import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.sipfoundry.sipxconfig.branch.Branch;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.permission.Permission;
import org.sipfoundry.sipxconfig.phonebook.Address;
import org.sipfoundry.sipxconfig.setting.Group;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class RestUtilities {

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
            // if (resultsPerPage >= totalResults) {
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


    // XML Response functions
    // ----------------------

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

            // no related data (create function overloads to modify)

            response.setEntity(new DomRepresentation(MediaType.TEXT_XML, doc));

        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void setResponse(Response response, ResponseCode code, String message, int id) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, code);

            // create root node
            Element elementResponse = doc.createElement("response");
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, code, message);

            // add related data
            Element elementData = doc.createElement("data");
            Element elementId = doc.createElement("id");
            elementId.appendChild(doc.createTextNode(String.valueOf(id)));
            elementData.appendChild(elementId);
            elementResponse.appendChild(elementData);

            response.setEntity(new DomRepresentation(MediaType.TEXT_XML, doc));

        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void setResponse(Response response, ResponseCode code, String message, String id) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, code);

            // create root node
            Element elementResponse = doc.createElement("response");
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, code, message);

            // add related data
            Element elementData = doc.createElement("data");
            Element elementId = doc.createElement("id");
            elementId.appendChild(doc.createTextNode(id));
            elementData.appendChild(elementId);
            elementResponse.appendChild(elementData);

            response.setEntity(new DomRepresentation(MediaType.TEXT_XML, doc));

        }
        catch (IOException e) {
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

            return representation; // new DomRepresentation(MediaType.TEXT_XML, doc);

        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    public static void setResponseError(Response response, ResponseCode code, String message, String additionalMessage) {
        Representation representation = getResponseError(response, code, message, additionalMessage);

        response.setEntity(representation);
    }

    public static Representation getResponseError(Response response, ResponseCode code, String message, String additionalMessage) {
        try {
            DomRepresentation representation = new DomRepresentation(MediaType.TEXT_XML);
            Document doc = representation.getDocument();

            // set response status
            setResponseStatus(response, code);

            // create root node
            Element elementResponse = doc.createElement("response");
            doc.appendChild(elementResponse);

            setResponseHeader(doc, elementResponse, code, message);

            // add related data
            Element elementData = doc.createElement("data");
            Element elementId = doc.createElement("additionalMessage");
            elementId.appendChild(doc.createTextNode(additionalMessage));
            elementData.appendChild(elementId);
            elementResponse.appendChild(elementData);

            return representation; // new DomRepresentation(MediaType.TEXT_XML, doc);

        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
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

    private static void setResponseStatus(Response response, ResponseCode code) {
        // set response status based on code
        switch (code) {
        case SUCCESS_CREATED:
            response.setStatus(Status.SUCCESS_CREATED);
            break;

        case ERROR_MISSING_INPUT:
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            break;

        case ERROR_BAD_INPUT:
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            break;

        case ERROR_WRITE_FAILED:
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            break;

        case ERROR_READ_FAILED:
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
            break;

        default:
            response.setStatus(Status.SUCCESS_OK);
        }
    }

    public static enum ResponseCode {
        SUCCESS, SUCCESS_CREATED, SUCCESS_UPDATED, SUCCESS_DELETED, ERROR_MISSING_INPUT, ERROR_BAD_INPUT, ERROR_WRITE_FAILED, ERROR_READ_FAILED
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

    public static class ValidationInfo {
        Boolean valid = true;
        String message = "Valid";
        ResponseCode responseCode = ResponseCode.SUCCESS;
    }


    // Common Rest Info objects
    // ------------------------

    static class PermissionRestInfoFull {
        private final String m_name;
        private final String m_label;
        private final String m_description;
        private final boolean m_defaultValue;
        private final Permission.Type m_type;
        private final boolean m_builtIn;

        public PermissionRestInfoFull(Permission permission) {
            m_name = permission.getName();
            m_label = permission.getLabel();
            m_description = permission.getDescription();
            m_defaultValue = permission.getDefaultValue();
            m_type = permission.getType();
            m_builtIn = permission.isBuiltIn();
        }

        public String getName() {
            return m_name;
        }

        public String getLabel() {
            return m_label;
        }

        public String getDescription() {
            return m_description;
        }

        public boolean getDefaultValue() {
            return m_defaultValue;
        }

        public Permission.Type getType() {
            return m_type;
        }

        public boolean getBuiltIn() {
            return m_builtIn;
        }
    }

    static class BranchRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;

        public BranchRestInfo(Branch branch) {
            m_id = branch.getId();
            m_name = branch.getName();
            m_description = branch.getDescription();
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

    static class BranchRestInfoFull extends BranchRestInfo {
        private final Address m_address;
        private final String m_phoneNumber;
        private final String m_faxNumber;

        public BranchRestInfoFull(Branch branch) {
            super(branch);

            m_address = branch.getAddress();
            m_phoneNumber = branch.getPhoneNumber();
            m_faxNumber = branch.getFaxNumber();
        }

        public Address getAddress() {
            return m_address;
        }

        public String getPhoneNumber() {
            return m_phoneNumber;
        }

        public String getFaxNumber() {
            return m_faxNumber;
        }
    }

    static class UserGroupRestInfo {
        private final int m_id;
        private final String m_name;
        private final String m_description;

        public UserGroupRestInfo(Group userGroup) {
            m_id = userGroup.getId();
            m_name = userGroup.getName();
            m_description = userGroup.getDescription();
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

    static class UserGroupRestInfoFull extends UserGroupRestInfo {
        private final BranchRestInfoFull m_branch;

        public UserGroupRestInfoFull(Group userGroup, BranchRestInfoFull branchRestInfo) {
            super(userGroup);

            m_branch = branchRestInfo;
        }

        public BranchRestInfoFull getBranch() {
            return m_branch;
        }
    }

    static class SettingBooleanRestInfo {
        private final String m_name;
        private final String m_value;
        private final boolean m_defaultValue;

        public SettingBooleanRestInfo(String name, String value, boolean defaultValue) {
            m_name = name;
            m_value = value;
            m_defaultValue = defaultValue;
        }

        public String getName() {
            return m_name;
        }

        public String getValue() {
            return m_value;
        }

        public boolean getDefaultValue() {
            return m_defaultValue;
        }
    }

    static class UserGroupPermissionRestInfoFull extends UserGroupRestInfo {
        private final List<SettingBooleanRestInfo> m_permissions;

        public UserGroupPermissionRestInfoFull(Group userGroup, List<SettingBooleanRestInfo> settingsRestInfo) {
            super(userGroup);

            m_permissions = settingsRestInfo;
        }

        public List<SettingBooleanRestInfo> getPermissions() {
            return m_permissions;
        }
    }

    static class UserRestInfoFull {
        private final int m_id;
        private final String m_userName; // also called "User ID" in gui
        private final String m_lastName;
        private final String m_firstName;
        private final String m_pin;
        private final String m_sipPassword;
        private final String m_emailAddress;  // this is actually from "Contact Information" tab.  Maybe create separate API later
        private final List<UserGroupRestInfo> m_groups;
        private final BranchRestInfo m_branch;
        private final List<AliasRestInfo> m_aliases;


        public UserRestInfoFull(User user, List<UserGroupRestInfo> userGroupsRestInfo, BranchRestInfo branchRestInfo, List<AliasRestInfo> aliasesRestInfo) {
            m_id = user.getId();
            m_userName = user.getUserName();
            m_lastName = user.getLastName();
            m_firstName = user.getFirstName();
            m_pin = ""; // pin is hardcoded to never display but must still be submitted
            m_sipPassword = user.getSipPassword();
            m_emailAddress = user.getEmailAddress();
            m_groups = userGroupsRestInfo;
            m_branch = branchRestInfo;
            m_aliases = aliasesRestInfo;
        }

        public int getId() {
            return m_id;
        }

        public String getUserName() {
            return m_userName;
        }

        public String getLastName() {
            return m_lastName;
        }

        public String getFirstName() {
            return m_firstName;
        }

        public String getPin() {
            return m_pin;
        }

        public String getSipPassword() {
            return m_sipPassword;
        }

        public String getEmailAddress() {
            return m_emailAddress;
        }
        public List<UserGroupRestInfo> getGroups() {
            return m_groups;
        }

        public BranchRestInfo getBranch() {
            return m_branch;
        }

        public List<AliasRestInfo> getAliases() {
            return m_aliases;
        }
    }

    static class AliasRestInfo {

        private final String m_alias;

        public AliasRestInfo(String alias) {
            m_alias = alias;
        }

        public String getAlias() {
            return m_alias;
        }
    }


    // Common OpenACD Rest Info objects
    // ------------------------

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

}
