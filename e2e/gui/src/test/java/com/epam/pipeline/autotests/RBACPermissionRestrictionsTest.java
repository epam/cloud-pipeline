/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.ConfirmationPopupAO.confirmCommittingToExistingTool;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE_PERSONAL_GROUP;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import com.epam.pipeline.autotests.ao.ToolGroup;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.DENY;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.String.format;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RBACPermissionRestrictionsTest
        extends AbstractSeveralPipelineRunningTest implements Authorization {

        private final String defaultRegistry = C.DEFAULT_REGISTRY;
        private final String defaultRegistryId = C.DEFAULT_REGISTRY_IP;
        private final String testingTool = C.TESTING_TOOL_NAME;
        private final String personalGroup = "Personal";
        private final String defaultGroup = C.DEFAULT_GROUP;
        private final String userRoleGroup = C.ROLE_USER;
        private final String testGroup = "ROLE_CONFIGURATION_MANAGER";
        private final static String uiPersonalToolsPermissionsRestrictions = "ui.personal.tools.permissions.restrictions";
        private final String storage1 = format("storage_3389_%s", Utils.randomSuffix());
        private String toolPersonalRestrictions1 = format("[{\n\"role\": \"ROLE_%s\",\n\"disable\": \"WRITE,EXECUTE\"}]",
                userRoleGroup);
        private String toolPersonalRestrictions2 = "[{\n\"role\": \"ALL\",\n\"disable\": \"WRITE,EXECUTE\"}]";
        private final String personalGroupName = format("%s/%s", user.login.toLowerCase(), nameWithoutGroup(testingTool));
        private String[] initialToolsPermissionsRestrictions = new String[1];

    @BeforeClass
    private void getInitialParameters() {
        initialToolsPermissionsRestrictions = navigationMenu()
                .settings()
                .switchToPreferences()
                .getPreference(uiPersonalToolsPermissionsRestrictions);
    }

    @AfterClass(alwaysRun = true)
    public void restorePreference() {
        open(C.ROOT_ADDRESS);
        logoutIfNeeded();
        loginAs(admin);
        setRestrictions(uiPersonalToolsPermissionsRestrictions,
                initialToolsPermissionsRestrictions[0]);
    }

    @Test
    @TestCase(value = "3230_1")
    private void personalToolGroupsAndToolsPermissionRestrictionsForGroup() {
        try {
            setRestrictions(uiPersonalToolsPermissionsRestrictions, toolPersonalRestrictions1);
            preparations();
            tools()
                    .performWithin(defaultRegistry, personalGroup, group ->
                            group.editGroup(settings ->
                                    settings.permissions()
                                            .addNewGroup(userRoleGroup)
                                            .selectByName(getUserNameByAccountLogin(userRoleGroup))
                                            .showPermissions()
                                            .validatePrivilegeValue(EXECUTE, DENY)
                                            .validatePrivilegeValue(WRITE, DENY)
                                            .validatePrivilegesAreDisabled(WRITE, EXECUTE)
                                            .closeAll())
                    );
            tools()
                    .performWithin(defaultRegistry, personalGroup, personalGroupName, tool ->
                            tool.permissions()
                                    .addNewGroup(userRoleGroup)
                                    .selectByName(getUserNameByAccountLogin(userRoleGroup))
                                    .showPermissions()
                                    .validatePrivilegeValue(EXECUTE, DENY)
                                    .validatePrivilegeValue(WRITE, DENY)
                                    .validatePrivilegesAreDisabled(WRITE, EXECUTE)
                                    .closeAll()
                    );
        } finally {
            open(C.ROOT_ADDRESS);
            deletePermissions();
        }
    }

    @Test(dependsOnMethods = "personalToolGroupsAndToolsPermissionRestrictionsForGroup")
    @TestCase(value = "3230_2")
    private void personalToolGroupsAndToolsPermissionRestrictionsForAllGroup() {
        try {
            setRestrictions(uiPersonalToolsPermissionsRestrictions, toolPersonalRestrictions2);
            logoutIfNeeded();
            loginAs(user);
            tools()
                    .performWithin(defaultRegistry, personalGroup, group ->
                            group.editGroup(settings ->
                                    settings.permissions()
                                            .addNewGroup(testGroup)
                                            .selectByName(getUserNameByAccountLogin(testGroup))
                                            .showPermissions()
                                            .validatePrivilegeValue(EXECUTE, DENY)
                                            .validatePrivilegeValue(WRITE, DENY)
                                            .validatePrivilegesAreDisabled(WRITE, EXECUTE)
                                            .closeAll())
                    );
            tools()
                    .performWithin(defaultRegistry, personalGroup, personalGroupName, tool ->
                            tool.permissions()
                                    .addNewGroup(testGroup)
                                    .selectByName(getUserNameByAccountLogin(testGroup))
                                    .showPermissions()
                                    .validatePrivilegeValue(EXECUTE, DENY)
                                    .validatePrivilegeValue(WRITE, DENY)
                                    .validatePrivilegesAreDisabled(WRITE, EXECUTE)
                                    .closeAll()
                    );
        } finally {
            open(C.ROOT_ADDRESS);
            deletePermissions();
        }
    }

    private void preparations() {
        logoutIfNeeded();
        loginAs(user);
        tools()
                .perform(defaultRegistry, personalGroup, group ->
                        group.performIf(CREATE_PERSONAL_GROUP, visible, ToolGroup::createPersonalGroup));
        tools()
                .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .doNotMountStoragesSelect(true)
                .launch(this)
                .showLog(getLastRunId())
                .waitForCommitButton()
                .commit(commit ->
                        commit.setRegistry(defaultRegistry)
                                .setGroup(personalGroup)
                                .ok()
                                .also(confirmCommittingToExistingTool(defaultRegistryId, personalGroupName)))
                .assertCommittingFinishedSuccessfully();
    }

    private void setRestrictions(String preference, String value) {
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .updateCodeText(preference, value, true)
                .saveIfNeeded();
    }

    private void deletePermissions() {
        tools()
                .performWithin(defaultRegistry, personalGroup, group ->
                        group.editGroup(settings ->
                                settings.permissions()
                                        .deleteIfPresent(userRoleGroup)
                                        .closeAll()));
        tools()
                .performWithin(defaultRegistry, personalGroup, personalGroupName, tool ->
                        tool.permissions()
                                .deleteIfPresent(userRoleGroup)
                                .closeAll());
    }
}
