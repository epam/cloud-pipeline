/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import static com.codeborne.selenide.Condition.visible;
import com.epam.pipeline.autotests.ao.LogAO;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.SECONDS;

public class Launch_PipelineRunTimeoutTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final String CONFIG_JSON = "/timeout.json";
    private static final String SLEEP_SHELL = "/sleep2m.sh";
    private static final String TASK_NAME = "Task1";
    private String testPipeline = "";
    private String pipelineFileName = "";

    @Test
    @TestCase(value = {"EPMCMBIBPC-376"})
    public void preparePipelineAndValidate() {
        navigationMenu()
                .createPipeline(Template.SHELL, testPipeline = getPipelineName());
        pipelineFileName = Utils.getFileNameFromPipelineName(testPipeline, "sh");
        library()
                .clickOnPipeline(testPipeline)
                .firstVersion()
                .codeTab()
                .clearAndFillPipelineFile("config.json", Utils.readResourceFully(CONFIG_JSON)
                        .replace("{{file_name}}", pipelineFileName)
                        .replace("{{instance_type}}", C.DEFAULT_INSTANCE))
                .runPipeline();

    }

    @Test(dependsOnMethods = {"preparePipelineAndValidate"})
    @TestCase(value = {"EPMCMBIBPC-396"})
    public void pipelineShouldStartWithTimeout() {
        navigationMenu()
                .library()
                .clickOnPipeline(testPipeline)
                .firstVersion()
                .codeTab()
                .clearAndFillPipelineFile(pipelineFileName, Utils.readResourceFully(SLEEP_SHELL))
                .sleep(2, SECONDS)
                .runPipeline()
                .launch(this)
                .showLogForce(getRunId())
                .waitFor(LogAO.Status.FAILURE);
    }
}
