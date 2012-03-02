/*
 *
 *
 * Copyright (C) 2011 eZuce Inc., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the AGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.vm;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.sipfoundry.sipxconfig.address.Address;
import org.sipfoundry.sipxconfig.address.AddressManager;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.common.event.DaoEventListener;
import org.sipfoundry.sipxconfig.commserver.LocationsManager;
import org.sipfoundry.sipxconfig.feature.FeatureManager;
import org.sipfoundry.sipxconfig.ivr.Ivr;
import org.sipfoundry.sipxconfig.permission.PermissionName;
import org.sipfoundry.sipxconfig.vm.attendant.PersonalAttendantManager;

public abstract class AbstractMailboxManager extends PersonalAttendantManager implements DaoEventListener {
    protected static final String PATH_MAILBOX = "/mailbox/";
    protected static final String PATH_MESSAGE = "/message/";
    private File m_stdpromptDirectory;
    private CoreContext m_coreContext;
    private LocationsManager m_locationsManager;
    private AddressManager m_addressManager;
    private String m_binDir;
    private FeatureManager m_featureManager;

    public boolean isSystemCpui() {
        return m_featureManager.isFeatureEnabled(Ivr.CALLPILOT);
    }

    public String getStdpromptDirectory() {
        if (m_stdpromptDirectory != null) {
            return m_stdpromptDirectory.getPath();
        }
        return null;
    }

    public String getMediaFileURL(String userId, String folder, String messageId) {
        String url = "https://%s:%s/media/%s/%s/%s";
        Address ivrAddress = m_addressManager.getSingleAddress(Ivr.REST_API);
        if (ivrAddress != null) {
            return String.format(url, ivrAddress.getAddress(), ivrAddress.getPort(), userId, folder, messageId);
        }
        return null;
    }

    public List<String> getFolderIds() {
        // to support custom folders, return these names and any additional
        // directories here
        return Arrays.asList(new String[] {
            "inbox", "conference", "deleted", "saved"
        });
    }

    @Override
    public void onDelete(Object entity) {
        if (entity instanceof User) {
            User user = (User) entity;
            removePersonalAttendantForUser(user);
            if (m_featureManager.isFeatureEnabled(Ivr.FEATURE)) {
                deleteMailbox(user.getUserName());
            }
        }
    }

    @Override
    public void onSave(Object entity) {
    }

    public void setStdpromptDirectory(String stdpromptDirectory) {
        m_stdpromptDirectory = new File(stdpromptDirectory);
    }

    public DistributionList[] loadDistributionLists(User user) {
        DistributionList[] lists = new DistributionList[DistributionList.MAX_SIZE];
        //0 is not available
        for (int i = 1; i < lists.length; i++) {
            DistributionList dl = new DistributionList();
            dl.setExtensions(StringUtils.split(StringUtils.defaultIfEmpty(user.getSettingValue(
                    new StringBuilder(DistributionList.SETTING_PATH_DISTRIBUTION_LIST).append(i).toString()), "")));
            lists[i] = dl;
        }

        return lists;
    }


    public void saveDistributionLists(User user, DistributionList[] lists) {
        Collection<String> aliases = DistributionList.getUniqueExtensions(lists);
        getCoreContext().checkForValidExtensions(aliases, PermissionName.VOICEMAIL);
        for (int i = 1; i < lists.length; i++) {
            if (lists[i].getExtensions() != null) {
                user.setSettingValue(new StringBuilder(DistributionList.SETTING_PATH_DISTRIBUTION_LIST).
                        append(i).toString(), joinBySpace(lists[i].getExtensions()));
            }
        }
        getCoreContext().saveUser(user);
    }

    public String joinBySpace(String[] array) {
        String s = null;
        if (array != null) {
            s = StringUtils.join(array, ' ');
        }
        return s;
    }

    public void setCoreContext(CoreContext coreContext) {
        m_coreContext = coreContext;
    }

    protected CoreContext getCoreContext() {
        return m_coreContext;
    }

    public void setLocationsManager(LocationsManager locationsManager) {
        m_locationsManager = locationsManager;
    }

    protected LocationsManager getLocationsManager() {
        return m_locationsManager;
    }

    public void setBinDir(String binDir) {
        m_binDir = binDir;
    }

    protected String getBinDir() {
        return m_binDir;
    }

    protected String getMailboxServerUrl() {
        Address ivrAddress = m_addressManager.getSingleAddress(Ivr.REST_API);
        if (ivrAddress != null) {
            return String.format("https://%s:%s", ivrAddress.getAddress(), ivrAddress.getPort());
        }
        return null;
    }

    public void setFeatureManager(FeatureManager featureManager) {
        m_featureManager = featureManager;
    }

    public abstract void deleteMailbox(String userId);

    public void setAddressManager(AddressManager addressManager) {
        m_addressManager = addressManager;
    }

    public AddressManager getAddressManager() {
        return m_addressManager;
    }
}