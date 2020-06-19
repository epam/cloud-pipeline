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

import com.epam.pipeline.autotests.ao.DocumentTabAO;
import com.epam.pipeline.autotests.ao.GlobalSearchAO;
import com.epam.pipeline.autotests.ao.LibraryFolderAO;
import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.LogAO.Status;
import com.epam.pipeline.autotests.ao.NavigationHomeAO;
import com.epam.pipeline.autotests.ao.NavigationMenuAO;
import com.epam.pipeline.autotests.ao.PipelineCodeTabAO;
import com.epam.pipeline.autotests.ao.PipelineLibraryContentAO;
import com.epam.pipeline.autotests.ao.StorageContentAO;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.Keys;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.ao.Profile.advancedTab;
import static com.epam.pipeline.autotests.utils.C.LOGIN;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class GlobalSearchTest extends AbstractSeveralPipelineRunningTest implements Navigation {

    private final String folder = "globalSearchFolder" + Utils.randomSuffix();
    private final String pipeline = "globalSearchPipeline" + Utils.randomSuffix();
    private final String configuration = "globalSearchConf" + Utils.randomSuffix();
    private final String innerFolder1 = "globalSearchInnerFolder1" + Utils.randomSuffix();
    private final String innerFolder2 = "globalSearchInnerFolder2" + Utils.randomSuffix();
    private final String storage = "globalSearchStorage" + Utils.randomSuffix();
    private final String storageFolder = "globalSearchStorageFolder" + Utils.randomSuffix();
    private final String storageFile = "globalSearchStorageFile" + Utils.randomSuffix();
    private final String storageFileContent = "globalSearchStorageFileContent" + Utils.randomSuffix();
    private final String storageAlias = "globalSearchStorageAlias" + Utils.randomSuffix();
    private final String customConfigurationProfile = "custom-profile";
    private final String customDisk = "22";
    private final String defaultInstanceType = C.DEFAULT_INSTANCE;
    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String defaultProfile = "default";
    private final String configurationName = "test_conf";
    private final String configurationNodeType = "c5.large (CPU: 2, RAM: 4)";
    private final String configurationDisk = "23";
    private final String configVar = "config.json";
    private final String title = "testIssue" + Utils.randomSuffix();
    private final String description = "testIssueDescription";
    private String draftVersionName = "";
    private String testRunID = "";
    private String pipelineWithRun = String.format("%s-%s", pipeline.toLowerCase(), testRunID);

    @BeforeClass
    @TestCase(value = {"EPMCMBIBPC-2653"})
    public void prepareForSearch() {
        navigationMenu()
                .library()
                .createFolder(folder)
                .cd(folder)
                .createPipeline(pipeline)
                .createFolder(innerFolder1)
                .createConfiguration(configuration)
                .configurationWithin(configuration, configuration ->
                        configuration.expandTabs(advancedTab)
                                .setValue(DISK, customDisk)
                                .selectValue(INSTANCE_TYPE, defaultInstanceType)
                                .setValue(NAME, customConfigurationProfile)
                                .selectDockerImage(dockerImage ->
                                        dockerImage
                                                .selectRegistry(defaultRegistry)
                                                .selectGroup(defaultGroup)
                                                .selectTool(testingTool)
                                                .click(OK)
                                )
                                .click(SAVE)
                );
        navigationMenu()
                .library()
                .cd(folder)
                .cd(innerFolder1)
                .createFolder(innerFolder2);
        navigationMenu()
                .library()
                .cd(folder)
                .createStorage(storage)
                .selectStorage(storage)
                .createFolder(storageFolder)
                .createAndEditFile(storageFile, storageFileContent);
        home()
                .globalSearch()
                .ensureVisible(FOLDERS, PIPELINES, RUNS, TOOLS, DATA, ISSUES, SEARCH, QUESTION_MARK)
                .ensureAll(enabled, FOLDERS, PIPELINES, RUNS, TOOLS, DATA, ISSUES)
                .close();
        home()
                .ensure(NavigationHomeAO.panel("Active runs"), visible);
        sleep(2, SECONDS);
        search()
                .hover(QUESTION_MARK)
                .sleep(1, SECONDS)
                .messageShouldAppear("The query string supports the following special characters:")
                .close();

        sleep(2, MINUTES);
    }

    @BeforeMethod
    public void checkCloseSearch() {
        while($(byClassName("earch__search-container")).is(visible)) {
            actions().sendKeys(Keys.ESCAPE).perform();
        }
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2657"})
    public void searchResultCancel() {
        search()
                .search(folder)
                .enter()
                .sleep(3, SECONDS)
                .ensure(FOLDERS, enabled)
                .ensureAll(GlobalSearchAO.disable, PIPELINES, RUNS, TOOLS, DATA, ISSUES)
                .hover(SEARCH_RESULT)
                .close()
                .ensureAll(enabled, FOLDERS, PIPELINES, RUNS, TOOLS, DATA, ISSUES)
                .ensure(SEARCH, empty)
                .close();
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2658"})
    public void searchForPipeline() {
        library().cd(folder)
                .clickOnDraftVersion(pipeline)
                .configurationTab()
                .editConfiguration(defaultProfile, profile ->
                        profile
                                .expandTab(EXEC_ENVIRONMENT)
                                .expandTab(ADVANCED_PANEL)
                                .clear(NAME).setValue(NAME,configurationName)
                                .clear(DISK).setValue(DISK,configurationDisk)
                                .selectValue(INSTANCE_TYPE, configurationNodeType)
                                .sleep(2, SECONDS)
                                .click(SAVE)
                                .sleep(2, SECONDS)
                );
        draftVersionName = library()
                .cd(folder)
                .clickOnPipeline(pipeline)
                .getFirstVersionName();
        home().sleep(2, MINUTES);
        search()
                .search(pipeline)
                .enter()
                .sleep(2, SECONDS)
                .ensureAll(GlobalSearchAO.disable, FOLDERS, RUNS, TOOLS, DATA, ISSUES)
                .ensureAll(enabled, PIPELINES)
                .ensure(PIPELINES, text("3 PIPELINES"))
                .validateCountSearchResults(3)
                .hover(SEARCH_RESULT)
                .openSearchResultItem(pipeline)
                .ensure(TITLE, text(pipeline))
                .ensure(HIGHLIGHTS, text("Found in name"))
                .ensure(INFO, text(draftVersionName))
                .parent()
                .openSearchResultItem("docs/README.md")
                .ensure(TITLE, text("README.md"))
                .ensure(HIGHLIGHTS, text("Found in pipelineName"))
                .ensure(PREVIEW, text("Job definition"))
                .parent()
                .openSearchResultItem(configVar)
                .ensure(TITLE, text(configVar))
                .ensure(DESCRIPTION, text(pipeline))
                .ensure(HIGHLIGHTS, text("Found in pipelineName"))
                .ensure(PREVIEW, text(configurationDisk), text(configurationName),
                        text(configurationNodeType.substring(0, configurationNodeType.indexOf(" "))))
                .parent()
                .moveToSearchResultItem(pipeline, () -> new PipelineLibraryContentAO(pipeline))
                .assertPipelineName(pipeline);
        search()
                .search(pipeline)
                .enter()
                .sleep(2, SECONDS)
                .moveToSearchResultItem("docs/README.md", () -> new DocumentTabAO(pipeline))
                .shouldContainDocument("README.md");
        search()
                .search(pipeline)
                .enter()
                .sleep(2, SECONDS)
                .moveToSearchResultItem(configVar, () -> new PipelineCodeTabAO(pipeline))
                .ensure(byText(configVar), visible);
    }

    @Test(dependsOnMethods = {"searchForPipeline"})
    @TestCase(value = {"EPMCMBIBPC-2662"})
    public void searchForPipelineWithRuns() {
        library()
                .cd(folder)
                .clickOnPipeline(pipeline)
                .firstVersion()
                .runPipeline()
                .launch(this)
                .showLog(testRunID=getLastRunId())
                .waitForCompletion();
        library()
                .cd(folder)
                .clickOnPipeline(pipeline)
                .firstVersion()
                .runPipeline()
                .launch(this)
                .stopRun(getLastRunId());
        library()
                .cd(folder)
                .clickOnPipeline(pipeline)
                .firstVersion()
                .runPipeline()
                .launch(this);
        search()
                .click(PIPELINES)
                .search(pipeline)
                .enter()
                .sleep(2, SECONDS)
                .hover(SEARCH_RESULT)
                .openSearchResultItem(pipeline)
                .ensure(TITLE, text(pipeline))
                .ensure(HIGHLIGHTS, text("Found in name"))
                .ensure(INFO, text(draftVersionName))
                .checkCompletedField()
                .close()
                .close();
    }

    @Test(dependsOnMethods = {"searchForPipelineWithRuns"})
    @TestCase(value = {"EPMCMBIBPC-2663"})
    public void searchForPipelineRun() {
        home();
        search()
                .click(RUNS)
                .search(pipeline)
                .enter()
                .sleep(2, SECONDS)
                .hover(SEARCH_RESULT)
                .openSearchResultItemWithText(pipelineWithRun)
                .ensure(TITLE, Status.SUCCESS.reached, text(testRunID), text(pipeline), text(draftVersionName))
                .checkTags(configurationDisk, configurationNodeType)
                .ensure(HIGHLIGHTS, text("Found in pipelineName"), text("Found in description"),
                        text("Found in podId"))
                .ensure(PREVIEW, text("Owner"), text(LOGIN), text("Scheduled"), text("Started"),
                        text("Finished"), text("Estimated price"))
                .ensure(PREVIEW_TAB, text("InitializeNode"))
                .parent()
                .moveToSearchResultItemWithText(pipelineWithRun, LogAO::new)
                .ensure(STATUS, text(String.format("Run #%s", testRunID)));
    }

    @Test(dependsOnMethods = {"searchForPipelineWithRuns"})
    @TestCase(value = {"EPMCMBIBPC-2665"})
    public void searchForPipelineRunByID() {
        home();
        search()
                .search(testRunID)
                .enter()
                .sleep(2, SECONDS)
                .hover(SEARCH_RESULT)
                .openSearchResultItemWithText(pipelineWithRun)
                .ensure(TITLE, Status.SUCCESS.reached, text(testRunID), text(pipeline))
                .ensure(HIGHLIGHTS, text("Found in id"), text("Found in logs"))
                .close()
                .close();
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2660"})
    public void searchForStorage() {
        home();
        search()
                .search(storage)
                .enter()
                .sleep(2, SECONDS)
                .validateCountSearchResults(2)
                .ensure(DATA, text("2 DATA"))
                .ensureAll(GlobalSearchAO.disable, FOLDERS, PIPELINES, TOOLS, ISSUES)
                .hover(SEARCH_RESULT)
                .openSearchResultItem(storage)
                .ensure(TITLE, text(storage))
                .ensure(HIGHLIGHTS, text("Found in name"))
                .ensure(PREVIEW, text(storageFolder), text(storageFile))
                .parent()
                .openSearchResultItem(storageFile)
                .ensure(TITLE, text(storageFile))
                .ensure(HIGHLIGHTS, text("Found in storage_name"))
                .ensure(INFO, text(storage), text(String.format("s3://%s%s", storage, storageFile)))
                .ensure(ATTRIBUTES, text(LOGIN))
                .ensure(PREVIEW, text(storageFileContent))
                .parent()
                .moveToSearchResultItem(storage, StorageContentAO::new)
                .validateHeader(storage);
        search()
                .search(storage)
                .enter()
                .sleep(2, SECONDS)
                .moveToSearchResultItem(storageFile, StorageContentAO::new)
                .validateHeader(storage);
    }

    @Test(dependsOnMethods = {"searchForStorage"})
    @TestCase(value = {"EPMCMBIBPC-2661"})
    public void searchForStorageWithChangedName() {
        home();
        library()
                .cd(folder)
                .selectStorage(storage)
                .clickEditStorageButton()
                .setAlias(storageAlias)
                .ok();
        home().sleep(3, MINUTES);
        search()
                .search(storageAlias)
                .enter()
                .sleep(2, SECONDS)
                .hover(SEARCH_RESULT)
                .openSearchResultItem(storageAlias)
                .ensure(TITLE, text(storageAlias))
                .ensure(HIGHLIGHTS, text("Found in name"))
                .ensure(PREVIEW, text(storageFolder), text(storageFile))
                .parent()
                .search(storage.toLowerCase())
                .enter()
                .sleep(2, SECONDS)
                .hover(SEARCH_RESULT)
                .openSearchResultItem(storageAlias)
                .ensure(TITLE, text(storageAlias))
                .ensure(HIGHLIGHTS, text("Found in path"))
                .ensure(PREVIEW, text(storageFolder), text(storageFile))
                .close()
                .close();
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2670"})
    public void issueSearch() {
        library()
                .clickOnFolder(folder)
                .showIssues()
                .clickNewIssue()
                .addNewIssue(title, description);
        home().sleep(6, MINUTES);
        search()
                .search(title)
                .enter()
                .sleep(2, SECONDS)
                .hover(SEARCH_RESULT)
                .openSearchResultItem(title)
                .ensure(TITLE, text(title))
                .ensure(DESCRIPTION, matchText("Opened .* by You"))
                .ensure(HIGHLIGHTS, text("Found in name"))
                .ensure(PREVIEW, text(description))
                .close()
                .close();
        search()
                .click(ISSUES)
                .search(description)
                .enter()
                .hover(SEARCH_RESULT)
                .openSearchResultItem(title)
                .ensure(TITLE, text(title))
                .ensure(DESCRIPTION, matchText("Opened .* by You"))
                .ensure(HIGHLIGHTS, text("Found in text"))
                .ensure(PREVIEW, text(description))
                .close()
                .close();
    }

    @Test(dependsOnMethods = {"issueSearch"})
    @TestCase(value = {"EPMCMBIBPC-2671"})
    public void identicalNamesSearch() {
        library()
                .cd(folder)
                .createFolder(title);
        home().sleep(5, MINUTES);
        search()
                .ensureAll(enabled, FOLDERS, PIPELINES, RUNS, TOOLS, DATA, ISSUES)
                .search(title)
                .enter()
                .ensureAll(GlobalSearchAO.disable, PIPELINES, RUNS, TOOLS, DATA)
                .ensureAll(enabled, FOLDERS, ISSUES)
                .ensure(FOLDERS, text("1 FOLDER"))
                .ensure(ISSUES, text("1 ISSUE"))
                .validateSearchResults(2, title)
                .click(FOLDERS)
                .ensureAll(enabled, FOLDERS, ISSUES)
                .ensure(FOLDERS, GlobalSearchAO.selected)
                .validateSearchResults(1, title)
                .click(FOLDERS)
                .ensureAll(enabled, FOLDERS, ISSUES)
                .validateSearchResults(2, title)
                .close()
                .click(ISSUES)
                .ensureAll(enabled, FOLDERS, PIPELINES, RUNS, TOOLS, DATA, ISSUES)
                .ensure(ISSUES, GlobalSearchAO.selected)
                .search(title)
                .enter()
                .ensureAll(GlobalSearchAO.disable, FOLDERS, PIPELINES, RUNS, TOOLS, DATA)
                .ensure(ISSUES, enabled)
                .ensure(ISSUES, text("1 ISSUE"))
                .ensure(ISSUES, GlobalSearchAO.selected)
                .validateSearchResults(1, title)
                .click(ISSUES)
                .ensureAll(GlobalSearchAO.disable, PIPELINES, RUNS, TOOLS, DATA)
                .ensureAll(enabled, FOLDERS, ISSUES)
                .validateSearchResults(2, title)
                .close()
                .close();
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2654"})
    public void folderSearch() {
        search()
                .search(innerFolder1)
                .sleep(3, SECONDS)
                .validateSearchResults(1, innerFolder1)
                .hover(SEARCH_RESULT)
                .openSearchResultItem(innerFolder1)
                .ensure(TITLE, text(innerFolder1))
                .ensure(HIGHLIGHTS, text("Found in name"))
                .ensure(PREVIEW, text(innerFolder2))
                .parent()
                .moveToSearchResultItem(innerFolder1, () -> new LibraryFolderAO(innerFolder1))
                .ensureAll(visible, SETTINGS, UPLOAD_METADATA);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2655"})
    public void folderSearchByEnterKey() {
        search()
                .search(innerFolder1)
                .enter()
                .hover(SEARCH_RESULT)
                .openSearchResultItem(innerFolder1)
                .ensure(TITLE, text(innerFolder1))
                .ensure(HIGHLIGHTS, text("Found in name"))
                .ensure(PREVIEW, text(innerFolder2))
                .parent()
                .moveToSearchResultItem(innerFolder1, () -> new LibraryFolderAO(innerFolder1))
                .ensureAll(visible, SETTINGS, UPLOAD_METADATA);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2656"})
    public void negativeFolderSearch() {
        search()
                .click(PIPELINES)
                .search(innerFolder1)
                .enter()
                .validateSearchResults(0, "")
                .close()
                .search(innerFolder1.substring(0, innerFolder1.length()/2 - 1))
                .enter()
                .validateSearchResults(0, "")
                .close()
                .close();
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2675"})
    public void searchWithSpecialExpressions() {
        String folderWithExpression = innerFolder1 + "suffix";
        navigationMenu()
                .library()
                .cd(folder)
                .createFolder(folderWithExpression);
        home().sleep(2, MINUTES);
        search()
                .search(innerFolder1 + "*")
                .enter()
                .hover(SEARCH_RESULT)
                .sleep(2, SECONDS)
                .validateCountSearchResults(2)
                .ensure(FOLDERS, text("2 FOLDERS"))
                .ensure(SEARCH_RESULT, text(innerFolder1), text(folderWithExpression))
                .close()
                .close();
    }

    @Test(priority = 100)
    @TestCase(value = {"EPMCMBIBPC-2676"})
    public void searchAfterDeleting() {
        library()
                .removeNotEmptyFolder(folder)
                .sleep(5, MINUTES);
        home();
        search()
                .search(pipeline)
                .enter()
                .validateSearchResults(0, "")
                .search(configuration)
                .enter()
                .validateSearchResults(0, "")
                .search(innerFolder1)
                .enter()
                .validateSearchResults(0, "")
                .search(title)
                .enter()
                .validateSearchResults(0, "")
                .close()
                .close();
    }

    private GlobalSearchAO search() {
        return new NavigationMenuAO().search();
    }
}
