/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.ao.PipelineGraphTabAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import java.util.function.Supplier;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.disappears;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.PipelineGraphTabAO.TypeCombobox.shouldContainTypes;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.modalWithTitle;

public class WDLTaskEditorTest
        extends AbstractBfxPipelineTest
        implements Navigation {

    private final String pipelineName = "wdl-editor-test-pipeline" + Utils.randomSuffix();
    private final String fileInPipeline = Utils.getFileNameFromPipelineName(pipelineName, "wdl");
    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String pipeline1538 = "pipeline-1538-" + Utils.randomSuffix();

    @BeforeClass
    public void createPipeline() {
        navigationMenu()
                .library()
                .createPipeline(Template.WDL, pipelineName);
    }

    @AfterClass(alwaysRun = true)
    public void removePipeline() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .removePipelineIfExists(pipelineName)
                .removePipelineIfExists(pipeline1538);
    }

    @Test
    @TestCase({"EPMCMBIBPC-588"})
    public void validateWDLEditButtons() {
        getFirstVersion(pipelineName)
                .graphTab()
                .ensureVisible(SAVE, REVERT, LAYOUT, FIT, SHOW_LINKS, ADD_SCATTER, ADD_TASK);
    }

    @Test(dependsOnMethods = {"validateWDLEditButtons"})
    @TestCase({"EPMCMBIBPC-608"})
    public void validateAddTaskPopup() {
        getFirstVersion(pipelineName)
                .graphTab()
                .openAddTaskDialog()
                .ensureVisible(NAME, INPUT_ADD, OUTPUT_ADD, ANOTHER_DOCKER_IMAGE, COMMAND, ADD, CANCEL);
    }

    @Test(dependsOnMethods = {"validateAddTaskPopup"})
    @TestCase({"EPMCMBIBPC-642"})
    public void validateAnotherDockerImageCausesDockerImagesListAppearing() {
        taskAdditionDialog()
                .enableAnotherDockerImage()
                .openDockerImagesCombobox()
                .ensureVisible(REGISTRY, GROUP, SEARCH, OK, CANCEL)
                .click(CANCEL, taskAdditionPopupOf(pipelineName))
                .disableAnotherDockerImage();
    }

    @Test(dependsOnMethods = {"validateAnotherDockerImageCausesDockerImagesListAppearing"})
    @TestCase({"EPMCMBIBPC-610"})
    public void validateInputAddButton() {
        taskAdditionDialog()
                .clickInputSectionAddButton()
                .ensureVisible(NAME, TYPE, VALUE, DELETE_ICON);
    }

    @Test(dependsOnMethods = {"validateInputAddButton"})
    @TestCase({"EPMCMBIBPC-614"})
    public void validateTypeDropDownList() {
        sectionRowInTaskAdditionPopup()
                .openTypeCombobox()
                .also(shouldContainTypes("String", "File", "Int", "Boolean", "Float", "Object"))
                .close()
                .dropCurrentRow();
    }

    @Test(dependsOnMethods = {"validateTypeDropDownList"})
    @TestCase({"EPMCMBIBPC-618"})
    public void validateAddingParameterInTask() {
        String taskName = generateWdlTaskName();
        taskAdditionDialog()
                .setName(taskName)
                .clickInputSectionAddButton()
                .setName("test_in")
                .setType("Int")
                .setValue("0")
                .close()
                .ok()
                .ensure(SAVE, visible, enabled);
    }

    @Test(dependsOnMethods = {"validateAddingParameterInTask"})
    @TestCase({"EPMCMBIBPC-620"})
    public void checkThatDiagramChangingChangesCode() {
        String taskName = generateWdlTaskName();
        String varName = "test_in";
        String varType = "Int";
        String varValue = "0";
        getFirstVersion(pipelineName)
                .graphTab()
                .openAddTaskDialog()
                .clickInputSectionAddButton()
                .setName(varName)
                .setType(varType)
                .setValue(varValue)
                .close()
                .setName(taskName)
                .ok()
                .saveAndCommitWithMessage("commit by EPMCMBIBPC-620 test case")
                .codeTab()
                .clickOnFile(fileInPipeline)
                .ensureVisible(EDIT, CLOSE)
                .shouldContainInCode(String.format("task %s", taskName))
                .shouldContainInCode(String.format("%s %s = %s", varType, varName, varValue))
                .shouldContainInCode(String.format("call %s", taskName))
                .close();
    }

    @Test(dependsOnMethods = "validateAnotherDockerImageCausesDockerImagesListAppearing", priority = 1)
    @TestCase({"EPMCMBIBPC-643"})
    public void checkCodeAfterChangingImage() {
        String taskName = generateWdlTaskName();
        getFirstVersion(pipelineName)
                .graphTab()
                .openAddTaskDialog()
                .setName(taskName)
                .enableAnotherDockerImage()
                .openDockerImagesCombobox()
                .selectRegistry(defaultRegistry)
                .selectGroup(defaultGroup)
                .selectTool(testingTool)
                .click(OK, taskAdditionPopupOf(pipelineName))
                .ok()
                .saveAndCommitWithMessage("testing")
                .codeTab()
                .clickOnFile(fileInPipeline)
                .shouldContainInCode(testingTool)
                .close();
    }

    @Test(priority = 10)
    @TestCase({"EPMCMBIBPC-1538"})
    public void closingPopupAfterWdlPipelineCommit() {
        final String taskName = "taskName";
        final String taskNewName = "taskNewName";

        open(C.ROOT_ADDRESS);
        library()
                .createPipeline(Template.WDL, pipeline1538)
                .clickOnPipeline(pipeline1538)
                .firstVersion()
                .graphTab()
                .openAddTaskDialog()
                .setName(taskName)
                .clickInputSectionAddButton()
                .setName("test_in")
                .setType("Int")
                .setValue("0")
                .close()
                .ok()
                .clickScatter(taskName)
                .edit()
                .setValue(ALIAS, taskNewName)
                .ok()
                .saveAndCommitWithMessage("commit message")
                .ensure(modalWithTitle("Commit"), disappears);
    }

    private Supplier<PipelineGraphTabAO.TaskAdditionPopupAO> taskAdditionPopupOf(final String pipelineName) {
        return () -> new PipelineGraphTabAO.TaskAdditionPopupAO(new PipelineGraphTabAO(pipelineName));
    }

    private PipelineCodeTabAO getFirstVersion(String pipelineName) {
        return navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .codeTab();
    }

    private PipelineGraphTabAO.TaskAdditionPopupAO taskAdditionDialog() {
        return new PipelineGraphTabAO.TaskAdditionPopupAO(new PipelineGraphTabAO(pipelineName));
    }

    private PipelineGraphTabAO.SectionRowAO<PipelineGraphTabAO.TaskAdditionPopupAO> sectionRowInTaskAdditionPopup() {
        return new PipelineGraphTabAO.SectionRowAO<>(new PipelineGraphTabAO.TaskAdditionPopupAO(new PipelineGraphTabAO(pipelineName)));
    }

    private String generateWdlTaskName() {
        return "task" + Utils.randomSuffix();
    }
}
