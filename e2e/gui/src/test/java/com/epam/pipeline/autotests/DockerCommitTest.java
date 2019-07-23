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

import com.epam.pipeline.autotests.ao.*;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.function.Consumer;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.CommitPopup.deleteRuntimeFiles;
import static com.epam.pipeline.autotests.ao.CommitPopup.stopPipeline;
import static com.epam.pipeline.autotests.ao.ConfirmationPopupAO.confirmCommittingToExistingTool;
import static com.epam.pipeline.autotests.ao.LogAO.Status.LOADING;
import static com.epam.pipeline.autotests.ao.LogAO.Status.STOPPED;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class DockerCommitTest
        extends AbstractSeveralPipelineRunningTest
        implements Tools {

    private static final long suffix = Utils.randomSuffix();
    private final String login = C.LOGIN;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String registryIp = C.DEFAULT_REGISTRY_IP;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String endpoint = C.VALID_ENDPOINT;
    private final String defaultCommand = Tools.defaultCommand;
    private final String defaultInstanceType = Tools.defaultInstanceType;
    private final String defaultDiskSize = Tools.defaultDiskSize;
    private final String defaultPriceType = Tools.defaultPriceType;
    private final String testFileName = "test_file.txt";
    private final String testFileContent = "This is a test file " + suffix;
    private final String personalGroup = "personal";
    private final String toolInPersonalGroup = personalGroupActualName(login) + "/" + exactToolName(tool);
    private final String customTag = "test_tag";

    @AfterClass(alwaysRun = true, dependsOnMethods = {"stopRuns"})
    @Override
    public void removeNodes() {
        sleep(3, MINUTES);
        super.removeNodes();
    }

    @BeforeClass
    @AfterClass(alwaysRun = true)
    public void fallbackToToolDefaultState() {
        open(C.ROOT_ADDRESS);
        fallbackToToolDefaultState(registry, group, tool);
    }

    @BeforeClass
    public void createPersonalGroup() {
        open(C.ROOT_ADDRESS);
        tools().perform(registry, personalGroup, ToolGroup::createPersonalGroup);
    }

    @AfterClass(alwaysRun = true)
    public void deletePersonalGroup() {
        open(C.ROOT_ADDRESS);
        tools()
                .perform(registry, personalGroup, group ->
                        group.performIf(groupHasNoText("No tools found."), deleteTool(toolInPersonalGroup))
                )
                .sleep(1, SECONDS)
                .performIf(CREATE_PERSONAL_GROUP, not(visible), deleteGroup(personalGroupActualName(C.LOGIN)));
    }

    @AfterClass(alwaysRun = true)
    public void deleteCustomVersion() {
        open(C.ROOT_ADDRESS);
        tools()
                .perform(registry, group, tool, tool ->
                        tool.versions()
                                .viewUnscannedVersions()
                                .performIf(hasOnPage(customTag), deleteVersion(customTag))
                );
    }

    @Test
    @TestCase({"EPMCMBIBPC-692"})
    public void pushDockerFormValidation() {
        tools()
                .perform(registry, group, tool, tool ->
                        tool.settings()
                                .ensure(PORT, value(endpoint))
                                .ensure(INSTANCE_TYPE, value(defaultInstanceType))
                                .ensure(PRICE_TYPE, value(defaultPriceType))
                                .ensure(DISK, value(defaultDiskSize))
                                .ensure(DEFAULT_COMMAND, value(defaultCommand))
                                .run(this)
                )
                .showLog(getLastRunId())
                .waitForCommitButton()
                .commit(commit ->
                        commit.ensureVisible(REGISTRY, GROUP, IMAGE_NAME, VERSION, DELETE_RUNTIME_FILES, STOP_PIPELINE,
                                CANCEL, COMMIT)
                                .cancel()
                );
    }

    @Test(dependsOnMethods = {"pushDockerFormValidation"})
    @TestCase({"EPMCMBIBPC-1350"})
    public void listOfGroupInRegistry() {
        logAO().commit(commit ->
                commit.hover(GROUP).ensure(byText(group), visible)
                        .cancel()
        );
    }

    @Test(dependsOnMethods = {"listOfGroupInRegistry"})
    @TestCase({"EPMCMBIBPC-693"})
    public void checkListOfRegistry() {
        logAO().commit(commit ->
                commit.hover(REGISTRY).ensure(byText(registry), visible)
                        .cancel()
        );
    }

    @Test(dependsOnMethods = {"checkListOfRegistry"})
    @TestCase({"EPMCMBIBPC-694"})
    public void commitDockerValidation() {
        logAO()
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getLastRunId())
                        .execute("cd /")
                        .execute(String.format("echo '%s' > %s", testFileContent, testFileName))
                        .close()
                );

        runsMenu()
                .log(getLastRunId(), log ->
                        log
                                .commit(commit ->
                                        commit.setRegistry(registry)
                                                .setGroup(group)
                                                .ensure(IMAGE_NAME, value(exactToolName(tool)))
                                                .ok()
                                                .also(confirmCommittingToExistingTool(registryIp, tool))
                                )
                                .assertCommittingFinishedSuccessfully()
                )
                .stopRun(getLastRunId());
    }

    @Test(dependsOnMethods = {"commitDockerValidation"})
    @TestCase({"EPMCMBIBPC-695"})
    public void validateCommittedDocker() {
        tools()
                .perform(registry, group, tool, tool ->
                        tool.settings()
                                .ensure(PORT, value(endpoint))
                                .ensure(INSTANCE_TYPE, value(defaultInstanceType))
                                .ensure(PRICE_TYPE, value(defaultPriceType))
                                .ensure(DISK, value(defaultDiskSize))
                                .ensure(DEFAULT_COMMAND, value(defaultCommand))
                                .run(this)
                )
                .log(getLastRunId(), log ->
                        log.waitForSshLink()
                                .ssh(shell ->
                                        shell.execute("cd /")
                                                .execute("head " + testFileName)
                                                .assertOutputContains(testFileContent)
                                                .close()
                                )
                );

        runsMenu()
                .stopRun(getLastRunId());
    }

    @Test(dependsOnMethods = {"validateCommittedDocker"})
    @TestCase({"EPMCMBIBPC-696"})
    public void validateStopPipelineFlagWorksProperly() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForCommitButton()
                .commit(commit ->
                        commit.setRegistry(registry)
                                .sleep(3, SECONDS)
                                .setName(nameWithoutGroup(tool))
                                .sleep(5, SECONDS)
                                .click(stopPipeline())
                                .ok()
                                .also(confirmCommittingToExistingTool(registryIp, tool))
                )
                .assertCommittingFinishedSuccessfully()
                .shouldHaveStatus(STOPPED);
    }

    @Test(dependsOnMethods = {"validateStopPipelineFlagWorksProperly"})
    @TestCase({"EPMCMBIBPC-724"})
    public void validateDeleteRuntimeFilesFlagWorksProperly() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForCommitButton()
                .commit(commit ->
                        commit.setRegistry(registry)
                                .sleep(3, SECONDS)
                                .setName(nameWithoutGroup(tool))
                                .sleep(5, SECONDS)
                                .click(deleteRuntimeFiles())
                                .ok()
                                .also(confirmCommittingToExistingTool(registryIp, tool))
                )
                .assertCommittingFinishedSuccessfully();
    }

    @Test(dependsOnMethods = {"validateDeleteRuntimeFilesFlagWorksProperly"})
    @TestCase({"EPMCMBIBPC-697"})
    public void validateCommittedDockerThatHadEnabledDeleteRuntimeFilesFlag() {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .shouldHaveStatus(LOADING);

        runsMenu()
                .stopRun(getLastRunId());
    }

    @Test(dependsOnMethods = {"validateCommittedDockerThatHadEnabledDeleteRuntimeFilesFlag"})
    @TestCase({"EPMCMBIBPC-1352"})
    public void validateCommittedDockerInPersonalGroup() {
        String commandCD = "cd /";
        String command = "echo \"This is a test file %s\" > %s";

        tools()
                .perform(registry, group, tool, tool ->
                        tool.settings()
                                .ensure(PORT, value(endpoint))
                                .ensure(INSTANCE_TYPE, value(defaultInstanceType))
                                .ensure(PRICE_TYPE, value(defaultPriceType))
                                .ensure(DISK, value(defaultDiskSize))
                                .ensure(DEFAULT_COMMAND, value(defaultCommand))
                                .run(this)
                )
                .log(getLastRunId(), log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell -> shell
                                                .waitUntilTextAppears(getLastRunId())
                                                .execute(commandCD)
                                                .execute(String.format(command, suffix, testFileName))))
                                .waitForCommitButton()
                                .commit(commit ->
                                        commit.setRegistry(registry)
                                                .setGroup(personalGroup)
                                                .ensure(IMAGE_NAME, value(exactToolName(tool)))
                                                .sleep(1, SECONDS)
                                                .ok())
                                .assertCommittingFinishedSuccessfully()
                    );

        runsMenu()
                .stopRun(getLastRunId());
    }

    @Test(dependsOnMethods = {"validateCommittedDockerInPersonalGroup"})
    @TestCase({"EPMCMBIBPC-1353"})
    public void validateCommittedDockerInGroup() {
        tools()
                .perform(registry, personalGroup, toolInPersonalGroup, tool ->
                        tool.settings()
                                .ensure(PORT, value(endpoint))
                                .ensure(INSTANCE_TYPE, value(defaultInstanceType))
                                .ensure(PRICE_TYPE, value(defaultPriceType))
                                .ensure(DISK, value(defaultDiskSize))
                                .setDefaultCommand(defaultCommand)
                                .save()
                                .run(this)
                )
                .log(getLastRunId(), log ->
                        log.waitForSshLink()
                                .inAnotherTab(logTab -> logTab
                                        .ssh(shell ->
                                                shell.execute("cd /")
                                                        .execute("head " + testFileName)
                                                        .assertOutputContains(testFileContent)))
                );

        runsMenu()
                .stopRun(getLastRunId());
    }

    @Test(dependsOnMethods = {"validateCommittedDockerInGroup"})
    @TestCase({"EPMCMBIBPC-1408"})
    public void validateCommitDockerImageWithTag() {
        tools()
                .perform(registry, group, tool, tool ->
                        tool.settings()
                                .ensure(PORT, value(endpoint))
                                .ensure(INSTANCE_TYPE, value(defaultInstanceType))
                                .ensure(PRICE_TYPE, value(defaultPriceType))
                                .ensure(DISK, value(defaultDiskSize))
                                .ensure(DEFAULT_COMMAND, value(defaultCommand))
                                .run(this)
                )
                .log(getLastRunId(), log ->
                        log.waitForCommitButton()
                                .commit(commit ->
                                        commit.setRegistry(registry)
                                                .setGroup(group)
                                                .ensure(IMAGE_NAME, value(nameWithoutGroup(tool)))
                                                .sleep(1, SECONDS)
                                                .setVersion(customTag)
                                                .sleep(1, SECONDS)
                                                .ok())
                                .assertCommittingFinishedSuccessfully()
                );

        tools()
                .perform(registry, group, tool, tool ->
                        tool.versions()
                                .viewUnscannedVersions()
                                .ensureHasTag(customTag)
                );

        runsMenu()
                .stopRun(getLastRunId());
    }

    @Test(dependsOnMethods = {"validateCommitDockerImageWithTag"})
    @TestCase({"EPMCMBIBPC-1409"})
    public void validateLaunchSpecificVersion() {
        String dockerFormat = "%s/%s/%s:%s";

        tools()
                .perform(registry, group, tool, defaultTool ->
                        defaultTool.versions()
                                .viewUnscannedVersions()
                                .runVersionWithDefaultSettings(this, nameWithoutGroup(tool), customTag)
                )
                .showLog(getLastRunId())
                .instanceParameters(parameters ->
                                parameters.ensure(IMAGE, text(String.format(dockerFormat, registryIp, group, nameWithoutGroup(tool), customTag)))
                );

        runsMenu()
                .stopRun(getLastRunId());

        tools()
                .perform(registry, group, tool, tool ->
                        tool.versions()
                                .viewUnscannedVersions()
                                .deleteVersion(customTag));
    }

    private LogAO logAO() {
        return new LogAO();
    }

    private String exactToolName(String tool) {
        return tool.substring(tool.lastIndexOf("/") + 1);
    }

    private String personalGroupActualName(final String login) {
        return login.toLowerCase().replaceAll("[._@]", "-");
    }

    private Consumer<ToolGroup> deleteGroup(final String groupName) {
        return group -> group.deleteGroup(deletion -> deletion.ensureGroupNameIs(groupName).ok());
    }

    public boolean hasOnPage(String customTag) {
        return $(byClassName("ant-table-tbody"))
                .find(byXpath(String.format(".//tr[contains(@class, 'ant-table-row-level-0') and contains(., '%s')]", customTag)))
                .exists();
    }

    private Consumer<ToolVersions> deleteVersion(String customTag) {
        return tool -> tool.deleteVersion(customTag);
    }

    private Consumer<ConfirmationPopupAO<Registry>> confirmGroupDeletion(final String groupName) {
        return confirmation ->
                confirmation.ensureTitleIs(String.format("Are you sure you want to delete '%s'?", groupName))
                        .sleep(1, SECONDS)
                        .ok();
    }

    private boolean groupHasNoText(String text) {
        return !$(byClassName("ant-card-body")).find(byText(text)).exists();
    }
}