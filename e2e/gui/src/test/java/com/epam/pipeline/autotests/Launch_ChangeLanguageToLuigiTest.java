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
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;

public class Launch_ChangeLanguageToLuigiTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final String CONFIG_JSON = "/languageToLuigi.json";

    @Test
    @TestCase(value = {"EPMCMBIBPC-373"})
    public void createPipelineAndValidate() {
        navigationMenu()
                .createPipeline(Template.SHELL, getPipelineName())
                .firstVersion()
                .codeTab()
                .clickOnFile("config.json")
                .editFile(configuration -> Utils.readResourceFully(CONFIG_JSON)
                        .replace("{{instance_type}}", C.DEFAULT_INSTANCE))
                .saveAndCommitWithMessage("test: Change configuration from SHELL to LUIGI")
                .graphTab();
    }
}
