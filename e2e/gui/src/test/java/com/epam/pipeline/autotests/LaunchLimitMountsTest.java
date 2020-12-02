/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.ToolSettings;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.BucketPermission;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessages;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CLEAR_SELECTION;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.LIMIT_MOUNTS;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.SEARCH_INPUT;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL;
import static com.epam.pipeline.autotests.ao.Primitive.SELECT_ALL_NON_SENSITIVE;
import static com.epam.pipeline.autotests.ao.Primitive.SENSITIVE_STORAGE;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;

public class LaunchLimitMountsTest
        extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {
    private String storage1 = "launchLimitMountsStorage" + Utils.randomSuffix();
    private String storage2 = "launchLimitMountsStorage" + Utils.randomSuffix();
    private String storage3 = "launchLimitMountsStorage" + Utils.randomSuffix();
    private String storageSensitive = "launchLimitMountsStorage" + Utils.randomSuffix();
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private final String mountDataStoragesTask = "MountDataStorages";
    private String storageID = "";
    private String sensitiveStorageID = "";
    private String testRunID = "";
    private String message = "Selection contains sensitive storages. This will apply a number of restrictions " +
            "for the job: no Internet access, all the storages will be available in a read-only mode, " +
            "you won't be able to extract the data from the running job and other.";
    private String warningMessage = "A large number of the object data storages (%s) are going to be mounted for this job";

    @BeforeClass(alwaysRun = true)
    public void setPreferences() {
        library()
                .createStorage(storage1)
                .createStorage(storage2)
                .clickOnCreateStorageButton()
                .setStoragePath(storageSensitive)
                .clickSensitiveStorageCheckbox()
                .ok()
                .selectStorage(storage1)
                .validateHeader(storage1);

        storageID = Utils.entityIDfromURL();
        library()
                .selectStorage(storageSensitive)
                .validateHeader(storageSensitive);

        sensitiveStorageID = Utils.entityIDfromURL();
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool.settings()
                                .disableAllowSensitiveStorage()
                                .performIf(SAVE, enabled, ToolSettings::save)
                );
    }

    @BeforeMethod
    public void openPage() {
        open(C.ROOT_ADDRESS);
    }

    @AfterClass(alwaysRun = true)
    public void removeEntities() {
        open(C.ROOT_ADDRESS);
        logoutIfNeeded();
        loginAs(admin);
        library()
                .removeStorage(storage1)
                .removeStorage(storage2)
                .removeStorage(storageSensitive)
                .removeStorage(storage3);
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool.settings()
                                .disableAllowSensitiveStorage()
                                .performIf(SAVE, enabled, ToolSettings::save)
                );
    }

    @Test(priority = 1)
    @TestCase(value = {"EPMCMBIBPC-2681"})
    public void prepareLimitMounts() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .ensure(LIMIT_MOUNTS, text("All available non-sensitive storages"))
                .selectDataStoragesToLimitMounts()
                .ensureVisible(SEARCH_INPUT)
                .ensureAll(disabled, SELECT_ALL, SELECT_ALL_NON_SENSITIVE)
                .ensureAll(enabled, CLEAR_SELECTION, CANCEL, OK)
                .validateFields("", "Name", "Type")
                .storagesCountShouldBeGreaterThan(2)
                .clearSelection()
                .ensureAll(enabled, SELECT_ALL, SELECT_ALL_NON_SENSITIVE, OK)
                .ensureNotVisible(CLEAR_SELECTION)
                .searchStorage(storage1)
                .selectStorage(storage1)
                .ensureVisible(CLEAR_SELECTION)
                .ensureAll(enabled, OK)
                .ok()
                .ensure(LIMIT_MOUNTS, text(storage1));
    }

    @Test(priority = 1)
    @TestCase(value = {"EPMCMBIBPC-2682"})
    public void runPipelineWithLimitMounts() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .selectDataStoragesToLimitMounts()
                .clearSelection()
                .searchStorage(storage1)
                .selectStorage(storage1)
                .ok()
                .launch(this)
                .showLog(testRunID = getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_LIMIT_MOUNTS", storage1), exist)
                .waitForSshLink()
                .waitForTask(mountDataStoragesTask)
                .click(taskWithName(mountDataStoragesTask))
                .ensure(log(), containsMessages("Found 1 available storage(s). Checking mount options."))
                .ensure(log(), containsMessages(format("Run is launched with mount limits (%s) Only 1 storages will be mounted", storageID)))
                .ensure(log(), containsMessages(mountStorageMessage(storage1)))
                .ssh(shell -> shell
                        .execute("ls /cloud-data/")
                        .assertOutputContains(storage1.toLowerCase())
                        .assertPageDoesNotContain(storage2.toLowerCase())
                        .close());
    }

    @Test(priority = 1, dependsOnMethods = {"runPipelineWithLimitMounts"})
    @TestCase(value = {"EPMCMBIBPC-2683"})
    public void rerunPipelineWithoutLimitMounts() {
        final Set<String> logMess =
                 runsMenu()
                .showLog(testRunID)
                .stop(format("pipeline-%s", testRunID))
                .clickOnRerunButton()
                .expandTab(ADVANCED_PANEL)
                .ensure(LIMIT_MOUNTS, text(storage1))
                .selectDataStoragesToLimitMounts()
                .selectAllNonSensitive()
                .ok()
                .ensure(LIMIT_MOUNTS, text("All available non-sensitive storages"))
                .launch(this)
                .showLog(getLastRunId())
                .ensureNotVisible(PARAMETERS)
                .waitForSshLink()
                .waitForTask(mountDataStoragesTask)
                .clickMountBuckets()
                .logMessages()
                .collect(toSet());

        runsMenu()
                .showLog(getLastRunId())
                .logContainsMessage(logMess, " available storage(s). Checking mount options.")
                .checkAvailableStoragesCount(logMess, 2)
                .logNotContainsMessage(logMess, "Run is launched with mount limits")
                .logContainsMessage(logMess, mountStorageMessage(storage1))
                .logContainsMessage(logMess, mountStorageMessage(storage2))
                .ssh(shell -> shell
                        .execute("ls /cloud-data/")
                        .assertOutputContains(storage1.toLowerCase())
                        .assertOutputContains(storage2.toLowerCase())
                        .close());
    }

    @Test(priority = 2)
    @TestCase(value = {"EPMCMBIBPC-3177"})
    public void prepareSensitiveLimitMounts() {
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool.settings()
                                .enableAllowSensitiveStorage()
                                .performIf(SAVE, enabled, ToolSettings::save)
                );
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .ensure(LIMIT_MOUNTS, text("All available non-sensitive storages"))
                .selectDataStoragesToLimitMounts()
                .ensureVisible(SEARCH_INPUT)
                .ensureAll(disabled, SELECT_ALL_NON_SENSITIVE)
                .ensureAll(enabled, SELECT_ALL, CLEAR_SELECTION, CANCEL, OK)
                .validateFields("", "Name", "Type")
                .storagesCountShouldBeGreaterThan(2)
                .clearSelection()
                .ensureAll(enabled, SELECT_ALL, SELECT_ALL_NON_SENSITIVE, OK)
                .ensureNotVisible(CLEAR_SELECTION)
                .click(SELECT_ALL)
                .ensure(SENSITIVE_STORAGE, text(message))
                .ensureVisible(CLEAR_SELECTION)
                .ensure(OK, enabled)
                .clearSelection()
                .ensureNotVisible(SENSITIVE_STORAGE)
                .ensureNotVisible(CLEAR_SELECTION)
                .ensure(OK, enabled)
                .searchStorage(storageSensitive)
                .selectStorage(storageSensitive)
                .ensure(SENSITIVE_STORAGE, text(message))
                .ensureVisible(CLEAR_SELECTION)
                .ensure(OK, enabled)
                .searchStorage(storage1)
                .selectStorage(storage1)
                .ok()
                .ensure(LIMIT_MOUNTS, text(storage1), text(storageSensitive));
    }

    @Test(priority = 2, dependsOnMethods = {"prepareSensitiveLimitMounts"})
    @TestCase(value = {"EPMCMBIBPC-3178"})
    public void runPipelineWithSensitiveLimitMounts() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .selectDataStoragesToLimitMounts()
                .clearSelection()
                .searchStorage(storageSensitive)
                .selectStorage(storageSensitive)
                .searchStorage(storage1)
                .selectStorage(storage1)
                .ok()
                .clickAddSystemParameter()
                .selectSystemParameters("CP_S3_FUSE_TYPE")
                .ok()
                .inputSystemParameterValue("CP_S3_FUSE_TYPE", "pipefuse")
                .launch(this)
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .checkMountLimitsParameter(storageSensitive, storage1)
                .waitForSshLink()
                .waitForTask(mountDataStoragesTask)
                .click(taskWithName(mountDataStoragesTask))
                .ensure(log(), containsMessages("Found 2 available storage(s). Checking mount options."))
                .ensure(log(), containsMessages(format("Run is launched with mount limits (%s,%s) Only 2 storages will be mounted",
                        sensitiveStorageID, storageID)))
                .ensure(log(), containsMessages(mountStorageMessage(storage1)))
                .ensure(log(), containsMessages(mountStorageMessage(storageSensitive)))
                .ssh(shell -> shell
                        .execute("ls /cloud-data/")
                        .assertOutputContains(storage1.toLowerCase())
                        .assertOutputContains(storageSensitive.toLowerCase())
                        .close());
    }

    @Test(priority = 3)
    @TestCase(value = {"1447"})
    public void displayingCPCAPLIMITMOUNTSinUserFriendlyManner() {
        library()
                .createStorage(storage3);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .selectDataStoragesToLimitMounts()
                .clearSelection()
                .searchStorage(storage3)
                .selectStorage(storage3)
                .ok()
                .launch(this)
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_LIMIT_MOUNTS", storage3), exist)
                .openStorageFromLimitMountsParameter(storage3)
                .validateHeader(storage3);
    }

    @Test(priority = 4)
    @TestCase(value = {"1480"})
    public void checkWarningInCaseOfOOMriskDueToStorageMountsNumber() {
        List<String> addStor = new ArrayList<>();
        int countObjectStorages = 0;
        int minRAM = 0;
        try {
            logoutIfNeeded();
            loginAs(user);
            countObjectStorages = tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .selectDataStoragesToLimitMounts()
                    .clearSelection()
                    .selectAll()
                    .countObjectStorages();
            minRAM = tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .expandTab(ADVANCED_PANEL)
                    .minNodeTypeRAM();
            addStor = createStoragesIfNeeded(countObjectStorages, minRAM);
            final String warning = format(warningMessage, countObjectStorages);
            checkOOMwarningMessage("1", true, warning);
            checkOOMwarningMessage("100", false, warning);
        } finally {
            if(countObjectStorages < minRAM) {
                logoutIfNeeded();
                loginAs(admin);
                addStor.forEach(stor -> library().removeStorage(stor));
            }
        }
    }

    private String mountStorageMessage(String storage) {
        return format("%s mounted to /cloud-data/%s", storage.toLowerCase(), storage.toLowerCase());
    }

    private void checkOOMwarningMessage(String storageMountsPerGBratio, boolean messageIsVisible, String warning) {
        logout();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .setPreference("storage.mounts.per.gb.ratio", storageMountsPerGBratio, true)
                .saveIfNeeded();
        logout();
        loginAs(user);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .checkWarningMessage(warning, messageIsVisible)
                .checkLaunchWarningMessage(warning, messageIsVisible);
    }

    private List<String> createStoragesIfNeeded(int objStor, int min) {
        if (objStor >= min) {
            return new ArrayList<>();
        }
        logout();
        loginAs(admin);
        return IntStream.range(0, min - objStor)
                .mapToObj(i -> {
                    String storage = "launchLimitMountsStorage" + Utils.randomSuffix();
                    library()
                            .createStorage(storage)
                            .selectStorage(storage)
                            .clickEditStorageButton()
                            .clickOnPermissionsTab()
                            .addNewUser(user.login)
                            .closeAll();
                    givePermissions(user,
                            BucketPermission.allow(READ, storage),
                            BucketPermission.allow(WRITE, storage),
                            BucketPermission.allow(EXECUTE, storage)
                    );
                    return storage;
                }).collect(Collectors.toList());
    }
}
