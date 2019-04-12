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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.hasValue;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.*;

public class WDLScatterEditorTest
        extends AbstractBfxPipelineTest
        implements Navigation {

    private final String pipelineName = "wdl-editor-test-pipeline-" + Utils.randomSuffix();
    private final String fileInPipeline = Utils.getFileNameFromPipelineName(pipelineName, "wdl");

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
    @TestCase({"EPMCMBIBPC-624"})
    public void addScatterPopupValidation() {
        getFirstVersion(pipelineName)
                .graphTab()
                .openAddScatterDialog()
                .parent()
                .clickScatter("scatter")
                .ensureVisible(ADD_TASK, DELETE, INPUT_PANEL)
                .cancel();
    }

    @Test(dependsOnMethods = "addScatterPopupValidation")
    @TestCase({"EPMCMBIBPC-626"})
    public void checkAddButtonForScatterMenu() {
        getFirstVersion(pipelineName)
                .graphTab()
                .openAddScatterDialog()
                .parent()
                .clickScatter("scatter")
                .clickInputSectionAddButton()
                .ensure(NAME, visible, enabled)
                .ensure(TYPE, visible, disabled)
                .ensure(VALUE, visible, disabled)
                .ensureAll(not(empty), NAME, TYPE)
                .ensure(TYPE, hasValue("ScatterItem"))
                .close()
                .cancel();
    }

    @Test(dependsOnMethods = "checkAddButtonForScatterMenu", priority = 1)
    @TestCase({"EPMCMBIBPC-628"})
    public void validationOfAddingParameterInScatter() {
        final String inputParameterName = "test_in";
        getFirstVersion(pipelineName)
                .graphTab()
                .openAddScatterDialog()
                .parent()
                .clickScatter("scatter")
                .clickInputSectionAddButton()
                .setName(inputParameterName)
                .close()
                .parent()
                .searchScatter(inputParameterName)
                .ensure(SAVE, visible, enabled)
                .ensure(REVERT, visible, enabled);
    }

    @Test(dependsOnMethods = "validationOfAddingParameterInScatter", priority = 1)
    @TestCase({"EPMCMBIBPC-636"})
    public void checkIfDiagramChangesScatterCode() {
        final String inputParameterName = "test_in";
        final String scatterCodeLine = String.format("scatter (%s in ) {", inputParameterName);
        graphTab(pipelineName)
                .saveAndCommitWithMessage("commit by EPMCMBIBPC-636 test case")
                .codeTab()
                .clickOnFile(fileInPipeline)
                .ensureVisible(EDIT, CLOSE)
                .shouldContainInCode(scatterCodeLine)
                .close();
    }

    private PipelineCodeTabAO getFirstVersion(final String pipelineName) {
        return navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .codeTab();
    }

    private PipelineGraphTabAO graphTab(final String pipelineName) {
        return new PipelineGraphTabAO(pipelineName);
    }

    private PipelineGraphTabAO.SectionRowAO<PipelineGraphTabAO.ScatterAdditionPopupAO> sectionRowInScatterAdditionPopup() {
        return new PipelineGraphTabAO.SectionRowAO<>(new PipelineGraphTabAO.ScatterAdditionPopupAO(new PipelineGraphTabAO(pipelineName)));
    }
}
