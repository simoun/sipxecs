/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxconfig.phonebook;

import org.sipfoundry.sipxconfig.common.BeanWithId;

public class AddressBookEntry extends BeanWithId {

    private String m_jobTitle;
    private String m_jobDept;
    private String m_companyName;
    private String m_assistantName;
    private String m_location;

    private Address m_homeAddress;
    private Address m_officeAddress;
    private Address m_branchAddress;
    private String m_cellPhoneNumber;
    private String m_homePhoneNumber;
    private String m_assistantPhoneNumber;
    private String m_faxNumber;
    private String m_didNumber;
    private String m_imId;
    private String m_imPassword;
    private String m_imDisplayName;
    private String m_alternateImId;
    private String m_emailAddress;
    private String m_alternateEmailAddress;
    private boolean m_useBranchAddress;

    private String m_avatar = Gravatar.DEFAULT_AVATAR;

    public String getJobTitle() {
        return m_jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        m_jobTitle = jobTitle;
    }

    public String getJobDept() {
        return m_jobDept;
    }

    public void setJobDept(String jobDept) {
        m_jobDept = jobDept;
    }

    public String getCompanyName() {
        return m_companyName;
    }

    public void setCompanyName(String companyName) {
        m_companyName = companyName;
    }

    public String getAssistantName() {
        return m_assistantName;
    }

    public void setAssistantName(String assistantName) {
        m_assistantName = assistantName;
    }

    public Address getHomeAddress() {
        return m_homeAddress;
    }

    public void setHomeAddress(Address homeAddress) {
        m_homeAddress = homeAddress;
    }

    public Address getOfficeAddress() {
        if (m_useBranchAddress && null != m_branchAddress) {
            if (null == m_officeAddress) {
                m_officeAddress = new Address();
            }
            m_officeAddress.update(m_branchAddress);
        }
        return m_officeAddress;
    }

    public Address getBranchAddress() {
        return m_branchAddress;
    }

    public void setOfficeAddress(Address officeAddress) {
        if (null != m_officeAddress) {
            m_officeAddress.update(officeAddress);
        } else {
            m_officeAddress = officeAddress;
        }
    }

    public void setBranchAddress(Address branchAddress) {
        m_branchAddress = branchAddress;
    }

    public String getCellPhoneNumber() {
        return m_cellPhoneNumber;
    }

    public void setCellPhoneNumber(String cellPhoneNumber) {
        m_cellPhoneNumber = cellPhoneNumber;
    }

    public String getHomePhoneNumber() {
        return m_homePhoneNumber;
    }

    public void setHomePhoneNumber(String homePhoneNumber) {
        m_homePhoneNumber = homePhoneNumber;
    }

    public String getAssistantPhoneNumber() {
        return m_assistantPhoneNumber;
    }

    public void setAssistantPhoneNumber(String assistantPhoneNumber) {
        m_assistantPhoneNumber = assistantPhoneNumber;
    }

    public String getFaxNumber() {
        return m_faxNumber;
    }

    public void setFaxNumber(String faxNumber) {
        m_faxNumber = faxNumber;
    }

    public String getImId() {
        return m_imId;
    }

    public void setImId(String imId) {
        m_imId = imId;
    }


    public String getImPassword() {
        return m_imPassword;
    }

    public void setImPassword(String imPassword) {
        m_imPassword = imPassword;
    }

    public String getImDisplayName() {
        return m_imDisplayName;
    }

    public void setImDisplayName(String imDisplayName) {
        m_imDisplayName = imDisplayName;
    }

    public String getAlternateImId() {
        return m_alternateImId;
    }

    public void setAlternateImId(String alternateImId) {
        m_alternateImId = alternateImId;
    }

    public String getLocation() {
        return m_location;
    }

    public void setLocation(String location) {
        m_location = location;
    }

    public boolean getUseBranchAddress() {
        return m_useBranchAddress;
    }

    public void setUseBranchAddress(boolean useBranchAddress) {
        m_useBranchAddress = useBranchAddress;
    }

    public String getEmailAddress() {
        return m_emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        m_emailAddress = emailAddress;
        if (emailAddress != null && !emailAddress.isEmpty()) {
            Gravatar gravatar = new Gravatar(emailAddress);
            m_avatar = gravatar.getUrl();
        }
    }

    public String getAvatar() {
        return m_avatar;
    }

    public String getAlternateEmailAddress() {
        return m_alternateEmailAddress;
    }

    public void setAlternateEmailAddress(String alternateEmailAddress) {
        m_alternateEmailAddress = alternateEmailAddress;
    }

    public String getDidNumber() {
        return m_didNumber;
    }

    public void setDidNumber(String didNumber) {
        m_didNumber = didNumber;
    }
}
