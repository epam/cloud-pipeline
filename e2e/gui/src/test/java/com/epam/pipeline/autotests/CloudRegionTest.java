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

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.open;
import com.epam.pipeline.autotests.ao.CloudRegionsAO;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessages;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.CLOUD_REGION;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.FILE_STORAGES;
import static com.epam.pipeline.autotests.ao.Primitive.LAUNCH_BUTTON;
import static com.epam.pipeline.autotests.ao.Primitive.LIMIT_MOUNTS;
import static com.epam.pipeline.autotests.ao.Primitive.OBJECT_STORAGES;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.regex.Pattern.compile;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Stream;

public class CloudRegionTest
        extends AbstractSeveralPipelineRunningTest
        implements Authorization, Tools {

    private static final String NONE = "None";
    private static final String SAME_REGION = "Same region";
    private String nfsPrefix = C.NFS_PREFIX;
    private static final String CP_CAP_LIMIT_MOUNTS = "CP_CAP_LIMIT_MOUNTS";
    private static final String MOUNT_DATA_STORAGES = "MountDataStorages";
    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String storage1 = format("storage_3971_%s", Utils.randomSuffix());
    private final String storage2 = format("storage_3971_%s", Utils.randomSuffix());
    private final String nfsStorage1 = format("nfsStorage_3971_%s", Utils.randomSuffix());
    private final String cloudRegion1 = C.DEFAULT_CLOUD_REGION;
    private final String cloudRegion2 = C.ANOTHER_CLOUD_REGION;
    private final String cloudRegion1ID = "6";
    private final String cloudRegion2ID = "2";
    private final String mountPoint = "/testDir";
    private String[][] initialMountRules = new String[2][2];
    private String storage1ID = "";
    private String storage2ID = "";
    private String nfsStorage1ID = "";
    private String run1ID = null;
    private String run2ID = null;

    @BeforeClass
    public void preparations() {
        logoutIfNeeded();
        loginAs(admin);
        library()
                .clickOnCreateStorageButton()
                .setStoragePath(storage1)
                .selectValue(CLOUD_REGION, cloudRegion1)
                .setMountPoint(mountPoint)
                .ok()
                .clickOnCreateStorageButton()
                .setStoragePath(storage2)
                .selectValue(CLOUD_REGION, cloudRegion2)
                .setMountPoint(mountPoint)
                .ok()
                .clickOnCreateNfsMountButton()
                .setNfsMountPath("/" + nfsStorage1)
                .setNfsMountAlias(nfsStorage1)
                .setNfsMountPoint(mountPoint)
                .ok();
        storage1ID = getStorageID(storage1);
        storage2ID = getStorageID(storage2);
        nfsStorage1ID = getStorageID(nfsStorage1);
    }

    @BeforeClass
    public void getInitialRegionsMountRules() {
        getInitialRules(cloudRegion1, 0);
        getInitialRules(cloudRegion2, 1);
    }

    @AfterMethod
    public void relogin() {
        open(C.ROOT_ADDRESS);
    }

    @AfterClass
    public void cleanUpEntities() {
        Stream.of(storage1, storage2, nfsStorage1)
                .forEach(storage -> library()
                        .removeStorageIfExists(storage));
    }

    @AfterClass
    public void resumeRegionMountRules() {
        setRegionMountRules(cloudRegion1, initialMountRules[0][0], initialMountRules[0][1]);
        setRegionMountRules(cloudRegion2, initialMountRules[1][0], initialMountRules[1][1]);
    }

    @AfterClass(alwaysRun = true)
    void stopRun() {
        open(C.ROOT_ADDRESS);
        sleep(1, SECONDS);
        Stream.of(run1ID, run2ID)
                .forEach(runID -> Optional.ofNullable(runID)
                    .ifPresent(runId -> runsMenu().stopRunIfPresent(runId)));
    }

    @Test
    @TestCase(value = "3971_1")
    public void mountRulesMountNoneStoragesLocatedInCloudRegion() {
        setRegionMountRules(cloudRegion1, NONE, NONE);
        navigationMenu()
            .tools()
                .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .selectValue(CLOUD_REGION, cloudRegion1)
                .expandTab(ADVANCED_PANEL)
                .selectDataStoragesToLimitMounts()
                .clearSelection()
                .searchStorage(storage1)
                .validateNotFoundStorage()
                .searchStorage(nfsStorage1)
                .validateNotFoundStorage()
                .cancel()
                .selectValue(CLOUD_REGION, cloudRegion2)
                .selectDataStoragesToLimitMounts()
                .clearSelection()
                .searchStorage(storage1)
                .validateNotFoundStorage()
                .searchStorage(nfsStorage1)
                .validateNotFoundStorage()
                .cancel();
    }

    @Test
    @TestCase(value = "3971_2")
    public void mountRulesMountStoragesLocatedInTheSameCloudRegionWithInstance() {
        setRegionMountRules(cloudRegion1, SAME_REGION, SAME_REGION);
        navigationMenu()
                .tools()
                .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .selectValue(CLOUD_REGION, cloudRegion1)
                .expandTab(ADVANCED_PANEL)
                .selectDataStoragesToLimitMounts()
                .clearSelection()
                .searchStorage(storage1)
                .selectStorage(storage1)
                .searchStorage(nfsStorage1)
                .selectStorage(nfsStorage1)
                .ok()
                .ensure(LIMIT_MOUNTS, text(storage1), text(nfsStorage1))
                .selectValue(CLOUD_REGION, cloudRegion2)
                .ensure(LIMIT_MOUNTS, not(text(storage1)), not(text(nfsStorage1)))
                .selectDataStoragesToLimitMounts()
                .clearSelection()
                .searchStorage(storage1)
                .validateNotFoundStorage()
                .searchStorage(nfsStorage1)
                .validateNotFoundStorage()
                .cancel();
    }

    @Test
    @TestCase(value = "3971_3")
    public void mountRulesCheckMountStoragesLocatedInTheCertainCloudRegionViaPipeCLI() {
        final String[] output = new String[2];
        final String rootHost = "root@pipeline";
        setRegionMountRules(cloudRegion1, SAME_REGION, SAME_REGION);
        setRegionMountRules(cloudRegion2, NONE, NONE);
        tools()
                .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .doNotMountStoragesSelect(true)
                .launch(this)
                .showLog(getLastRunId())
                .waitForSshLink()
                .ssh(shell -> {
                    output[0] = shell
                            .waitUntilTextAppears(getLastRunId())
                            .execute(command(cloudRegion1ID))
                            .assertNextStringIsVisible("Pipeline run scheduled with RunId:",
                                    format("pipeline-%s", getLastRunId()))
                            .screenshot("screenshot-3971-1")
                            .lastCommandResult(command(cloudRegion1ID));
                    shell.refresh();
                    output[1] = shell
                            .waitUntilTextAppears(getLastRunId())
                            .execute(command(cloudRegion2ID))
                            .assertNextStringIsVisible("Pipeline run scheduled with RunId:",
                                    format("pipeline-%s", getLastRunId()))
                            .screenshot("screenshot-3971-2")
                            .lastCommandResult(command(cloudRegion2ID));
                    shell.close();
                });
        run1ID = getRunID(output[0]);
        run2ID = getRunID(output[1]);
        runsMenu()
                .viewAvailableActiveRuns()
                .shouldContainRun("pipeline", run1ID)
                .showLog(run1ID)
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(CP_CAP_LIMIT_MOUNTS, storage1), exist)
                .ensure(configurationParameter(CP_CAP_LIMIT_MOUNTS, storage2), exist)
                .ensure(configurationParameter(CP_CAP_LIMIT_MOUNTS, nfsStorage1), exist)
                .waitForTask(MOUNT_DATA_STORAGES)
                .clickTaskWithName(MOUNT_DATA_STORAGES)
                .ensure(log(), containsMessages(
                        "Found 2 available storage(s). Checking mount options.",
                        "Only 2 storages will be mounted",
                        format("-->%s mounted to /cloud-data/%s", storage1ID, nfsStorage1ID  ),
                        format("-->%s mounted to /cloud-data/%s", storage1ID),
                        format("-->%s%s mounted to /cloud-data/%s%s",
                                nfsPrefix, nfsStorage1ID, nfsPrefix, nfsStorage1ID )));
        runsMenu()
                .viewAvailableActiveRuns()
                .shouldContainRun("pipeline", run2ID)
                .showLog(run2ID)
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(CP_CAP_LIMIT_MOUNTS, storage1), exist)
                .ensure(configurationParameter(CP_CAP_LIMIT_MOUNTS, storage2), exist)
                .ensure(configurationParameter(CP_CAP_LIMIT_MOUNTS, nfsStorage1), exist)
                .waitForTask(MOUNT_DATA_STORAGES)
                .clickTaskWithName(MOUNT_DATA_STORAGES)
                .ensure(log(), containsMessages(
                        "Only 0 storages will be mounted.",
                        "Found 0 available storage(s). Checking mount options.",
                        "No remote storages are available or CP_CAP_LIMIT_MOUNTS configured to none"));

    }

    @Test
    @TestCase(value = "3971_4")
    public void mountRulesCheckFilterStoragesForCloudRegionInLaunchConfirmation() {
        setRegionMountRules(cloudRegion1, SAME_REGION, SAME_REGION);
        setRegionMountRules(cloudRegion2, NONE, NONE);
        navigationMenu()
                .tools()
                .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .selectValue(CLOUD_REGION, cloudRegion1)
                .click(LAUNCH_BUTTON)
                .checkStoragesAreInListConflictedStorages(storage1, nfsStorage1)
                .click(LAUNCH_BUTTON)
                .checkStoragesNotInListConflictedStorages(storage2);
    }

    private void getInitialRules(String region, int count) {
        CloudRegionsAO cloudRegionsAO = navigationMenu()
                .settings()
                .switchToCloudRegions()
                .selectRegion(region);
        initialMountRules[count][0] = cloudRegionsAO.getMountRule(OBJECT_STORAGES);
        initialMountRules[count][1] = cloudRegionsAO.getMountRule(FILE_STORAGES);
    }

    private void setRegionMountRules(String region, String objectStorageRule, String fileStorageRule) {
        navigationMenu()
                .settings()
                .switchToCloudRegions()
                .selectRegion(region)
                .selectValue(OBJECT_STORAGES, objectStorageRule)
                .selectValue(FILE_STORAGES, fileStorageRule)
                .save();
    }

    private String getStorageID(String storageName) {
        library()
                .selectStorage(storageName);
        return Utils.entityIDfromURL();
    }

    private String command(String regionId) {
        return format("pipe run -di %s:latest -cmd \"sleep infinity\" -pt on-demand " +
                        "-r %s -y -- CP_CAP_LIMIT_MOUNTS \"%s,%s,%s\"",
                testingTool, regionId, storage1ID, storage2ID, nfsStorage1ID);
    }

    private String getRunID(String output) {
        final Matcher matcher = compile("\\d+").matcher(output);
        assertTrue(matcher.find());
        return matcher.group(0);
    }
}
