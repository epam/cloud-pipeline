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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class AbstractSeveralPipelineRunningTest
        extends AbstractBfxPipelineTest
        implements Navigation {

    private final List<String> runIds = new ArrayList<>();

    public void addRunId(String runId) {
        runIds.add(runId);
    }

    public String getLastRunId() {
        if (runIds.isEmpty()) throw new RuntimeException("There is no pipeline has been run.");
        final String runId = runIds.get(runIds.size() - 1);
        System.out.printf("Returns latest run with runId %s from %s%n", runId, Arrays.toString(runIds.toArray()));
        return runId;
    }

    @AfterClass(alwaysRun = true, dependsOnMethods = {"stopRuns"})
    public void removeNodes() {
        open(C.ROOT_ADDRESS);
        sleep(1, SECONDS);
        runIds.forEach(runId ->
            navigationMenu()
                    .clusterNodes()
                    .removeNodeIfPresent(runId)
        );
    }

    @AfterClass(alwaysRun = true)
    public void stopRuns() {
        open(C.ROOT_ADDRESS);
        sleep(1, SECONDS);
        System.out.printf("Stop runs with ids %s%n", Arrays.toString(runIds.toArray()));
        runIds.forEach(id -> {
            System.out.printf("Run with id %s is going to be stopped.%n", id);
            navigationMenu().runs().stopRunIfPresent(id);
        });
    }
}
