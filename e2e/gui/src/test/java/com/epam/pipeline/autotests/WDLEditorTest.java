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
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.ALIAS;
import static com.epam.pipeline.autotests.ao.Primitive.ANOTHER_DOCKER_IMAGE;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.COMMAND;
import static com.epam.pipeline.autotests.ao.Primitive.INPUT_ADD;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.OUTPUT_ADD;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;

public class WDLEditorTest extends AbstractBfxPipelineTest implements Navigation {
    private final String pipelineName = "wdl-editor-test-pipeline" + Utils.randomSuffix();
    private final String fileInPipeline = Utils.getFileNameFromPipelineName(pipelineName, "wdl");
    private final String logSuccessMessage = "pipe_log SUCCESS \"Running WDL pipeline\" \"%s\"";
    private final String commitMessage = "testing";
    private final String taskName = "testing_task";

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
                .setName(taskName)
                .setCommand(String.format(logSuccessMessage, taskName))
                .ok()
                .ensure(SAVE, visible, enabled)
                .revert()
                .ensure(SAVE, visible, disabled);
    }

    @Test
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

    @Test
    @TestCase({"EPMCMBIBPC-637"})
    public void validateAddTaskInScatter() {
        getFirstVersion(pipelineName)
                .graphTab()
                .clickScatter("workflow")
                .openAddTaskDialog()
                .setName(taskName)
                .setCommand(String.format(logSuccessMessage, taskName))
                .ok()
                .searchLabel(taskName);
    }

    @Test
    @TestCase({"EPMCMBIBPC-638"})
    public void checkDiagramChangeForScatter() {
        getFirstVersion(pipelineName)
                .clickOnFile(fileInPipeline)
                .clickEdit()
                .clear()
                .fillWith(Utils.readResourceFully("/extraScatter.wdl"))
                .deleteExtraBrackets()
                .saveAndCommitWithMessage(commitMessage)
                .graphTab()
                .searchScatter("scattername");
    }

    @Test
    @TestCase({"EPMCMBIBPC-640"})
    public void checkDiagramChangeForScatterWithTask() {
        getFirstVersion(pipelineName)
                .clickOnFile(fileInPipeline)
                .clickEdit()
                .clear()
                .fillWith(Utils.readResourceFully("/extraScatterTask.wdl"))
                .deleteExtraBrackets()
                .saveAndCommitWithMessage(commitMessage)
                .graphTab()
                .searchScatter("myscatter")
                .searchLabel("MyTask");
    }

    @Test
    @TestCase({"EPMCMBIBPC-641"})
    public void validateTaskEdit() {
        getFirstVersion(pipelineName)
                .graphTab()
                .clickScatter("HelloWorld_print")
                .edit()
                .ensureVisible(NAME, ALIAS, INPUT_ADD, OUTPUT_ADD, ANOTHER_DOCKER_IMAGE, COMMAND, SAVE, CANCEL)
                .cancel();
    }

    @Test
    @TestCase({"EPMCMBIBPC-650"})
    public void checkJsonAfterChangingParameters() {
        String varName = "somevariable";
        String varType = "Int";
        getFirstVersion(pipelineName)
                .graphTab()
                .clickScatter("workflow")
                .editWorkflow()
                .clickInputSectionAddButton()
                .setName(varName)
                .setType(varType)
                .close()
                .ok()
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
