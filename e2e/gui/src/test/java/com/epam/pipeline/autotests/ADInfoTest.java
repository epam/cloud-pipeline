/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.lang.String.format;

public class ADInfoTest extends AbstractSinglePipelineRunningTest implements Authorization {

    private static final String LDAP_DNS_NAME = "ldap";
    private static final String LDAP_PORT = "389";
    private static final String LDAP_URLS = "ldap.urls";
    private static final String LDAP_USERNAME = "ldap.username";
    private static final String LDAP_PASSWORD = "ldap.password";
    private static final String LDAP_BASE_PATH = "ldap.base.path";
    private static final String SYSTEM_USER_MONITOR_DELAY = "system.user.monitor.delay";
    private final String ldapServerTool = C.LDAP_SERVER_TEST_TOOL;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;

    private boolean userMonitor;
    private boolean ldapUserBlockMonitor;
    private String[] systemUserMonitorDelay;
    private String[] ldapUrls;
    private String[] ldapBasePath;
    private String[] ldapUsername;
    private String[] ldapPassword;

    @BeforeClass(alwaysRun = true)
    public void getDefaultPreferences() {
        loginAsAdminAndPerform(() -> {
            final SettingsPageAO.PreferencesAO preferencesAO = navigationMenu()
                    .settings()
                    .switchToPreferences();
            userMonitor = preferencesAO.switchToSystem().getUserMonitor();
            ldapUserBlockMonitor = preferencesAO.switchToSystem().getLdapUserBlockMonitor();
            systemUserMonitorDelay = preferencesAO.getLinePreference(SYSTEM_USER_MONITOR_DELAY);
            ldapUrls = preferencesAO.getLinePreference(LDAP_URLS);
            ldapBasePath = preferencesAO.getLinePreference(LDAP_BASE_PATH);
            ldapUsername = preferencesAO.getLinePreference(LDAP_USERNAME);
            ldapPassword = preferencesAO.getLinePreference(LDAP_PASSWORD);
        });
    }

    @AfterClass(alwaysRun = true)
    public void resetPreference() {
        if (!userMonitor) {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .switchToSystem()
                    .disableUserMonitor()
                    .saveIfNeeded();
        }
        if (!ldapUserBlockMonitor) {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .switchToSystem()
                    .disableLdapUserBlockMonitor()
                    .saveIfNeeded();
        }
        final SettingsPageAO.PreferencesAO preferencesAO = navigationMenu()
                .settings()
                .switchToPreferences();
        Stream.of(
                Pair.of(systemUserMonitorDelay, SYSTEM_USER_MONITOR_DELAY),
                Pair.of(ldapUrls, LDAP_URLS),
                Pair.of(ldapBasePath, LDAP_BASE_PATH),
                Pair.of(ldapUsername, LDAP_USERNAME),
                Pair.of(ldapPassword, LDAP_PASSWORD)
                )
                .filter(s -> s.getLeft() != null && s.getLeft()[0] != null)
                .forEach(preference ->
                        preferencesAO.updatePreference(preference.getRight(), preference.getLeft()[0], false)
                );
        final String userLogin = userWithoutCompletedRuns.login.toUpperCase();
        final SettingsPageAO.UserManagementAO.UsersTabAO.UserEntry userEntry = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(userLogin);
        if (userEntry.isBlockedUser(userLogin)) {
            userEntry
                    .edit()
                    .unblockUser(userLogin);
        }
    }

    @Test
    @TestCase({"2319"})
    public void checkAutomaticallyBlockingUsers() {
        tools()
                .perform(registry, group, ldapServerTool, ToolTab::runWithCustomSettings)
                .configureInternalDNSName(LDAP_DNS_NAME, LDAP_PORT)
                .launch(this);
        if (!userMonitor) {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .switchToSystem()
                    .enableUserMonitor()
                    .save();
        }
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updatePreference(SYSTEM_USER_MONITOR_DELAY, C.SYSTEM_MONITOR_DELAY, false)
                .updatePreference(LDAP_URLS, C.LDAP_URLS, false)
                .updatePreference(LDAP_BASE_PATH, C.LDAP_BASE_PATH, false)
                .updatePreference(LDAP_USERNAME, C.LDAP_USERNAME, false)
                .updatePreference(LDAP_PASSWORD, C.LDAP_PASSWORD, false);
        final SettingsPageAO.UserManagementAO.UsersTabAO usersTabAO = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers();
        usersTabAO
                .searchUserEntry(admin.login.toUpperCase())
                .validateBlockedStatus(admin.login.toUpperCase(), false);
        usersTabAO
                .searchUserEntry(userWithoutCompletedRuns.login.toUpperCase())
                .validateBlockedStatus(userWithoutCompletedRuns.login.toUpperCase(), false);
        if (!ldapUserBlockMonitor) {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .switchToSystem()
                    .enableLdapUserBlockMonitor()
                    .save();
        }
        runsMenu()
                .showLog(getRunId())
                .waitForSshLink();
        Utils.sleep(Long.parseLong(C.SYSTEM_MONITOR_DELAY), TimeUnit.MILLISECONDS);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login.toUpperCase())
                .validateBlockedStatus(admin.login.toUpperCase(), false);
        usersTabAO
                .searchUserEntry(userWithoutCompletedRuns.login.toUpperCase())
                .validateBlockedStatus(userWithoutCompletedRuns.login.toUpperCase(), true);
        runsMenu()
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(format("samba-tool user enable %s", user.login.toUpperCase()))
                        .assertPageContainsString(format("Enabled user '%s'", user.login.toUpperCase()))
                        .close());
        Utils.sleep(Long.parseLong(C.SYSTEM_MONITOR_DELAY), TimeUnit.MILLISECONDS);
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login.toUpperCase())
                .validateBlockedStatus(admin.login.toUpperCase(), false);
        usersTabAO
                .searchUserEntry(userWithoutCompletedRuns.login.toUpperCase())
                .validateBlockedStatus(userWithoutCompletedRuns.login.toUpperCase(), true);
    }
}
