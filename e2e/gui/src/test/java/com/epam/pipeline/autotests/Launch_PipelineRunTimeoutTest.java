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

import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessage;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.LogAO.pipelineLink;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.TIMEOUT;
import static com.epam.pipeline.autotests.utils.Json.editingConfiguration;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Launch_PipelineRunTimeoutTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final String SLEEP_SHELL = "/sleep2m.sh";
    private static final String TIMEOUT_MINUTES = "1";

    @Test
    @TestCase(value = {"EPMCMBIBPC-376"})
    public void preparePipelineAndValidate() {
        navigationMenu()
                .createPipeline(Template.SHELL, getPipelineName())
                .firstVersion()
                .codeTab()
                .clickOnFile("config.json")
                // the next test launches this configuration as it is, and the platform's default instance type
                // may not be allowed, so the instance type is set too
                .editFile(editingConfiguration("default", configuration -> configuration
                        .put("timeout", Integer.parseInt(TIMEOUT_MINUTES))
                        .put("instance_size", C.DEFAULT_INSTANCE)))
                .saveAndCommitWithMessage("test: Change timeout")
                .sleep(2, SECONDS)
                .runPipeline()
                .ensure(TIMEOUT, attribute("value", TIMEOUT_MINUTES));
    }

    @Test(dependsOnMethods = {"preparePipelineAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-396"})
    public void pipelineShouldStartWithTimeout() {
        String pipelineFileName = Utils.getFileNameFromPipelineName(getPipelineName(), "sh");
        navigationMenu()
                .library()
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .codeTab()
                .clearAndFillPipelineFile(pipelineFileName, Utils.readResourceFully(SLEEP_SHELL))
                .sleep(2, SECONDS)
                .runPipeline()
		.setPriceType(ON_DEMAND)
                .launch(this)
                .showLogForce(getRunId())
                .ensure(pipelineLink(), text(getPipelineName()))
                .waitFor(LogAO.Status.FAILURE)
                .waitForTaskOfFinishedRun(getPipelineName())
                .click(taskWithName(getPipelineName()))
                .ensure(log(), containsMessage("Timeout was elapsed"))
                .ensure(taskWithName("Task1"), not(exist));
    }
}
