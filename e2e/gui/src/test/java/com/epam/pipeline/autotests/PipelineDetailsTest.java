/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.ao.PipelineCodeTabAO;
import com.epam.pipeline.autotests.ao.PipelineHistoryTabAO;
import com.epam.pipeline.autotests.ao.PipelineRunFormAO;
import com.epam.pipeline.autotests.ao.StorageRulesTabAO;
import com.epam.pipeline.autotests.ao.StorageRulesTabAO.RuleAdditionPopupAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.ao.DocumentTabAO.fileWithName;
import static com.epam.pipeline.autotests.ao.LogAO.Status.LOADING;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_NEW_RULE;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.DOCKER_IMAGE;
import static com.epam.pipeline.autotests.ao.Primitive.DOWNLOAD;
import static com.epam.pipeline.autotests.ao.Primitive.RENAME;
import static com.epam.pipeline.autotests.ao.Primitive.UPLOAD;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class PipelineDetailsTest extends AbstractSeveralPipelineRunningTest implements Authorization {

    private static final String FOLDER_PREFIX = "folder_";
    private static final String FILE_PREFIX = "FILE_";
    private static final String RENAMED_FILE_NAME = "renamed_file.md";
    private static final String FILE_MASK_STRING = "FileMaskString";
    private static final String localFilePath = URI.create(C.DOWNLOAD_FOLDER + "/").resolve(RENAMED_FILE_NAME)
            .toString();
    private final String pipelineName = "pipeline-details-test-" + Utils.randomSuffix();
    private final String draftPipelineName = "shellpipe-draft-test" + Utils.randomSuffix();
    private final String pipelineFolder = FOLDER_PREFIX + pipelineName;
    private final String pipelineFile = FILE_PREFIX + pipelineName;
    // The GUI shows dates of the API server's clock, which may differ a little from this machine's clock
    private static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(2);
    private LocalDateTime pipelineCreatedAt;

    @AfterClass(alwaysRun = true)
    public void deleteDownloaded() {
        new File(localFilePath).delete();
    }

    @AfterClass(alwaysRun = true)
    public void removePipelines() {
        library().removePipelineIfExists(pipelineName)
                .removePipelineIfExists(draftPipelineName);
    }

    @BeforeClass
    public void createPipeline() {
        pipelineCreatedAt = LocalDateTime.now();
        library().createPipeline(Template.SHELL, pipelineName);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-339"})
    public void shouldCreateFolderInPipeline() {
        library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .codeTab()
                .createFolder(pipelineFolder)
                .shouldContainElement(pipelineFolder);
    }

    @Test(dependsOnMethods = {"shouldCreateFolderInPipeline"})
    @TestCase(value = {"EPMCMBIBPC-340"})
    public void shouldCreateFileInSubfolder() {
        codeTab()
                .clickOnFolder(pipelineFolder)
                .createFile(pipelineFile)
                .ensure(byText(pipelineFile), visible);
    }

    @Test(dependsOnMethods = {"shouldCreateFileInSubfolder"})
    @TestCase(value = {"EPMCMBIBPC-345"})
    public void documentTabShouldBeValid() {
        codeTab()
                .documentsTab()
                .ensure(UPLOAD, visible, enabled)
                .ensure(fileWithName("README.md"), visible)
                .documentsCountShouldBe(1)
                .ensureVisible(DELETE, RENAME, DOWNLOAD);
    }

    @Test(dependsOnMethods = {"documentTabShouldBeValid"})
    @TestCase(value = {"EPMCMBIBPC-346"})
    public void documentShouldBeRenamed() {
        documentsTab()
                .renameFile(RENAMED_FILE_NAME)
                .ensure(fileWithName(RENAMED_FILE_NAME), visible)
                .ensure(fileWithName("README.md"), not(exist))
                .documentsCountShouldBe(1);
    }

    @Test(dependsOnMethods = {"documentShouldBeRenamed"})
    @TestCase(value = {"EPMCMBIBPC-347"})
    public void shouldDownloadCorrectDocumentFile() {
        final String documentSource = documentsTab()
                .documentSource(RENAMED_FILE_NAME);
        documentsTab()
                .download()
                .sleep(6, SECONDS);

        assertEquals(Utils.readFile(localFilePath).replaceAll("\\n+$", ""),
                documentSource.replaceAll("\\n+$", ""));
    }

    @Test(dependsOnMethods = {"shouldDownloadCorrectDocumentFile"})
    @TestCase(value = {"EPMCMBIBPC-348"})
    public void shouldDeleteDocumentFile() {
        documentsTab()
                .delete()
                .shouldContainNoDocuments()
                .ensure(UPLOAD, visible);
    }

    @Test(dependsOnMethods = {"shouldDeleteDocumentFile"})
    @TestCase(value = {"EPMCMBIBPC-349"})
    public void shouldUploadFile() {
        final Path path = Paths.get(localFilePath);
        final String uploadedFileName = path.getFileName().toString();
        documentsTab()
                .upload(path)
                .ensure(fileWithName(uploadedFileName), visible);
    }

    @Test(dependsOnMethods = {"shouldUploadFile"})
    @TestCase(value = {"EPMCMBIBPC-350"})
    public void shouldHaveEmptyHistory() {
        documentsTab()
                .historyTab()
                .shouldHaveEmptyHistory();
    }

    @Test(dependsOnMethods = {"shouldHaveEmptyHistory"})
    @TestCase(value = {"EPMCMBIBPC-350"})
    public void shouldHaveHistoryEntry() {
        final PipelineRunFormAO runForm = historyTab()
                .runPipeline()
                .setPriceType(ON_DEMAND);
        final String dockerImage = runForm.get(DOCKER_IMAGE).shouldBe(visible).shouldNotBe(empty).getValue();
        runForm.launch(this);

        navigateToPipelineHistory()
                .recordsCountShouldBe(1)
                .shouldContainActiveRun(pipelineName, getLastRunId(), "draft-.{8}", 30,
                        getUserNameByAccountLogin(C.LOGIN));

        runsMenu()
                .sleep(3, SECONDS)
                .showLog(getLastRunId())
                .shouldHaveStatus(LOADING)
                .waitForCompletion();

        navigateToPipelineHistory()
                .shouldContainStoppedRun(pipelineName, getLastRunId(), "draft-.{8}", 200,
                        getUserNameByAccountLogin(C.LOGIN))
                .shouldContainCompletedRunDetails(dockerImage.substring(dockerImage.lastIndexOf('/') + 1));
    }

    @Test(dependsOnMethods = {"shouldHaveHistoryEntry"})
    @TestCase(value = {"EPMCMBIBPC-353"})
    public void storageRulesTabShouldBeValid() {
        final LocalDateTime createdDate = historyTab()
                .storageRulesTab()
                .ensure(ADD_NEW_RULE, visible, enabled)
                .shouldContainRulesTable()
                .rulesCountShouldBe(1)
                .shouldContainRule(0, "*")
                .ruleCreatedDate("*");

        assertCreatedBetween(createdDate, pipelineCreatedAt);
    }

    @Test(dependsOnMethods = {"storageRulesTabShouldBeValid"})
    @TestCase(value = {"EPMCMBIBPC-354"})
    public void shouldCreateStorageRule() {
        final LocalDateTime ruleCreationStart = LocalDateTime.now();
        List<String> rules =
                storageRulesTab()
                        .openAddNewStorageDialog()
                        .pipelineFieldShouldBeDisabled(pipelineName)
                        .setFileMask(FILE_MASK_STRING)
                        .enableMoveToSTS()
                        .ok()
                        .sleep(2, SECONDS)
                        .rulesCountShouldBe(2)
                        .rules();

        assertTrue(rules.contains(FILE_MASK_STRING));
        assertCreatedBetween(storageRulesTab().ruleCreatedDate(FILE_MASK_STRING), ruleCreationStart);
    }

    @Test(dependsOnMethods = {"shouldCreateStorageRule"})
    @TestCase(value = {"EPMCMBIBPC-356"})
    public void shouldNotCreateACopyOfRule() {
        storageRulesTab()
                .addNewStorageRule("*")
                .messageShouldAppear("Your operation has been aborted due to SQL error.");
        Utils.getPopupByTitle("Create new rule").shouldBe(visible);

        addRulePopupAO()
                .cancel();
    }

    @Test(dependsOnMethods = {"shouldNotCreateACopyOfRule"})
    @TestCase(value = {"EPMCMBIBPC-355"})
    public void shouldDeleteStorageRule() {
        storageRulesTab()
                .deleteStorageRule(FILE_MASK_STRING)
                .rulesCountShouldBe(1)
                .shouldContainRule(0, "*");
    }

    @Test(dependsOnMethods = {"shouldDeleteStorageRule"})
    @TestCase(value = {"EPMCMBIBPC-1629"})
    public void validateViewDraftVersionsPipelineHistory() {
        String releaseVersion = "release_version";

        String firstVersion = navigationMenu()
                .library()
                .createPipeline(Template.SHELL, draftPipelineName)
                .clickOnPipeline(draftPipelineName)
                .firstVersion()
                .getVersion();

        runPipeline(draftPipelineName, this);
        runsMenu()
                .stopRun(getLastRunId());

        library()
                .clickOnPipeline(draftPipelineName)
                .releaseFirstVersion(releaseVersion);

        library()
                .clickOnPipeline(draftPipelineName)
                .sleep(2, SECONDS)
                .runFirstVersion()
                .launch(this);
        runsMenu()
                .stopRun(getLastRunId());

        String thirdVersion = library()
                .clickOnPipeline(draftPipelineName)
                .firstVersion()
                .codeTab()
                .clearAndFillPipelineFile(
                        Utils.getFileNameFromPipelineName(draftPipelineName, "sh"),
                        " this is a new text to set new version"
                )
                .sleep(1, SECONDS)
                .documentsTab()
                .getVersion();

        runPipeline(draftPipelineName,this);
        runsMenu()
                .stopRun(getLastRunId());

        library()
                .clickOnPipeline(draftPipelineName)
                .firstVersion()
                .historyTab()
                .runVersionsShouldBe("draft-" + thirdVersion)
                .viewAllVersions()
                .runVersionsShouldBe("draft-" + firstVersion, releaseVersion, "draft-" + thirdVersion);
    }

    private void runPipeline(String pipelineName, PipelineDetailsTest pipelineDetailsTest) {
        library()
                .clickOnPipeline(pipelineName)
                .sleep(2, SECONDS)
                .firstVersion()
                .runPipeline()
                .launch(pipelineDetailsTest);
    }

    private PipelineHistoryTabAO navigateToPipelineHistory() {
        return navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .historyTab();
    }

    private PipelineCodeTabAO codeTab() {
        return new PipelineCodeTabAO(pipelineName);
    }

    private DocumentTabAO documentsTab() {
        return new DocumentTabAO(pipelineName);
    }

    private PipelineHistoryTabAO historyTab() {
        return new PipelineHistoryTabAO(pipelineName);
    }

    private StorageRulesTabAO storageRulesTab() {
        return new StorageRulesTabAO(pipelineName);
    }

    private RuleAdditionPopupAO addRulePopupAO() {
        return new RuleAdditionPopupAO(new StorageRulesTabAO(pipelineName));
    }

    private void assertCreatedBetween(final LocalDateTime createdDate, final LocalDateTime from) {
        final LocalDateTime now = LocalDateTime.now();
        assertTrue(!createdDate.isBefore(from.minus(CLOCK_TOLERANCE))
                        && !createdDate.isAfter(now.plus(CLOCK_TOLERANCE)),
                String.format("Created date %s is not between %s and %s", createdDate, from, now));
    }
}
