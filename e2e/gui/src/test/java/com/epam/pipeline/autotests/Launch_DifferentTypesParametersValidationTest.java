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

import com.epam.pipeline.autotests.ao.ParameterType;
import com.epam.pipeline.autotests.ao.PipelineCodeTabAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.SECONDS;

public class Launch_DifferentTypesParametersValidationTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final String CONFIG_JSON = "/differentTypes.json";

    @Test
    @TestCase("EPMCMBIBPC-367")
    public void preparePipeline() {
        navigationMenu()
            .createPipeline(Template.SHELL, getPipelineName())
            .firstVersion()
            .codeTab()
            .clearAndFillPipelineFile("config.json", Utils.readResourceFully(CONFIG_JSON)
                    .replace("{{storage_type}}", C.STORAGE_PREFIX)
                    .replace("{{instance_type}}", C.DEFAULT_INSTANCE));
    }

    @Test
    @TestCase("EPMCMBIBPC-367")
    public void validateParameters() {
        new PipelineCodeTabAO(getPipelineName())
            .sleep(2, SECONDS)
            .runPipeline()
            .validateThereIsParameterOfType("output", String.format("%s://unexist-bucket/out", C.STORAGE_PREFIX),
                    ParameterType.OUTPUT, false)
            .validateThereIsParameterOfType("input", String.format("%s://unexist-bucket/in", C.STORAGE_PREFIX),
                    ParameterType.INPUT, false)
            .validateThereIsParameterOfType("common",
                    String.format("%s://unexist-bucket/common", C.STORAGE_PREFIX), ParameterType.COMMON, false)
            .validateThereIsParameterOfType("path", String.format("%s://unexist-bucket/path", C.STORAGE_PREFIX),
                    ParameterType.PATH, false);
    }
}
