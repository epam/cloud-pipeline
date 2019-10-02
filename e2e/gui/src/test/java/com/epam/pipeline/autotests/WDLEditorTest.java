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
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.hidden;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_TASK;
import static com.epam.pipeline.autotests.ao.Primitive.ALIAS;
import static com.epam.pipeline.autotests.ao.Primitive.ANOTHER_DOCKER_IMAGE;
import static com.epam.pipeline.autotests.ao.Primitive.COMMAND;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.INPUT_ADD;
import static com.epam.pipeline.autotests.ao.Primitive.INPUT_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.OUTPUT_ADD;
import static com.epam.pipeline.autotests.ao.Primitive.REVERT;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.fieldWithLabel;
import static java.util.concurrent.TimeUnit.SECONDS;

public class WDLEditorTest extends AbstractBfxPipelineTest implements Navigation {
    private final String pipelineName = "wdl-editor-test-pipeline" + Utils.randomSuffix();
    private final String fileInPipeline = Utils.getFileNameFromPipelineName(pipelineName, "wdl");
    private final String commitMessage = "testing";
    private final String taskName = "testing_task";
    private final String defaultTask = "workflowTask";

    @BeforeClass
    public void createPipeline() {
        navigationMenu()
                .library()
                .createPipeline(Template.WDL, pipelineName);
    }

    @AfterClass
    public void removePipeline() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .removePipeline(pipelineName);
    }

    @Test
    @TestCase({"EPMCMBIBPC-639"})
    public void checkRevertButton() {
        getFirstVersion(pipelineName)
                .graphTab()
                .openAddTaskDialog()
                .parent()
                .clickTask(defaultTask)
                .setValue(ALIAS, taskName)
                .ensure(SAVE, visible, enabled)
                .ensure(REVERT, visible, enabled)
                .parent()
                .clickLabel(taskName)
                .revert()
                .ensure(fieldWithLabel(taskName), hidden)
                .ensure(SAVE, visible, disabled)
                .ensure(REVERT, visible, disabled);
    }

    @Test(priority = 1)
    @TestCase({"EPMCMBIBPC-621"})
    public void checkDiagramChange() {
        getFirstVersion(pipelineName)
                .clickOnFile(fileInPipeline)
                .clickEdit()
                .clear()
                .fillWith(Utils.readResourceFully("/extraTask.wdl"))
                .deleteExtraBrackets()
                .saveAndCommitWithMessage(commitMessage)
                .graphTab()
                .searchLabel("MyTask");
    }

    @Test(priority = 0)
    @TestCase({"EPMCMBIBPC-637"})
    public void validateAddTaskInScatter() {
        getFirstVersion(pipelineName)
                .graphTab()
                .openAddScatterDialog()
                .parent()
                .clickScatter("scatter")
                .click(ADD_TASK)
                .parent()
                .searchLabel("scatterTask")
                .searchScatter("scatter")
                .revert();
    }

    @Test(priority = 1)
    @TestCase({"EPMCMBIBPC-638"})
    public void checkDiagramChangeForScatter() {
        getFirstVersion(pipelineName)
                .clickOnFile(fileInPipeline)
                .clickEdit()
                .clear()
                .fillWith(Utils.readResourceFully("/extraScatter.wdl"))
                .deleteExtraBrackets()
                .saveAndCommitWithMessage(commitMessage)
                .sleep(2, SECONDS)
                .graphTab()
                .sleep(3, SECONDS)
                .searchScatter("scattername");
    }

    @Test(priority = 1)
    @TestCase({"EPMCMBIBPC-640"})
    public void checkDiagramChangeForScatterWithTask() {
        getFirstVersion(pipelineName)
                .clickOnFile(fileInPipeline)
                .clickEdit()
                .clear()
                .fillWith(Utils.readResourceFully("/extraScatterTask.wdl"))
                .deleteExtraBrackets()
                .saveAndCommitWithMessage(commitMessage)
                .sleep(2, SECONDS)
                .graphTab()
                .sleep(2, SECONDS)
                .searchScatter("myscatter")
                .searchLabel("MyTask");
    }

    @Test
    @TestCase({"EPMCMBIBPC-641"})
    public void validateTaskEdit() {
        getFirstVersion(pipelineName)
                .graphTab()
                .clickLabel("HelloWorld_print")
                .edit()
                .ensureVisible(ALIAS, INPUT_ADD, INPUT_PANEL, OUTPUT_ADD, ANOTHER_DOCKER_IMAGE, COMMAND, DELETE)
                .parent()
                .revert();
    }

    @Test
    @TestCase({"EPMCMBIBPC-650"})
    public void checkJsonAfterChangingParameters() {
        final String varName = "somevariable";
        final String varType = "Int";
        final String varValue = "111";
        getFirstVersion(pipelineName)
                .graphTab()
                .clickLabel("workflow")
                .editWorkflow()
                .clickInputSectionAddButton()
                .setName(varName)
                .setType(varType)
                .setValue(varValue)
                .close()
                .parent()
                .saveAndChangeJsonWithMessage(commitMessage)
                .codeTab()
                .clickOnFile("config.json")
                .shouldContainInCode(varName)
                .close();
    }

    private PipelineCodeTabAO getFirstVersion(String pipelineName) {
        return navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .codeTab();
    }
}
