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
import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.ToolDescription;
import com.epam.pipeline.autotests.ao.ToolPageAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.MINUTES;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.Status.FAILURE;
import static com.epam.pipeline.autotests.ao.LogAO.Status.STOPPED;
import static com.epam.pipeline.autotests.ao.LogAO.Status.SUCCESS;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessage;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.DEFAULT_COMMAND;
import static com.epam.pipeline.autotests.ao.Primitive.IMAGE_NAME;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.PORT;
import static com.epam.pipeline.autotests.ao.Primitive.RUN_CAPABILITIES;
import static com.epam.pipeline.autotests.ao.RunsMenuAO.HeaderColumn.DOCKER_IMAGE;
import static com.epam.pipeline.autotests.ao.RunsMenuAO.HeaderColumn.RUN;
import static com.epam.pipeline.autotests.ao.RunsMenuAO.HeaderColumn.STARTED;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RunToolsInSandBoxTest
        extends AbstractSeveralPipelineRunningTest
        implements Authorization, Tools {

    private static final String INITIALIZE_ENVIRONMENT = "InitializeEnvironment";
    // the output of a tool run is kept in the task named after the default pipeline name of tool runs
    private static final String RUN_OUTPUT_TASK = "pipeline";
    private static final long TIMEOUT_CHECK_SLACK_MS = SECONDS.toMillis(30);
    private final String command = "nginx -g \"daemon off;\"";
    private final String type = C.DEFAULT_INSTANCE;
    private final String disk = "15";
    private final String price = C.DEFAULT_INSTANCE_PRICE_TYPE_TOOL;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private final String endpoint = C.VALID_ENDPOINT;
    // a Docker Hub image as repository[:tag], pulled inside the runs of the DinD and Singularity cases
    private final String dindImage = C.DIND_DOCKER_IMAGE;
    private final String priceType = C.DEFAULT_INSTANCE_PRICE_TYPE;
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
        tools().performWithin(registry, group, tool, toolPage ->
                toolPage.ensure(IMAGE_NAME, text(nameWithoutGroup(tool)))
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
        final RunsMenuAO runs = tools()
                .perform(registry, group, tool, toolTab -> toolTab.settings().run(this));
        final String runId = getLastRunId();
        // the waiting icon lasts only until the node and the image are ready, so it is checked first
        runs.runShouldBeWaiting(runId);
        $(byId("active-runs-button")).closest("li").shouldHave(cssClass("ant-menu-item-selected"));
        assertTrue(runId.matches("\\d+"), format("Run ID '%s' is not a number", runId));
        runs
                .runCellShouldHave(runId, RUN, matchText(format("\\b%s\\b", runId)))
                .runCellShouldHave(runId, DOCKER_IMAGE, exactText(format("%s:%s", nameWithoutGroup(tool), "latest")))
                .runCellShouldHave(runId, STARTED, matchText("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test(dependsOnMethods = {"validatePipelineIsLaunchedForToolInSandbox"})
    @TestCase(value = {"EPMCMBIBPC-495"})
    public void validateEndpointLink() {
        ToolPageAO endpointPage = null;
        try {
            endpointPage = runsMenu()
                    .showLog(getLastRunId())
                    .waitForEndpointLink()
                    .sleep(3, MINUTES)
                    .clickOnEndpointLink();
            endpointPage
                    .sleep(10, SECONDS)
                    .validateEndpointPage(C.LOGIN);
        } finally {
            if (endpointPage != null) {
                endpointPage.closeTab();
            }
            open(C.ROOT_ADDRESS);
        }
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
        restartBrowser(C.ROOT_ADDRESS);

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
        final LogAO runPage = runsMenu()
                .showLog(getLastRunId())
                .stop(format("pipeline-%s", getLastRunId()))
                .waitFor(STOPPED)
                .sleep(3, MINUTES);
        ToolPageAO endpointPage = null;
        try {
            endpointPage = runPage.clickOnEndpointLink();
            endpointPage
                    .screenshot("test501screenshot")
                    .assertPageTitleIs("404 Not Found");
        } finally {
            if (endpointPage != null) {
                endpointPage.closeTab();
            }
        }
        runsMenu()
                .completedRuns()
                .validateStatus(getLastRunId(), STOPPED);
    }

    @Test(dependsOnMethods = {"validateStopToolInSandbox"})
    @TestCase(value = {"EPMCMBIBPC-503"})
    public void validateNodeReusage() {
        open(C.ROOT_ADDRESS);
        sleep(1, MINUTES);
        tools().perform(registry, group, tool, runTool())
                .setDefaultLaunchOptions()
                .withoutMounts()
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

        final LogAO runPage = tools().perform(registry, group, tool, runTool())
                .setLaunchOptions(disk, type, String.valueOf(timeout))
                .withoutMounts()
                .launchTool(this, nameWithoutGroup(tool))
                .showLog(getLastRunId())
                // the timeout counts from the start of the command, which follows the environment initialization
                .waitForTaskStatus(INITIALIZE_ENVIRONMENT, SUCCESS);
        final long commandStart = System.currentTimeMillis();
        runPage
                .waitForCompletion()
                .shouldHaveStatus(FAILURE);
        final long commandDuration = System.currentTimeMillis() - commandStart;
        assertTrue(commandDuration >= MINUTES.toMillis(timeout) - TIMEOUT_CHECK_SLACK_MS,
                format("The run finished %s s after its command started, before the %s min timeout",
                        MILLISECONDS.toSeconds(commandDuration), timeout));
        waitForTaskOfFinishedRun(runPage, RUN_OUTPUT_TASK)
                .click(taskWithName(RUN_OUTPUT_TASK))
                .ensure(log(), containsMessage("Timeout was elapsed"));
    }

    /**
     * The run page stops refreshing its tasks when the run finishes, and the output task of a tool run is saved
     * only after that, so the page is reloaded until the task is shown.
     */
    private LogAO waitForTaskOfFinishedRun(final LogAO runPage, final String task) {
        for (int attempt = 0; attempt < 6 && !$(taskWithName(task)).exists(); attempt++) {
            sleep(10, SECONDS);
            runPage.refresh();
        }
        return runPage.waitForTask(task);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-3172", "EPMCMBIBPC-3173"})
    public void validationOfDinDLaunchAndFunctionality() {
        tools()
                .perform(registry, group, tool, runTool())
                .selectValue(RUN_CAPABILITIES, "DinD")
                .setPriceType(priceType)
                .withoutMounts()
                .launch(this)
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_DIND_CONTAINER", "true"), exist)
                .waitForSshLink()
                .ssh(shell -> shell
                        .execute("docker --version")
                        .assertOutputContains("Docker version", ", build ")
                        .execute(format("docker pull %s", dindImage()))
                        .execute(format("docker create %s", dindImage()))
                        .execute("docker image ls")
                        .assertOutputContains("REPOSITORY", "TAG", "IMAGE ID", "CREATED", "SIZE")
                        // the image's row: its repository, then its tag (the columns are padded with spaces)
                        .assertPageContainsStringsWithRegex("docker image ls",
                                format("%s\\s+%s\\s", Pattern.quote(dindRepository()), Pattern.quote(dindTag())))
                        .execute("docker container ls -a")
                        .assertOutputContains("CONTAINER ID", "IMAGE", "COMMAND", "CREATED", "STATUS", "PORTS", "NAMES")
                        // the container's row: the image, then the command
                        .assertPageContainsStringsWithRegex("docker container ls -a",
                                format("%s\\s+\"/bin/bash\"", Pattern.quote(dindImage())))
                        .assertOutputContains("Created")
                        .close());
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-3174", "EPMCMBIBPC-3175"})
    public void validationOfSingularityLaunchAndFunctionality() {
        tools()
                .perform(registry, group, tool, runTool())
                .selectValue(RUN_CAPABILITIES, "Singularity")
                .setPriceType(priceType)
                .withoutMounts()
                .launch(this)
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("CP_CAP_SINGULARITY", "true"), exist)
                .waitForSshLink()
                .ssh(shell -> shell
                        .execute("singularity help version")
                        .assertOutputContains("Show the version for Singularity")
                        // The run sets the owner and the API token as Singularity's credentials, for the
                        // platform's registry: sent to Docker Hub, they fail the pull, and must not go there
                        .execute(format("env -u SINGULARITY_DOCKER_USERNAME -u SINGULARITY_DOCKER_PASSWORD "
                                + "singularity build %s.sif docker://%s", sifName(), dindImage()))
                        .waitForLog("Build complete")
                        .assertOutputContains(format("Build complete: %s.sif", sifName()))
                        .execute(format("singularity instance start %s.sif instance1", sifName()))
                        .assertOutputContains("instance started successfully")
                        .execute("singularity instance list")
                        .assertOutputContains("INSTANCE NAME", "PID", "IP", "IMAGE")
                        .assertPageContainsString("IMAGE\ninstance1")
                        .assertPageContainsString(format("/root/%s.sif", sifName()))
                        .close());
    }

    // e2e.ui.dind.docker.image, read where it is used: a missing key fails these two cases only
    private String dindImage() {
        assertTrue(StringUtils.isNotBlank(dindImage), "e2e.ui.dind.docker.image is not set");
        return dindImage.trim();
    }

    // a tag is the part after the last ':' that has no '/' after it; without one, docker takes "latest"
    private String dindRepository() {
        final int colon = dindImage().lastIndexOf(':');
        return colon > dindImage().lastIndexOf('/') ? dindImage().substring(0, colon) : dindImage();
    }

    private String dindTag() {
        final String repository = dindRepository();
        return repository.length() < dindImage().length() ? dindImage().substring(repository.length() + 1) : "latest";
    }

    // the image file Singularity builds, named after the last part of the repository
    private String sifName() {
        return dindRepository().substring(dindRepository().lastIndexOf('/') + 1);
    }

    private Function<ToolDescription, PipelineRunFormAO> runTool() {
        return tool -> tool.settings().runWithCustomSettings();
    }
}
