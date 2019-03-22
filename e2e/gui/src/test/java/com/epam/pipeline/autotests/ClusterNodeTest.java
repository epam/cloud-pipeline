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

import com.epam.pipeline.autotests.ao.ClusterMenuAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class ClusterNodeTest extends AbstractAutoRemovingPipelineRunningTest {

    @AfterClass
    @Override
    void removeNode() {
        // Node is terminated in EPMCMBIBPC-270
    }

    @Test(priority = 0)
    @TestCase(value = {"EPMCMBIBPC-270"})
    public void startAndStopPipelineAfterNodeAppear() {
        navigationMenu()
            .createPipeline(Template.SHELL, getPipelineName())
            .firstVersion()
            .runPipeline()
            .launch(this);

        clusterMenu()
            .waitForTheNode(getPipelineName(), getRunId());

        runsMenu()
            .stopRun(getRunId());
    }

    @Test(priority = 1, dependsOnMethods = {"startAndStopPipelineAfterNodeAppear"})
    @TestCase(value = {"EPMCMBIBPC-269"})
    public void nodesSortingIncreaseByDateShouldBeValid() {
        clusterMenu()
            .sortByIncrease(ClusterMenuAO.HeaderColumn.DATE)
            .validateSortedByIncrease(ClusterMenuAO.HeaderColumn.DATE);
    }

    @Test(priority = 2, dependsOnMethods = {"startAndStopPipelineAfterNodeAppear"})
    @TestCase(value = {"EPMCMBIBPC-271"})
    public void nodesSortingDecreaseByDateShouldBeValid() {
        clusterMenu()
            .sortByDecrease(ClusterMenuAO.HeaderColumn.DATE)
            .validateSortedByDecrease(ClusterMenuAO.HeaderColumn.DATE);
    }

    @Test(priority = 3, dependsOnMethods = {"startAndStopPipelineAfterNodeAppear"})
    @TestCase(value = {"EPMCMBIBPC-274"})
    public void nodesSortingIncreaseByLabelShouldBeValid() {
        clusterMenu()
            .sortByIncrease(ClusterMenuAO.HeaderColumn.LABEL)
            .validateSortedByIncrease(ClusterMenuAO.HeaderColumn.LABEL);
    }

    @Test(priority = 4, dependsOnMethods = {"startAndStopPipelineAfterNodeAppear"})
    @TestCase(value = {"EPMCMBIBPC-275"})
    public void nodesSortingDecreaseByLabelShouldBeValid() {
        clusterMenu()
            .sortByDecrease(ClusterMenuAO.HeaderColumn.LABEL)
            .validateSortedByDecrease(ClusterMenuAO.HeaderColumn.LABEL);
    }

    @Test(priority = 5, dependsOnMethods = {"startAndStopPipelineAfterNodeAppear"})
    @TestCase(value = {"EPMCMBIBPC-272"})
    public void nodesSortingIncreaseByNameShouldBeValid() {
        clusterMenu()
            .sortByIncrease(ClusterMenuAO.HeaderColumn.NAME)
            .validateSortedByIncrease(ClusterMenuAO.HeaderColumn.NAME);
    }

    @Test(priority = 6, dependsOnMethods = {"startAndStopPipelineAfterNodeAppear"})
    @TestCase(value = {"EPMCMBIBPC-273"})
    public void nodesSortingDecreaseByNameShouldBeValid() {
        clusterMenu()
            .sortByDecrease(ClusterMenuAO.HeaderColumn.NAME)
            .validateSortedByDecrease(ClusterMenuAO.HeaderColumn.NAME);
    }

    @Test(priority = 7, dependsOnMethods = {"startAndStopPipelineAfterNodeAppear"})
    @TestCase(value = {"EPMCMBIBPC-276, EPMCMBIBPC-277"})
    public void shouldFilterNodeByAddress() throws InterruptedException {
        final ClusterMenuAO clusterMenuAO = clusterMenu();

        final String ip = clusterMenuAO.getNodeAddress(getRunId());
        final int nodesCountBeforeFiltering = clusterMenuAO.getNodesCount();

        clusterMenuAO.filerBy(ClusterMenuAO.HeaderColumn.ADDRESS, ip);
        Thread.sleep(2000);
        assertEquals(clusterMenuAO.getNodesCount(), 1);

        final String filteredIp = clusterMenu().getNodeAddress(getRunId());
        assertEquals(filteredIp, ip);

        clusterMenuAO.resetFiltering(ClusterMenuAO.HeaderColumn.ADDRESS);
        Thread.sleep(2000);
        assertEquals(clusterMenuAO.getNodesCount(), nodesCountBeforeFiltering);
    }

    @Test(priority = 8, dependsOnMethods = {"startAndStopPipelineAfterNodeAppear"})
    @TestCase(value = {"EPMCMBIBPC-278, EPMCMBIBPC-279"})
    public void shouldFilterNodeByLabel() throws InterruptedException {
        final ClusterMenuAO clusterMenuAO = clusterMenu();
        final int nodesCountBeforeFiltering = clusterMenuAO.getNodesCount();

        clusterMenuAO.filerBy(ClusterMenuAO.HeaderColumn.LABEL, getRunId());
        Thread.sleep(2000);
        assertEquals(clusterMenuAO.getNodesCount(), 1);

        final String filteredRunId = clusterMenu().getNodeRunId(0);
        assertEquals(filteredRunId, "RUN ID " + getRunId());

        clusterMenuAO.resetFiltering(ClusterMenuAO.HeaderColumn.LABEL);
        Thread.sleep(2000);
        assertEquals(clusterMenuAO.getNodesCount(), nodesCountBeforeFiltering);
    }

    @Test(priority = 9, dependsOnMethods = {"startAndStopPipelineAfterNodeAppear"})
    @TestCase(value = {"EPMCMBIBPC-270"})
    public void shouldTerminateNode() {
        clusterMenu()
            .removeNode(getRunId())
            .validateThereIsNoNode(getRunId());
    }
}
