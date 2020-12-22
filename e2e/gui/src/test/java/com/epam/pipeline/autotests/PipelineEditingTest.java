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

import com.epam.pipeline.autotests.ao.PipelineCodeTabAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static java.util.concurrent.TimeUnit.SECONDS;

public class PipelineEditingTest extends AbstractBfxPipelineTest implements Navigation {

    private static final String FOLDER_NAME = "pipelineEditingFolder-" + Utils.randomSuffix();
    private static final String PIPELINE_NAME = "pipelineEditingPipe" + Utils.randomSuffix();
    private static final String NEW_FILE_NAME = "pipelineEditingFile-" + Utils.randomSuffix();
    private static final String RENAMED_FILE_NAME = "renamedPipelineEditingFile-" + Utils.randomSuffix();
    private static final String RENAMED_PIPELINE_NAME = "renamedPipelineEditingPipe-" + Utils.randomSuffix();

    @AfterClass(alwaysRun = true)
    @TestCase(value = {"EPMCMBIBPC-287", "EPMCMBIBPC-288"})
    public void cleanUp() {
        library()
                .removeNotEmptyFolder(FOLDER_NAME);
    }

    @Test(priority = 0)
    @TestCase(value = {"EPMCMBIBPC-284"})
    public void createFolderTest() {
        library()
                .createFolder(FOLDER_NAME);
    }

    @Test(dependsOnMethods = {"createFolderTest"})
    @TestCase(value = {"EPMCMBIBPC-285", "EPMCMBIBPC-231"})
    public void createPipelineFromPythonTemplateTest() {
        library()
                .cd(FOLDER_NAME)
                .createPipeline(Template.PYTHON, PIPELINE_NAME)
                .validatePipeline(PIPELINE_NAME);
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
        library()
                .cd(FOLDER_NAME)
                .clickOnPipeline(PIPELINE_NAME)
                .firstVersion()
                .codeTab()
                .createFile(NEW_FILE_NAME)
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
                .clickOnFile(RENAMED_FILE_NAME)
                .clickEdit()
                .clear()
                .fillWith(editedCode)
                .saveAndCommitWithMessage("Edited")
                .clickOnFile(RENAMED_FILE_NAME)
                .shouldContainInCode(editedCode)
                .close();
    }

    @Test(dependsOnMethods = {"editPipelineFileTest"})
    @TestCase(value = {"EPMCMBIBPC-291"})
    public void deletePipelineFileTest() {
        new PipelineCodeTabAO(PIPELINE_NAME)
                .deleteFile(RENAMED_FILE_NAME)
                .ensure(byText(RENAMED_FILE_NAME.toLowerCase() + ".py"), not(exist));
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
                .ensure(byText(RENAMED_PIPELINE_NAME), visible);
    }
}
