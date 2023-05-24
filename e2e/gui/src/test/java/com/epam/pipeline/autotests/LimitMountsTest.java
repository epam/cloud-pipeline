/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessages;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CLEAR_SELECTION;
import static com.epam.pipeline.autotests.ao.Primitive.LIMIT_MOUNTS;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL_NON_SENSITIVE;
import static com.epam.pipeline.autotests.ao.Primitive.TABLE;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class LimitMountsTest extends AbstractSeveralPipelineRunningTest implements Navigation, Authorization, Tools {

    private final String storage1 = "limitMountsStorage" + Utils.randomSuffix();
    private final String storage2 = "limitMountsStorage" + Utils.randomSuffix();
    private final String storage3 = "limitMountsStorage" + Utils.randomSuffix();
    private final String storage4 = "limitMountsStorage" + Utils.randomSuffix();
    private final String registry = C.DEFAULT_REGISTRY;
    private final String testTool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private final String mountDataStoragesTask = "MountDataStorages";
    private final String cpCapLimitMounts = "CP_CAP_LIMIT_MOUNTS";

    @BeforeClass(alwaysRun = true)
    public void setPreferences() {
        library()
                .createStorage(storage1)
                .createStorage(storage2)
                .createStorage(storage3)
                .clickOnCreateStorageButton()
                .setStoragePath(storage4)
                .clickSensitiveStorageCheckbox()
                .ok();
        Stream.of(storage2, storage3, storage4).forEach(s -> givePermissionsToStorage(user, s));
        cleanToolLimitMounts();
        cleanUserLimitMounts();
        logout();
        loginAs(user);
    }

    @AfterClass(alwaysRun = true)
    public void deleteTestEntities() {
        open(C.ROOT_ADDRESS);
        logoutIfNeeded();
        loginAs(admin);
        library()
                .removeStorageIfExists(storage1)
                .removeStorageIfExists(storage2)
                .removeStorageIfExists(storage3)
                .removeStorageIfExists(storage4);
        cleanToolLimitMounts();
        cleanUserLimitMounts();
    }

    @Test
    @TestCase(value = {"2210_1"})
    public void validateSelectDataStoragesToLimitMountsForm() {
        navigationMenu()
                .settings()
                .switchToMyProfile()
                .ensure(LIMIT_MOUNTS, text("All available non-sensitive storages"))
                .assertDoNotMountStoragesIsNotChecked()
                .limitMountsPerUser()
                .ensureAll(disabled, SELECT_ALL, SELECT_ALL_NON_SENSITIVE)
                .ensureAll(enabled, CLEAR_SELECTION, CANCEL, OK)
                .storagesCountShouldBeGreaterThan(2)
                .searchStorage(storage1)
                .ensure(TABLE, not(text(storage1)))
                .clearSelection()
                .ensureAll(enabled, SELECT_ALL, SELECT_ALL_NON_SENSITIVE, OK)
                .ensureNotVisible(CLEAR_SELECTION)
                .searchStorage(storage4)
                .ensure(TABLE, not(text(storage4)))
                .cancel();
    }

    @Test(priority = 1)
    @TestCase(value = {"2210_2"})
    public void validateDoNotMountStoragesOptionInUserProfile() {
        navigationMenu()
                .settings()
                .switchToMyProfile()
                .doNotMountStoragesSelect(true);
        tools()
                .perform(registry, group, testTool, tool ->
                    tool.run(this))
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(cpCapLimitMounts, "None"), exist)
                .waitForSshLink()
                .waitForTask(mountDataStoragesTask)
                .clickTaskWithName(mountDataStoragesTask)
                .ensure(log(), containsMessages(
                        "Run is launched with mount limits (None) Only 0 storages will be mounted",
                        "No remote storages are available or CP_CAP_LIMIT_MOUNTS configured to none"))
                .ssh(shell -> shell
                        .waitUntilTextAppears(getLastRunId())
                        .execute("ls -l cloud-data/")
                        .assertOutputContains("total 0")
                        .close());
    }

    @Test(priority = 2)
    @TestCase(value = {"2210_3"})
    public void validateLimitMountsValuesFromUserProfile() {
        navigationMenu()
                .settings()
                .switchToMyProfile()
                .doNotMountStoragesSelect(true)
                .doNotMountStoragesSelect(false)
                .limitMountsPerUser()
                .clearSelection()
                .searchStorage(storage3)
                .selectStorage(storage3)
                .ok();
        tools()
                .perform(registry, group, testTool, tool ->
                        tool
                                .settings()
                                .ensure(LIMIT_MOUNTS, text("All available non-sensitive storages"))
                                .runWithCustomSettings()
                )
                .expandTab(ADVANCED_PANEL)
                .ensure(LIMIT_MOUNTS, text(storage3))
                .launch(this)
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(cpCapLimitMounts, storage3), exist)
                .openStorageFromLimitMountsParameter(storage3)
                .validateHeader(storage3);
        runsMenu()
                .showLog(getLastRunId())
                .waitForSshLink()
                .waitForTask(mountDataStoragesTask)
                .clickTaskWithName(mountDataStoragesTask)
                .sleep(5, SECONDS)
                .ensure(log(), containsMessages("Found 1 available storage(s). Checking mount options."))
                .ensure(log(), containsMessages("Only 1 storages will be mounted"))
                .ensure(log(), containsMessages(mountStorageMessage(storage3)))
                .ssh(shell -> shell
                        .waitUntilTextAppears(getLastRunId())
                        .execute("ls /cloud-data/")
                        .assertOutputContains(storage3.toLowerCase())
                        .close());
    }

    @Test(priority = 3, dependsOnMethods = "validateLimitMountsValuesFromUserProfile")
    @TestCase(value = {"2210_4"})
    public void validateLimitMountsPriorityOrderApplying() {
        logoutIfNeeded();
        loginAs(admin);
        tools()
                .perform(registry, group, testTool, tool ->
                                tool
                                        .settings()
                                        .selectDataStoragesToLimitMounts()
                                        .clearSelection()
                                        .searchStorage(storage1)
                                        .selectStorage(storage1)
                                        .searchStorage(storage2)
                                        .selectStorage(storage2)
                                        .ok()
                                        .save()
                );
        logout();
        loginAs(user);
        tools()
                .perform(registry, group, testTool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .ensure(LIMIT_MOUNTS, text(storage2))
                .ensure(LIMIT_MOUNTS, not(text(storage3)))
                .launch(this)
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(cpCapLimitMounts, storage2), exist)
                .ensure(configurationParameter(cpCapLimitMounts, storage3), not(exist))
                .openStorageFromLimitMountsParameter(storage2)
                .validateHeader(storage2);
    }

    private void givePermissionsToStorage(Account user, String storage) {
        library()
                .selectStorage(storage)
                .clickEditStorageButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .selectByName(user.login)
                .showPermissions()
                .set(READ, ALLOW)
                .set(WRITE, ALLOW)
                .set(EXECUTE, ALLOW)
                .closeAll();
    }

    private String mountStorageMessage(String storage) {
        return format("%s mounted to /cloud-data/%s", storage.toLowerCase(), storage.toLowerCase());
    }

    private void cleanToolLimitMounts() {
        tools()
                .perform(registry, group, testTool, tool ->
                        tool.settings()
                                .doNotMountStoragesSelect(true)
                                .doNotMountStoragesSelect(false)
                                .save());
    }

    private void cleanUserLimitMounts() {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(user.login.toUpperCase())
                .edit()
                .sleep(2, SECONDS)
                .doNotMountStoragesSelect(true)
                .doNotMountStoragesSelect(false)
                .ok();
    }
}
