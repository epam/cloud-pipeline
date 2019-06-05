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

import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import org.testng.annotations.AfterClass;

import java.util.Optional;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class AbstractSinglePipelineRunningTest
        extends AbstractBfxPipelineTest
        implements Navigation {

    private String runId;

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getRunId() {
        return runId;
    }

    @AfterClass(alwaysRun = true, enabled = false)
    void removeNode() {
        open(C.ROOT_ADDRESS);
        sleep(1, SECONDS);
        Optional.ofNullable(getRunId())
                .ifPresent(runId -> clusterMenu().waitForTheNode(runId).removeNodeIfPresent(runId));
    }

    @AfterClass(alwaysRun = true, enabled = false)
    void stopRun() {
        open(C.ROOT_ADDRESS);
        sleep(1, SECONDS);
        Optional.ofNullable(getRunId())
                .ifPresent(runId -> runsMenu().stopRunIfPresent(runId));
    }
}
