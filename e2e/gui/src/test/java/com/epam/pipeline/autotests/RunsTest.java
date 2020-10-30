/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.CollectionCondition.sizeGreaterThanOrEqual;
import static com.epam.pipeline.autotests.ao.LogAO.Status.STOPPED;
import static com.epam.pipeline.autotests.ao.LogAO.Status.SUCCESS;
import static com.epam.pipeline.autotests.ao.RunsMenuAO.HeaderColumn.DOCKER_IMAGE;
import static com.epam.pipeline.autotests.ao.RunsMenuAO.HeaderColumn.PIPELINE;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;

public class RunsTest extends AbstractSeveralPipelineRunningTest implements Authorization, Tools {

    private final String pipeline = "runsTest-" + Utils.randomSuffix();
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String anotherGroup = C.ANOTHER_GROUP;
    private final String anotherTool = format("%s/%s", anotherGroup, C.ANOTHER_TESTING_TOOL_NAME);
    private String pipelineRunID = "";
    private String firstToolRunID = "";
    private String secondToolRunID = "";

    @BeforeClass
    public void initialLogout() {
        library()
                .createPipeline(pipeline)
                .clickOnPipeline(pipeline)
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .selectByName(user.login)
                .showPermissions()
                .set(READ, ALLOW)
                .set(WRITE, ALLOW)
                .set(EXECUTE, ALLOW)
                .closeAll();
        logout();
    }

    @AfterClass(alwaysRun = true)
    public void removePipeline() {
        logout();
        loginAs(admin);
        navigationMenu()
                .library()
                .removePipelineIfExists(pipeline);
    }

    @Test
    @TestCase({"EPMCMBIBPC-103"})
    public void platformUsageInfo() {
        loginAs(user);
        library()
                .clickOnPipeline(pipeline)
                .firstVersion()
                .runPipeline()
                .launch(this)
                .showLog(pipelineRunID = getLastRunId())
                .waitForCompletion();
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .launchTool(this, nameWithoutGroup(tool))
                .sleep(3, MINUTES)
                .stopRun(firstToolRunID = getLastRunId());
        tools()
                .perform(registry, anotherGroup, anotherTool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .launchTool(this, nameWithoutGroup(anotherTool))
                .sleep(3, MINUTES)
                .stopRun(secondToolRunID = getLastRunId());
        runsMenu()
                .completedRuns()
                .validateColumnName("Run", "Parent run", "Pipeline", "Docker image",
                        "Started", "Completed", "Elapsed", "Owner")
                .validateAllRunsHaveButton("RERUN")
                .validateAllRunsHaveButton("Log")
                .validateAllRunsHaveCost()
                .validateRowsCount(sizeGreaterThanOrEqual(3));
    }

    @Test(dependsOnMethods = {"platformUsageInfo"})
    @TestCase({"EPMCMBIBPC-3170"})
    public void platformUsagePipelines() {
        navigationMenu()
                .runs()
                .completedRuns()
                .refresh()
                .filterBy(PIPELINE, pipeline)
                .validateRowsCount(size(1))
                .assertLatestPipelineHasRunID(pipelineRunID)
                .validateStatus(pipelineRunID, SUCCESS)
                .validatePipelineOwner(pipelineRunID, user.login.toLowerCase());
    }

    @Test(dependsOnMethods = {"platformUsageInfo"})
    @TestCase({"EPMCMBIBPC-3171"})
    public void platformUsageTools() {
        navigationMenu()
                .runs()
                .completedRuns()
                .filterBy(DOCKER_IMAGE, nameWithoutGroup(tool))
                .validateRowsCount(sizeGreaterThanOrEqual(1))
                .assertLatestPipelineHasRunID(firstToolRunID)
                .validateStatus(firstToolRunID, STOPPED)
                .validatePipelineOwner(firstToolRunID, user.login.toLowerCase())
                .resetFiltering(DOCKER_IMAGE)
                .filterBy(DOCKER_IMAGE, nameWithoutGroup(anotherTool))
                .validateRowsCount(sizeGreaterThanOrEqual(1))
                .assertLatestPipelineHasRunID(secondToolRunID)
                .validateStatus(secondToolRunID, STOPPED)
                .validatePipelineOwner(secondToolRunID, user.login.toLowerCase());
    }
}
