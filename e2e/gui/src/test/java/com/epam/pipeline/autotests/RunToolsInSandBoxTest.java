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

import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.PipelineRunFormAO;
import com.epam.pipeline.autotests.ao.ToolDescription;
import com.epam.pipeline.autotests.ao.ToolPageAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import java.util.function.Function;

import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.Primitive.DEFAULT_COMMAND;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.PORT;
import static com.epam.pipeline.autotests.ao.Primitive.RUN_CAPABILITIES;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.rightPad;
import static org.testng.Assert.assertEquals;

public class RunToolsInSandBoxTest
        extends AbstractSeveralPipelineRunningTest
        implements Authorization, Tools {

    private final String command = "nginx -g \"daemon off;\"";
    private final String type = C.DEFAULT_INSTANCE;
    private final String disk = "15";
    private final String price = C.DEFAULT_INSTANCE_PRICE_TYPE;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private final String endpoint = C.VALID_ENDPOINT;
    private final String testDockerImage = C.TEST_DOCKER_IMAGE;
    private String nodeName;

    @BeforeClass
    @AfterClass(alwaysRun = true)
    public void fallbackToToolDefaultState() {
        fallbackToToolDefaultState(registry,
                group,
                tool,
                endpoint,
                command,
                type,
                price,
                disk);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-526"})
    public void prepareForToolInSandbox() {
        tools().performWithin(registry, group, tool, tool ->
                tool.settings()
                        .setDefaultCommand(command)
                        .save()
        );
    }

    @Test(dependsOnMethods = {"prepareForToolInSandbox"})
    @TestCase(value = {"EPMCMBIBPC-493"})
    public void validateRunParameters() {
        tools().performWithin(registry, group, tool, tool ->
                tool.settings()
                        .ensure(PORT, value(endpoint))
                        .ensure(DEFAULT_COMMAND, text(command))
        );
    }

    @Test(dependsOnMethods = {"validateRunParameters"})
    @TestCase(value = {"EPMCMBIBPC-494"})
    public void validatePipelineIsLaunchedForToolInSandbox() {
        tools()
                .perform(registry, group, tool, runTool())
                .setDefaultLaunchOptions()
                .launchTool(this, nameWithoutGroup(tool))
                .assertLatestPipelineHasName(format("%s:%s", nameWithoutGroup(tool), "latest"));
    }

    @Test(dependsOnMethods = {"validatePipelineIsLaunchedForToolInSandbox"})
    @TestCase(value = {"EPMCMBIBPC-495"})
    public void validateEndpointLink() {
        runsMenu()
                .show(getLastRunId())
                .clickEndpoint()
                .sleep(10, SECONDS)
                .validateEndpointPage(C.LOGIN);
    }

    @Test(dependsOnMethods = {"validatePipelineIsLaunchedForToolInSandbox"})
    @TestCase(value = {"EPMCMBIBPC-496"})
    public void validateUsernameOnPipelinePage() {
        open(C.ROOT_ADDRESS);
        runsMenu()
                .show(getLastRunId())
                .ensureHasOwner(getUserNameByAccountLogin(admin.login));
    }

    @Test(dependsOnMethods = "validateUsernameOnPipelinePage")
    @TestCase(value = {"EPMCMBIBPC-500"})
    public void checkToolAccessibilityForAnotherUser() {
        open(C.ROOT_ADDRESS);
        String pipelineUrl =
                runsMenu()
                        .show(getLastRunId())
                        .waitEndpoint()
                        .attr("href");
        logout();

        // in order to avoid caching issue
        Utils.restartBrowser(C.ROOT_ADDRESS);

        loginAs(user)
                .runs()
                .validateOnlyMyPipelines();

        open(pipelineUrl);
        new ToolPageAO(pipelineUrl)
                .sleep(5, SECONDS)
                .screenshot("test500screenshot")
                .assertPageTitleIs("401 Authorization Required");

        open(C.ROOT_ADDRESS);
        logout();
        loginAs(admin);
    }

    @Test(dependsOnMethods = {"checkToolAccessibilityForAnotherUser"})
    @TestCase(value = {"EPMCMBIBPC-501"})
    public void validateStopToolInSandbox() {
        open(C.ROOT_ADDRESS);
        nodeName = clusterMenu()
                .waitForTheNode(nameWithoutGroup(tool), getLastRunId())
                .getNodeName(getLastRunId());
        runsMenu()
                .stopRun(getLastRunId())
                .completedRuns()
                .validateStoppedStatus(getLastRunId())
                .show(getLastRunId())
                .clickEndpoint()
                .screenshot("test501screenshot")
                .assertPageTitleIs("404 Not Found");
    }

    @Test(dependsOnMethods = {"validateStopToolInSandbox"})
    @TestCase(value = {"EPMCMBIBPC-503"})
    public void validateNodeReusage() {
        open(C.ROOT_ADDRESS);

        tools().perform(registry, group, tool, runTool())
                .setDefaultLaunchOptions()
                .launchTool(this, nameWithoutGroup(tool));

        String otherNodeName = clusterMenu()
                .waitForTheNode(nameWithoutGroup(tool), getLastRunId())
                .getNodeName(getLastRunId());

        assertEquals(nodeName, otherNodeName);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-504"})
    public void validateStopToolInSandboxWithTimeout() {
        int timeout = 2;

        tools().perform(registry, group, tool, runTool())
                .setLaunchOptions(disk, type, String.valueOf(timeout))
                .launchTool(this, nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForCompletion()
                .shouldHaveStatus(LogAO.Status.FAILURE);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-3172", "EPMCMBIBPC-3173"})
    public void validationOfDinDLaunchAndFunctionality() {
        tools()
                .perform(registry, group, tool, runTool())
                .selectValue(RUN_CAPABILITIES, "DinD")
                .launch(this)
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_DIND_CONTAINER", "true"), exist)
                .waitForSshLink()
                .ssh(shell -> shell
                        .execute("docker --version")
                        .assertOutputContains("Docker version", ", build ")
                        .execute(format("docker pull %s", testDockerImage))
                        .execute(format("docker create %s", testDockerImage))
                        .execute("docker image ls")
                        .assertOutputContains("REPOSITORY", "TAG", "IMAGE ID", "CREATED", "SIZE")
                        .assertOutputContains(testDockerImage, "latest")
                        .assertPageContainsString(format(rightPad(testDockerImage, 20, ' '), "latest"))
                        .execute("docker container ls -a")
                        .assertOutputContains("CONTAINER ID", "IMAGE", "COMMAND", "CREATED", "STATUS", "PORTS", "NAMES")
                        .assertPageContainsString(format(rightPad(testDockerImage, 20, ' '), "\"/bin/bash\""))
                        .assertOutputContains("Created")
                        .close());
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-3174", "EPMCMBIBPC-3175"})
    public void validationOfSingularityLaunchAndFunctionality() {
        tools()
                .perform(registry, group, tool, runTool())
                .selectValue(RUN_CAPABILITIES, "Singularity")
                .launch(this)
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_SINGULARITY", "true"), exist)
                .waitForSshLink()
                .ssh(shell -> shell
                        .execute("singularity help version")
                        .assertOutputContains("Show the version for Singularity")
                        .execute(format("singularity build %s.sif library://%s", testDockerImage, testDockerImage))
                        .assertOutputContains(format("Build complete: %s.sif", testDockerImage))
                        .execute(format("singularity instance start %s.sif instance1", testDockerImage))
                        .assertOutputContains("instance started successfully")
                        .execute("singularity instance list")
                        .assertOutputContains("INSTANCE NAME", "PID", "IP", "IMAGE")
                        .assertPageContainsString("IMAGE\ninstance1")
                        .assertPageContainsString(format("/root/%s.sif", testDockerImage))
                        .close());
    }

    private Function<ToolDescription, PipelineRunFormAO> runTool() {
        return tool -> tool.settings().runWithCustomSettings();
    }
}
