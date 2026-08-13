/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.ao.PipelineGraphTabAO.SectionRowAO;
import com.epam.pipeline.autotests.ao.PipelineGraphTabAO.TaskPropertiesPopupAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;

import java.util.function.Supplier;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.disappear;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.PipelineGraphTabAO.TypeCombobox.shouldContainTypes;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.combobox;
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
    private final String defaultTask = "Task";
    private final String aliasName = "task" + Utils.randomSuffix();

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
                .ensureVisible(SAVE, REVERT, LAYOUT, FIT, SHOW_LINKS, ZOOM_IN, ZOOM_OUT, FULLSCREEN, PROPERTIES)
                .ensureAll(disabled, SAVE, REVERT);
    }

    @Test(dependsOnMethods = {"validateWDLEditButtons"})
    @TestCase({"EPMCMBIBPC-608"})
    public void validateAddTaskPopup() {
        getFirstVersion(pipelineName)
                .graphTab()
                .editWorkflow()
                .createNewTask()
                .ensureVisible(ALIAS, INPUT_ADD, OUTPUT_ADD, COMMAND, DELETE)
                .click(RUNTIME)
                .ensureVisible(ANOTHER_DOCKER_IMAGE, ANOTHER_COMPUTE_NODE);
    }

    @Test(dependsOnMethods = {"validateAddTaskPopup"})
    @TestCase({"EPMCMBIBPC-642"})
    public void validateAnotherDockerImageCausesDockerImagesListAppearing() {
        taskPropertiesDialog()
                .enableAnotherDockerImage()
                .openDockerImagesCombobox()
                .ensureVisible(REGISTRY, GROUP, SEARCH, OK, CANCEL)
                .click(CANCEL, taskPropertiesPopupOf(pipelineName))
                .deleteAdditionalConfiguration("docker")
                .enableAnotherComputeNode()
                .selectValue(combobox("Compute node type or binding"), C.DEFAULT_INSTANCE);
    }

    @Test(dependsOnMethods = {"validateAnotherDockerImageCausesDockerImagesListAppearing"})
    @TestCase({"EPMCMBIBPC-610"})
    public void validateInputAddButton() {
        taskPropertiesDialog()
                .clickInputSectionAddButton()
                .ensureVisible(NAME, TYPE, VALUE, DELETE_ICON);
    }

    @Test(dependsOnMethods = {"validateInputAddButton"})
    @TestCase({"EPMCMBIBPC-614"})
    public void validateTypeDropDownList() {
        sectionRowInTaskPropertiesPopup()
                .openTypeCombobox()
                .also(shouldContainTypes("File", "String", "Int", "Boolean", "Float",
                        "Array", "Object", "Pair", "Map"))
                .close()
                .dropCurrentRow();
    }

    @Test(dependsOnMethods = {"validateTypeDropDownList"})
    @TestCase({"EPMCMBIBPC-618"})
    public void validateAddingParameterInTask() {
        taskPropertiesDialog()
                .setValue(ALIAS, aliasName)
                .enter()
                .click(INPUT_ADD);
        sectionRowInTaskPropertiesPopup()
                .setName("test_in")
                .setType("Int")
                .setValue("0")
                .close()
                .setCommand("pipe_log SUCCESS \"Running WDL pipeline\" \"test_in\"")
                .parent()
                .searchScatter("test_in")
                .searchLabel(aliasName)
                .ensure(SAVE, visible, enabled)
                .ensure(REVERT, visible, enabled);
    }

    @Test(dependsOnMethods = {"validateAddingParameterInTask"})
    @TestCase({"EPMCMBIBPC-620", "EPMCMBIBPC-1538"})
    public void checkThatDiagramChangingChangesCode() {
        String varName = "test_in";
        String varType = "Int";
        String varValue = "0";
        taskPropertiesDialog()
                .parent()
                .saveAndCommitWithMessage("commit by EPMCMBIBPC-620 test case")
                .ensure(modalWithTitle("Commit"), disappear)
                .codeTab()
                .clickOnFile(fileInPipeline)
                .ensureVisible(EDIT, CLOSE)
                .shouldContainInCode(String.format("task %s", defaultTask))
                .shouldContainInCode(String.format("%s = %s", varName, varValue))
                .shouldContainInCode(String.format("%s %s", varType, varName))
                .shouldContainInCode(String.format("call %s as %s", defaultTask, aliasName))
                .close();
    }

    @Test(dependsOnMethods = "validateAnotherDockerImageCausesDockerImagesListAppearing", priority = 1)
    @TestCase({"EPMCMBIBPC-643"})
    public void checkCodeAfterChangingImage() {
        getFirstVersion(pipelineName)
                .graphTab()
                .editWorkflow()
                .parent()
                .editTask(defaultTask)
                .click(RUNTIME)
                .enableAnotherDockerImage()
                .openDockerImagesCombobox()
                .selectRegistry(defaultRegistry)
                .selectGroup(defaultGroup)
                .selectTool(testingTool)
                .click(OK, taskPropertiesPopupOf(pipelineName))
                .parent()
                .saveAndCommitWithMessage("testing")
                .codeTab()
                .clickOnFile(fileInPipeline)
                .shouldContainInCode(testingTool)
                .close();
    }

    private Supplier<TaskPropertiesPopupAO> taskPropertiesPopupOf(final String pipelineName) {
        return () -> new TaskPropertiesPopupAO(new PipelineGraphTabAO(pipelineName));
    }

    private PipelineCodeTabAO getFirstVersion(String pipelineName) {
        return navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .codeTab();
    }

    private TaskPropertiesPopupAO taskPropertiesDialog() {
        return new TaskPropertiesPopupAO(new PipelineGraphTabAO(pipelineName));
    }

    private SectionRowAO<TaskPropertiesPopupAO> sectionRowInTaskPropertiesPopup() {
        return new SectionRowAO<>(new TaskPropertiesPopupAO(new PipelineGraphTabAO(pipelineName)));
    }
}
