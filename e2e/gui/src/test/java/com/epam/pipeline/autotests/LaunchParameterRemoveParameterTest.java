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
import com.epam.pipeline.autotests.mixins.StorageHandling;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.epam.pipeline.autotests.ao.ParameterFieldAO.parameterByName;

public class LaunchParameterRemoveParameterTest extends AbstractAutoRemovingPipelineRunningTest implements StorageHandling {

    private final static String shellTemplate = "/fileKeeper.sh";

    @Test
    @TestCase({"EPMCMBIBPC-1007"})
    public void validateParametersRemoval() {
        navigationMenu()
                .createPipeline(Template.SHELL, getPipelineName())
                .firstVersion()
                .codeTab()
                .clearAndFillPipelineFile(
                        getPipelineName().toLowerCase() + ".sh",
                        Utils.readResourceFully(shellTemplate)
                )
                .runPipeline()
                .clickAddOutputParameter()
                .setName("new_parameter")
                .remove()
                .ensure(parameterByName("new_parameter"), not(exist));
    }
}
