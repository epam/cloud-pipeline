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

import com.epam.pipeline.autotests.utils.C;
import org.testng.annotations.AfterClass;

import java.util.Random;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.pipelineWithName;
import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class AbstractAutoRemovingPipelineRunningTest
        extends AbstractSinglePipelineRunningTest {

    private final String pipelineName = testPrefix().replaceAll("_", "-") +
            Math.abs(new Random().nextInt());

    @AfterClass(alwaysRun = true)
    public void removePipeline() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
            .library()
            .sleep(1, SECONDS)
            .performIf(
                pipelineWithName(getPipelineName()),
                exist,
                library -> library.clickOnPipeline(getPipelineName()).delete()
            );
    }

    public String getPipelineName() {
        return pipelineName;
    }

    private String testPrefix() {
        return this.getClass().getSimpleName();
    }
}
