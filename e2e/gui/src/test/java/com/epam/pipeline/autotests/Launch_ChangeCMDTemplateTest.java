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
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.exactText;
import static com.epam.pipeline.autotests.ao.Primitive.DEFAULT_COMMAND;
import static com.epam.pipeline.autotests.utils.Json.editingConfiguration;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Launch_ChangeCMDTemplateTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final String CMD_TEMPLATE = "cmd_template";

    @Test
    @TestCase(value = {"EPMCMBIBPC-377"})
    public void createPipelineAndValidate() {
        navigationMenu()
                .createPipeline(Template.SHELL, getPipelineName())
                .firstVersion()
                .codeTab()
                .clickOnFile("config.json")
                .editFile(editingConfiguration("default", configuration ->
                        configuration.put("cmd_template", CMD_TEMPLATE)))
                .saveAndCommitWithMessage("test: Change cmd_template")
                .sleep(2, SECONDS)
                .runPipeline()
                .ensure(DEFAULT_COMMAND, exactText(CMD_TEMPLATE));
    }
}
