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

import com.epam.pipeline.autotests.ao.PipelineGraphTabAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.FULLSCREEN;
import static com.epam.pipeline.autotests.ao.Primitive.ZOOM_IN;
import static com.epam.pipeline.autotests.ao.Primitive.ZOOM_OUT;

public class WDLPipelineTest extends AbstractBfxPipelineTest implements Navigation {
    private final String pipelineName = "wdl-pipeline-test-" + Utils.randomSuffix();

    @AfterClass
    public void removePipeline() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .removePipeline(pipelineName);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-351"})
    public void shouldCreateValidWDLPipeline() {
        navigationMenu()
                .library()
                .createPipeline(Template.WDL, pipelineName)
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .codeTab()
                .graphTab()
                .searchLabel("HelloWorld_print")
                .searchLabel("workflow")
                .ensureVisible(ZOOM_IN, ZOOM_OUT, FULLSCREEN);
    }

    @Test(dependsOnMethods = {"shouldCreateValidWDLPipeline"})
    @TestCase(value = {"EPMCMBIBPC-352"})
    public void fullScreenGraphShouldBeValid() {
        new PipelineGraphTabAO(pipelineName)
                .click(FULLSCREEN)
                .verifyFullcreen()
                .ensureVisible(ZOOM_IN, ZOOM_OUT, FULLSCREEN);
    }
}