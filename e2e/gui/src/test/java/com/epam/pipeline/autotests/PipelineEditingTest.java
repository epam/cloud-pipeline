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

import com.epam.pipeline.autotests.ao.PipelineCodeTabAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.disappear;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.withText;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.modalWithTitle;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class PipelineEditingTest extends AbstractBfxPipelineTest implements Navigation {

    private static final String FOLDER_NAME = "pipelineEditingFolder-" + Utils.randomSuffix();
    private static final String PIPELINE_NAME = "pipelineEditingPipe" + Utils.randomSuffix();
    private static final String NEW_FILE_NAME = "pipelineEditingFile-" + Utils.randomSuffix();
    private static final String RENAMED_FILE_NAME = "renamedPipelineEditingFile-" + Utils.randomSuffix();
    private static final String RENAMED_PIPELINE_NAME = "renamedPipelineEditingPipe-" + Utils.randomSuffix();
    private static final String LIBRARY_PIPELINE_NAME = "pipelineEditingLibraryPipe" + Utils.randomSuffix();

    @AfterClass(alwaysRun = true)
    @TestCase(value = {"EPMCMBIBPC-287", "EPMCMBIBPC-288"})
    public void cleanUp() {
        library()
                .removeNotEmptyFolder(FOLDER_NAME);
    }

    @AfterClass(alwaysRun = true)
    public void removeLibraryPipeline() {
        library()
                .removePipelineIfExists(LIBRARY_PIPELINE_NAME);
    }

    @Test(priority = 0)
    @TestCase(value = {"EPMCMBIBPC-284"})
    public void createFolderTest() {
        library()
                .createFolder(FOLDER_NAME)
                .validateFolderInTreeAndList(FOLDER_NAME);
    }

    @Test(priority = 0)
    @TestCase(value = {"EPMCMBIBPC-285"})
    public void createPipelineFromPythonTemplateInLibraryTest() {
        library()
                .createPipeline(Template.PYTHON, LIBRARY_PIPELINE_NAME)
                .validatePipelineInTreeAndList(LIBRARY_PIPELINE_NAME);
    }

    @Test(dependsOnMethods = {"createFolderTest"})
    @TestCase(value = {"EPMCMBIBPC-231"})
    public void createPipelineFromPythonTemplateTest() {
        library()
                .cd(FOLDER_NAME)
                .createPipeline(Template.PYTHON, PIPELINE_NAME)
                .validatePipelineInFolder(FOLDER_NAME, PIPELINE_NAME);
    }

    @Test(dependsOnMethods = {"createPipelineFromPythonTemplateTest"})
    @TestCase(value = {"EPMCMBIBPC-286"})
    public void pythonPipelineValidationTest() {
        new PipelinesLibraryAO()
                .clickOnPipeline(PIPELINE_NAME)
                .ensureVisible(SETTINGS, GIT_REPOSITORY)
                .firstVersion()
                .ensureVisible(DOCUMENTS_TAB, CODE_TAB, CONFIGURATION_TAB, HISTORY_TAB, STORAGE_RULES_TAB, RUN)
                .codeTab()
                .ensureVisible(CREATE_FOLDER, NEW_FILE, UPLOAD, RENAME, DELETE)
                .ensure(byText(PIPELINE_NAME.toLowerCase() + ".py"), visible)
                .ensure(byText("config.json"), visible);
    }

    @Test(dependsOnMethods = {"pythonPipelineValidationTest"})
    @TestCase(value = {"EPMCMBIBPC-295"})
    public void createPipelineFileTest() {
        final String creationMessage = format("Creating file '%s'", NEW_FILE_NAME);
        library()
                .cd(FOLDER_NAME)
                .clickOnPipeline(PIPELINE_NAME)
                .firstVersion()
                .codeTab()
                .openCreateFileDialog()
                .typeInField(NEW_FILE_NAME)
                .ok()
                .messageShouldAppear(creationMessage)
                .ensure(withText(creationMessage), disappear)
                .ensure(byText(NEW_FILE_NAME), visible);
    }

    @Test(dependsOnMethods = {"createPipelineFileTest"})
    @TestCase(value = {"EPMCMBIBPC-290"})
    public void editPipelineFileNameTest() {
        new PipelineCodeTabAO(PIPELINE_NAME)
                .clickOnRenameFileButton(NEW_FILE_NAME)
                .typeInField(RENAMED_FILE_NAME)
                .ok()
                .ensure(byText(RENAMED_FILE_NAME), visible);
    }

    @Test(dependsOnMethods = {"editPipelineFileNameTest"})
    @TestCase(value = {"EPMCMBIBPC-289"})
    public void editPipelineFileTest() {
        final String editedCode = "Edited code";
        new PipelineCodeTabAO(PIPELINE_NAME)
                .clearAndFillPipelineFile(RENAMED_FILE_NAME, editedCode)
                .sleep(4, SECONDS)
                .waitUntilRenameButtonAppears(RENAMED_FILE_NAME)
                .clickOnFile(RENAMED_FILE_NAME)
                .shouldContainInCode(editedCode)
                .close();
    }

    @Test(dependsOnMethods = {"editPipelineFileTest"})
    @TestCase(value = {"EPMCMBIBPC-291"})
    public void deletePipelineFileTest() {
        new PipelineCodeTabAO(PIPELINE_NAME)
                .deleteFile(RENAMED_FILE_NAME)
                .ensure(byText(RENAMED_FILE_NAME), not(exist))
                .shouldContainFile("config.json");
    }

    @Test(dependsOnMethods = {"deletePipelineFileTest"})
    @TestCase(value = {"EPMCMBIBPC-292"})
    public void editPipelineTest() {
        library()
                .sleep(2, SECONDS)
                .clickOnPipeline(PIPELINE_NAME)
                .sleep(2, SECONDS)
                .clickEditButton()
                .rename(RENAMED_PIPELINE_NAME)
                .save()
                .ensure(modalWithTitle("Edit pipeline info"), disappear)
                .ensure(byText(RENAMED_PIPELINE_NAME), visible);
    }
}
