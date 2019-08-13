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

import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.LogAO.Status.SUCCESS;
import static com.epam.pipeline.autotests.ao.LogAO.logMessage;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;

public class PipelineFromTemplateTest extends AbstractAutoRemovingPipelineRunningTest {
    @AfterMethod(alwaysRun = true)
    @Override
    public void removePipeline() {
        super.removePipeline();
    }

    @AfterMethod(alwaysRun = true)
    @Override
    public void removeNode() {
        super.removeNode();
    }

    @Test
    @TestCase("EPMCMBIBPC-228")
    public void wdl() {
        pipelineFromTemplateFinishesCorrectly(Template.WDL, "Running WDL pipeline");
    }

    @Test
    @TestCase("EPMCMBIBPC-97")
    public void shell() {
        pipelineFromTemplateFinishesCorrectly(Template.SHELL, "Running shell pipeline");
    }

    @Test
    @TestCase("EPMCMBIBPC-541")
    public void python() {
        pipelineFromTemplateFinishesCorrectly(Template.PYTHON, "Running python pipeline");
    }

    @Test
    @TestCase("EPMCMBIBPC-542")
    public void luigi() {
        pipelineFromTemplateFinishesCorrectly(Template.LUIGI, "Running luigi pipeline");
    }

    private void pipelineFromTemplateFinishesCorrectly(final Template template, final String expectedMessage) {
        navigationMenu()
                .createPipeline(template, getPipelineName())
                .firstVersion()
                .runPipeline()
                .launch(this)
                .showLog(getRunId())
                .waitForCompletion()
                .click(taskWithName("Task"))
                .ensure(logMessage(expectedMessage), visible)
                .ensure(STATUS, SUCCESS.reached);

        runsMenu()
                .completedRuns()
                .showLog(getRunId())
                .ensure(taskWithName(getPipelineName()), SUCCESS.reached);
    }
}
