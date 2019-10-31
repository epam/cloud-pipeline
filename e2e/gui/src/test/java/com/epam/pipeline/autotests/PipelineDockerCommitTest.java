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
import com.epam.pipeline.autotests.ao.ToolGroup;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.LogAO.Status.LOADING;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.PRICE_TYPE;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class PipelineDockerCommitTest
        extends AbstractSeveralPipelineRunningTest
        implements Tools {

    private final String registry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String pipelineName = "docker-commit-test-pipeline-" + Utils.randomSuffix();
    private final String fileInPipeline = Utils.getFileNameFromPipelineName(pipelineName, "sh");
    private final String toolSelfName = "shell";
    private final String toolFullName = String.format("%s/%s", defaultGroup, toolSelfName);
    private final String toolVersion = "latest";
    private final String diskSize = "19";
    private final String instanceType = C.DEFAULT_INSTANCE;
    private final String priceType = C.DEFAULT_INSTANCE_PRICE_TYPE;

    @BeforeClass
    public void removeTool() {
        tools().perform(registry, defaultGroup, group ->
            group.sleep(3, SECONDS)
                .performIf(ToolGroup.tool(toolFullName), visible, t ->
                    t.tool(toolFullName, tool ->  tool.sleep(1, SECONDS)
                        .delete()
                        .ensureTitleIs("Are you sure you want to delete tool?")
                        .ok())));
    }

    @BeforeClass
    public void createPipeline() {
        library().createPipeline(Template.SHELL, pipelineName);
    }

    @AfterClass(alwaysRun = true)
    public void enableToolIfItIsRemoved() {
        tools()
                .perform(registry, defaultGroup, group ->
                        group.performIf(group.allToolsNames().isEmpty(),
                                gr -> gr.enableTool(toolSelfName)));
    }

    @AfterClass(alwaysRun = true)
    public void removePipeline() {
        library().removePipeline(pipelineName);
    }

    @AfterClass(alwaysRun = true)
    @Override
    public void removeNodes() {
        sleep(3, MINUTES);
        super.removeNodes();
    }

    @Test
    @TestCase({"EPMCMBIBPC-698"})
    public void validateCommittingPipelineAsDocker() {
        navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .codeTab()
                .clearAndFillPipelineFile(fileInPipeline, "sleep 10000")
                .sleep(2, SECONDS)
                .runPipeline()
                .setLaunchOptions(diskSize, instanceType, null)
                .setPriceType(priceType)
                .launch(this)
                .showLog(getLastRunId())
                .waitForCommitButton()
                .commit(commit ->
                        commit.setRegistry(registry)
                                .setGroup(defaultGroup)
                                .sleep(2, SECONDS)
                                .setName(toolSelfName)
                                .sleep(1, SECONDS)
                                .setVersion(toolVersion)
                                .ok()
                )
                .assertCommittingFinishedSuccessfully();
    }

    @Test(dependsOnMethods = {"validateCommittingPipelineAsDocker"})
    @TestCase({"EPMCMBIBPC-700"})
    public void validateCommittedPipeline() {
        tools()
                .perform(registry, defaultGroup, toolFullName, tool ->
                        tool.settings()
                                .setDefaultCommand("sleep 100")
                                .setInstanceType(instanceType)
                                .setDisk(diskSize)
                                .setPriceType(priceType)
                                .save()
                                .run(this)
                )
                .showLog(getLastRunId())
                .shouldHaveStatus(LOADING);
    }
}
