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

import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import com.epam.pipeline.autotests.utils.listener.ConditionalTestAnalyzer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.CLOUD_REGION;
import static java.lang.String.format;

@Listeners(value = ConditionalTestAnalyzer.class)
public class DataStoragesCLITest extends AbstractSeveralPipelineRunningTest
        implements Authorization, Navigation {

    private final String anotherCloudRegion = C.ANOTHER_CLOUD_REGION;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private final String rootHost = "root@pipeline";
    private String storage1 = "dataStorageCLI-" + Utils.randomSuffix();
    private String storage2 = "dataStorageCLI-" + Utils.randomSuffix();
    private String fileFor1469 = "fileFromStorage1";
    private String storage3 = "dataStorageCLI-" + Utils.randomSuffix();
    private String folder1 = "3-folderDataStorageCLI";
    private String folder2 = "2-folderDataStorageCLI";
    private String folder3 = "4-folderDataStorageCLI";
    private String folder4 = "5-folderDataStorageCLI";
    private String fileFor1339_1 = "6-fileFor1339";
    private String fileFor1339_2 = "1-fileFor1339";
    private String fileFor1339_3 = "7-fileFor1339";
    private String pathStorage3 = "";
    private String runID1339 = "";

    @AfterClass(alwaysRun = true)
    public void removeStorages() {
        open(C.ROOT_ADDRESS);
        Utils.removeStorages(this, storage1, storage2, storage3);
    }

    @BeforeMethod
    void openApplication() {
        open(C.ROOT_ADDRESS);
    }

    @Test
    @TestCase(value = {"1469"})
    @CloudProviderOnly(values = {Cloud.AWS})
    public void checkTransferBetweenRegions() {
        String pathStorage1 = library()
                .createStorage(storage1)
                .selectStorage(storage1)
                .createAndEditFile(fileFor1469, "description1")
                .getStoragePath();
        String pathStorage2 = library()
                .clickOnCreateStorageButton()
                .setStoragePath(storage2)
                .selectValue(CLOUD_REGION, anotherCloudRegion)
                .ok()
                .selectStorage(storage2)
                .getStoragePath();
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getLastRunId())
                        .execute(format("pipe storage cp %s/%s %s/", pathStorage1, fileFor1469, pathStorage2))
                        .assertNextStringIsVisibleAtFileUpload("100%", format("pipeline-%s", getLastRunId()))
                        .close());
        library()
                .selectStorage(storage2)
                .validateElementIsPresent(fileFor1469);
        library()
                .selectStorage(storage1)
                .rmFile(fileFor1469)
                .validateCurrentFolderIsEmpty();
        runsMenu()
                .showLog(getLastRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getLastRunId())
                        .execute(format("pipe storage mv %s/%s %s/", pathStorage2, fileFor1469, pathStorage1))
                        .assertNextStringIsVisibleAtFileUpload("100%", format("pipeline-%s", getLastRunId()))
                        .close());
        library()
                .selectStorage(storage2)
                .validateHeader(storage2)
                .validateCurrentFolderIsEmpty();
        library()
                .selectStorage(storage1)
                .validateElementIsPresent(fileFor1469);
    }

    @Test
    @TestCase(value = {"1339_1"})
    public void checkPipeStorageLsPaging() {
        pathStorage3 = library()
                .clickOnCreateStorageButton()
                .setStoragePath(storage3)
                .setEnableVersioning(true)
                .ok()
                .selectStorage(storage3)
                .createFolder(folder1)
                .createFolder(folder2)
                .createFolder(folder3)
                .createFolder(folder4)
                .createFile(fileFor1339_1)
                .createFile(fileFor1339_2)
                .getStoragePath();
        String[] commands = {
                format("pipe storage ls %s/", pathStorage3),
                format("pipe storage ls --page 2 %s/", pathStorage3),
                format("pipe storage ls -p 4 %s/", pathStorage3)};
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .showLog(runID1339 = getLastRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(runID1339)
                        .execute(commands[0])
                        .assertNextStringIsVisible(commands[0], rootHost)
                        .assertPageAfterCommandContainsStrings(commands[0],
                                folder1, folder2, folder3, folder4, fileFor1339_1, fileFor1339_2)
                        .assertResultsCount(commands[0], runID1339, 6)
                        .execute(commands[1])
                        .assertNextStringIsVisible(commands[1], rootHost)
                        .assertPageAfterCommandContainsStrings(commands[1], folder2, fileFor1339_2)
                        .assertPageAfterCommandNotContainsStrings(commands[1],
                                folder1, folder3, folder4, fileFor1339_1)
                        .assertResultsCount(commands[1], runID1339, 2)
                        .execute(commands[2])
                        .assertNextStringIsVisible(commands[2], rootHost)
                        .assertPageAfterCommandContainsStrings(commands[2],
                                folder1, folder2, folder3, fileFor1339_2)
                        .assertPageAfterCommandNotContainsStrings(commands[2], folder4, fileFor1339_1)
                        .assertResultsCount(commands[2], runID1339, 4)
                        .close());
    }

    @CloudProviderOnly(values = {Cloud.AWS, Cloud.GCP})
    @Test(dependsOnMethods = {"checkPipeStorageLsPaging"})
    @TestCase(value = {"1339_2"})
    public void checkPipeStorageLsPagingOfVersions() {
        String[] commands =
                {format("pipe storage ls -l -v %s/%s", pathStorage3, fileFor1339_3),
                 format("pipe storage ls -l -v --page 3 %s/%s", pathStorage3, fileFor1339_3),
                 format("pipe storage ls -l -v -p 1 %s/%s", pathStorage3, fileFor1339_3)};
        library()
                .selectStorage(storage3)
                .createFileWithContent(fileFor1339_3, "initial description")
                .editFile(fileFor1339_3, "\n1st update")
                .editFile(fileFor1339_3, "\n2nd update")
                .editFile(fileFor1339_3, "\n3th update")
                .editFile(fileFor1339_3, "\n4th update");
       runsMenu()
                .showLog(runID1339)
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(runID1339)
                        .execute(commands[0])
                        .assertNextStringIsVisible(commands[0], rootHost)
                        .assertFileVersionsCount(commands[0], fileFor1339_3, 6)
                        .execute(commands[1])
                        .assertNextStringIsVisible(commands[1], rootHost)
                        .assertFileVersionsCount(commands[1], fileFor1339_3, 4)
                        .checkVersionsListIsSorted(commands[1])
                        .assertPageAfterCommandContainsStrings("(latest)")
                        .execute(commands[2])
                        .assertNextStringIsVisible(commands[2], rootHost)
                        .assertFileVersionsCount(commands[2], fileFor1339_3, 2)
                        .checkVersionsListIsSorted(commands[2])
                        .assertPageAfterCommandContainsStrings("(latest)")
                        .close());
    }
}
