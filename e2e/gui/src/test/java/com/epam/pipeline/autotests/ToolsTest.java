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

import com.codeborne.selenide.Condition;
import com.epam.pipeline.autotests.ao.*;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.codeborne.selenide.CollectionCondition.sizeGreaterThanOrEqual;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.ao.ToolDescription.editButtonFor;
import static com.epam.pipeline.autotests.ao.ToolGroup.tool;
import static com.epam.pipeline.autotests.ao.ToolGroup.toolsNames;
import static com.epam.pipeline.autotests.ao.ToolSettings.label;
import static com.epam.pipeline.autotests.ao.ToolVersions.tags;
import static com.epam.pipeline.autotests.ao.ToolVersions.tagsHave;
import static com.epam.pipeline.autotests.utils.Conditions.valueContains;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ToolsTest
        extends AbstractSinglePipelineRunningTest
        implements Tools, Authorization {

    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultRegistryId = C.DEFAULT_REGISTRY_IP;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String dockerImage = String.format("%s/%s", defaultRegistryId, testingTool);
    private final String personalGroup = "personal";
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String command = "echo \"Hi, I'm nginx!\"";
    private final String disk = "23";
    private final String instanceType = C.DEFAULT_INSTANCE;
    private final String defaultCommand = "/start.sh";
    private final String toolWithoutDefaultSettings = C.TOOL_WITHOUT_DEFAULT_SETTINGS;

    @BeforeClass
    @AfterClass(alwaysRun = true)
    public void deletePersonalGroup() {
        open(C.ROOT_ADDRESS);
        loginAsAdminAndPerform(() ->
                tools().performWithin(defaultRegistry, personalGroup, group ->
                    group.sleep(1, SECONDS)
                            .performIf(CREATE_PERSONAL_GROUP, not(visible), deleteGroup(personalGroupActualName(C.LOGIN)))
                )
        );
    }

    @BeforeClass
    @AfterClass(alwaysRun = true)
    public void fallBackToDefaultToolSettings() {
        open(C.ROOT_ADDRESS);
        loginAsAdminAndPerform(() ->
                fallbackToToolDefaultState(defaultRegistry, defaultGroup, testingTool)
        );
    }

    @BeforeClass
    @TestCase({"EPMCMBIBPC-1404"})
    public void validateDeleteTool() {
        tools()
                .perform(defaultRegistry, defaultGroup, toolWithoutDefaultSettings, tool ->
                        tool.delete()
                                .ensureTitleIs("Are you sure you want to delete tool?")
                                .ensureVisible(CANCEL, OK)
                                .ok()
                                .searchToolByName(toolWithoutDefaultSettings)
                                .ensureToolIsNotPresent(toolWithoutDefaultSettings)
                );
    }

    @BeforeClass(dependsOnMethods = {"validateDeleteTool"})
    @TestCase({"EPMCMBIBPC-1407"})
    public void validateEnableTool() {
        tools()
                .perform(defaultRegistry, defaultGroup, group ->
                        group.enableTool(nameWithoutGroup(toolWithoutDefaultSettings))
                                .searchToolByName(toolWithoutDefaultSettings)
                                .ensureToolIsPresent(toolWithoutDefaultSettings)
                );
    }

    @Test(priority = 0)
    @TestCase({"EPMCMBIBPC-398"})
    public void toolsPageValidation() {
        tools().performWithin(defaultRegistry, defaultGroup, group ->
                group.ensure(REGISTRY, text(defaultRegistry))
                        .ensureVisible(SHOW_METADATA, SETTINGS)
                        .click(GROUP).sleep(500, MILLISECONDS).ensureVisible(GROUPS_LIST, GROUPS_SEARCH)
                        .resetMouse()
        );
    }

    @Test(dependsOnMethods = {"toolsPageValidation"})
    @TestCase({"EPMCMBIBPC-399"})
    public void fullToolNameSearch() {
        tools().performWithin(defaultRegistry, defaultGroup, group ->
                group.searchToolByName(testingTool)
                        .ensure(tool(testingTool), visible)
        );
    }

    @Test(dependsOnMethods = {"fullToolNameSearch"})
    @TestCase({"EPMCMBIBPC-401"})
    public void fullGroupNameSearch() {
        tools().registry(defaultRegistry, registry ->
                registry.click(GROUP).setValue(GROUPS_SEARCH, personalGroup)
                        .also(selectGroup(personalGroup))
        );
    }

    @Test(dependsOnMethods = {"fullGroupNameSearch"})
    @TestCase({"EPMCMBIBPC-403"})
    public void personalGroupCrudOperations() {
        final String description = "some description";

        tools().performWithin(defaultRegistry, personalGroup, group ->
                        group.createPersonalGroup()
                                .ensure(message("No tools found"), visible)
                                .editGroup(groupEdition ->
                                        groupEdition.ensureVisible(INFO, PERMISSIONS)
                                                .ensure(NAME, disabled)
                                                .ensure(DESCRIPTION, focused)
                                                .ensureVisible(CANCEL, SAVE)
                                                .addDescription(description)
                                                .ok()
                                )
                                .sleep(500, MILLISECONDS)
                                .editGroup(groupEdition -> groupEdition.ensure(DESCRIPTION, text(description)).cancel())
                                .deleteGroup(confirmGroupDeletion(personalGroupActualName(C.LOGIN)))
                )
                .performWithin(defaultRegistry, personalGroup, group ->
                        group.ensure(byText("Personal tool group was not found in registry."), visible)
                                .ensureVisible(CREATE_PERSONAL_GROUP)
                                .createPersonalGroup()
                                .ensure(message("No tools found"), visible)
                                .deleteGroup(confirmGroupDeletion(personalGroupActualName(C.LOGIN))))
                .performWithin(defaultRegistry, personalGroup, group ->
                        group.ensure(byText("Personal tool group was not found in registry."), visible)
                                .ensureVisible(CREATE_PERSONAL_GROUP)
                );
    }

    @Test(dependsOnMethods = {"personalGroupCrudOperations"})
    @TestCase({"EPMCMBIBPC-402"})
    public void partToolNameSearch() {
        final String partName = testingTool.substring(0, testingTool.length() / 2);

        tools().performWithin(defaultRegistry, defaultGroup, group ->
                group.searchToolByName(partName)
                        .sleep(2, SECONDS)
                        .ensureAll(toolsNames(), text(partName))
        );
    }

    @Test(dependsOnMethods = {"partToolNameSearch"})
    @TestCase({"EPMCMBIBPC-409"})
    public void toolLabelsAddition() {
        final String firstLabel = "pretty label";
        final String secondLabel = "prettier label";

        tools().performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                tool.settings()
                        .addLabel(firstLabel)
                        .ensure(label(firstLabel), visible)
                        .addLabel(secondLabel)
                        .ensure(label(secondLabel), visible)
                        .removeLabel(secondLabel)
                        .ensure(label(secondLabel), not(visible))
        );
    }

    @Test(dependsOnMethods = {"toolLabelsAddition"})
    @TestCase({"EPMCMBIBPC-432"})
    public void toolDescriptionAddition() {
        final String shortDescription = "short description";
        final String fullDescription = "full description";
        tools().performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                tool.addShortDescription(shortDescription)
                        .ensure(SHORT_DESCRIPTION, text(shortDescription))
                        .addFullDescription(fullDescription)
                        .ensure(FULL_DESCRIPTION, text(fullDescription))
        );
    }

    @Test(dependsOnMethods = {"toolDescriptionAddition"})
    @TestCase({"EPMCMBIBPC-433"})
    public void groupCrudOperations() {
        final String groupName = "group-" + Utils.randomSuffix();
        final String description = "description";
        final String anotherDescription = "another description";
        tools().registryWithin(defaultRegistry, registry ->
                registry
                        .createGroup(groupAddition ->
                                groupAddition.ensureVisible(INFO_TAB, NAME, DESCRIPTION, CANCEL, CREATE)
                                        .addName(groupName)
                                        .addDescription(description)
                                        .ok()
                        )
                        .sleep(1, SECONDS)
                        .group(groupName, group ->
                                group.ensure(byText("No tools found"), visible)
                                        .editGroup(groupEdition ->
                                                groupEdition.ensureVisible(INFO, PERMISSIONS)
                                                        .ensure(NAME, visible, disabled)
                                                        .ensure(DESCRIPTION, visible, enabled)
                                                        .ensureVisible(CANCEL, SAVE)
                                                        .addDescription(anotherDescription)
                                                        .ok()
                                        )
                                        .ensure(GROUP, text(groupName))
                                        .editGroup(groupEdition ->
                                                groupEdition.ensure(DESCRIPTION, text(anotherDescription))
                                                        .cancel()
                                        )
                                        .deleteGroup(confirmGroupDeletion(groupName))
                        )
        );
    }

    @Test(dependsOnMethods = {"groupCrudOperations"})
    @TestCase({"EPMCMBIBPC-412"})
    public void toolEditionMenu() {
        tools().perform(defaultRegistry, defaultGroup, testingTool, tool ->
                tool.ensure(BACK_TO_GROUP, visible)
                        .ensure(IMAGE_NAME, text(testingTool))
                        .ensureVisible(SHOW_METADATA, RUN)
                        .also(shouldContainOptions(DELETE, PERMISSIONS))
                        .ensureVisible(DESCRIPTION, VERSIONS, SETTINGS)
                        .ensure(SHORT_DESCRIPTION, visible)
                        .ensure(editButtonFor(SHORT_DESCRIPTION), visible)
                        .ensure(FULL_DESCRIPTION, visible)
                        .ensure(editButtonFor(FULL_DESCRIPTION), visible)
        );
    }

    @Test(dependsOnMethods = {"toolEditionMenu"})
    @TestCase({"EPMCMBIBPC-427"})
    public void toolVersionsTab() {
        tools().perform(defaultRegistry, defaultGroup, testingTool, tool ->
                tool.versions()
                        .sleep(2, SECONDS)
                        .ensureAll(tags(), sizeGreaterThanOrEqual(1))
                        .also(tagsHave(RUN, DELETE))
        );
    }

    @Test(dependsOnMethods = {"toolVersionsTab"})
    @TestCase({"EPMCMBIBPC-429"})
    public void toolExecutionParameters() {
        tools()
                .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                        tool.settings()
                                .setDisk(disk)
                                .setInstanceType(instanceType)
                                .setDefaultCommand(defaultCommand)
                                .save()
                )
                .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                        tool.settings()
                                .ensure(INSTANCE, text(instanceType))
                                .ensure(DISK, value(disk))
                                .ensure(DEFAULT_COMMAND, text(defaultCommand))
                );
    }

    @Test(dependsOnMethods = {"toolExecutionParameters"})
    @TestCase({"EPMCMBIBPC-430"})
    public void toolDefaultCommandAddition() {
        tools()
                .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                        tool.settings()
                                .setDefaultCommand(command)
                                .performIf(SAVE, enabled, ToolSettings::save)
                                .ensure(SAVE, disabled)
                )
                .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                        tool.settings()
                                .ensure(DEFAULT_COMMAND, text(command))
                );
    }

    @Test(dependsOnMethods = {"toolDefaultCommandAddition"})
    @TestCase({"EPMCMBIBPC-435"})
    public void toolDefaultSettingsRun() {
        tools().perform(defaultRegistry, defaultGroup, testingTool, runTool())
                .showLog(getRunId())
                .instanceParameters(parameters ->
                        parameters.ensure(IMAGE, text(dockerImage))
                                .ensure(DEFAULT_COMMAND, text(command))
                                .ensure(DISK, text(disk))
                                .ensure(TYPE, text(instanceType))
                )
                .waitForCompletion();
    }

    @Test(dependsOnMethods = {"toolDefaultSettingsRun"})
    @TestCase({"EPMCMBIBPC-436"})
    public void toolCustomSettingsRun() {
        final String timeout = "2";
        final String priceType = "On-demand";

        tools()
                .performWithin(defaultRegistry, defaultGroup, testingTool, setDefaultCommand(defaultCommand))
                .perform(defaultRegistry, defaultGroup, testingTool, runToolWithCustomSettings())
                .ensure(DOCKER_IMAGE, value(dockerImage))
                .ensure(DEFAULT_COMMAND, text(defaultCommand))
                .ensure(INSTANCE_TYPE, text(instanceType))
                .ensure(DISK, value(disk))
                .setPriceType(priceType)
                .setTimeOut(timeout)
                .launchTool(this, Utils.nameWithoutGroup(testingTool))
                .showLogForce(getRunId())
                .instanceParameters(parameters -> parameters.ensure(PRICE_TYPE, text(priceType)))
                .waitFor(LogAO.Status.FAILURE);
    }

    @Test(dependsOnMethods = {"toolCustomSettingsRun"})
    @TestCase({"EPMCMBIBPC-434"})
    public void toolDefaultCommandIsSavedOnlyForEditedTool() {
        tools().performWithin(defaultRegistry, defaultGroup, toolWithoutDefaultSettings, tool ->
                tool.settings().ensure(DEFAULT_COMMAND, empty)
        );
    }

    @Test(dependsOnMethods = {"toolDefaultCommandIsSavedOnlyForEditedTool"})
    @TestCase({"EPMCMBIBPC-404"})
    public void validateSwitchBetweenGroups() {
        tools().registryWithin(defaultRegistry, registry ->
            registry.ensureGroupsAreVisible()
                .resetMouse()
                .groupWithin(defaultGroup, group ->
                    group.ensureVisible(SHOW_METADATA, SETTINGS, SEARCH)
                            .ensure(GROUP, text(defaultGroup))
                            .ensureAll(toolsNames(), text(defaultGroup))));
        refresh();
    }

    @Test(priority = 10)
    @TestCase({"EPMCMBIBPC-1546"})
    public void addToolDescriptionAndChangeSettings() {
        final String description = String.format("description-%d", Utils.randomSuffix());

        tools().performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
            tool
                .addFullDescription(description)
                .settings()
                .setDisk("11")
                .click(SAVE)
                .description()
                .ensure(FULL_DESCRIPTION, text(description))
        );
    }

    @Test(priority = 10)
    @TestCase({"EPMCMBIBPC-1576"})
    public void createGroupWithInvalidName() {
        final String groupName = "!@#$%^&*()[]{}";
        tools().registryWithin(defaultRegistry, registry ->
                registry
                        .createGroup(groupAddition ->
                                groupAddition.addName(groupName)
                                        .checkForText("Image name should contain only " +
                                                "lowercase letters, digits, separators (-, ., _) " +
                                                "and should not start or end with a separator")
                                        .cancel()
                        )
        );
    }

    @Test(priority = 10)
    @TestCase({"EPMCMBIBPC-1645"})
    public void validateFirstGroupDefaultHasOpened() {
        logout();
        loginAs(user);
        tools().perform(defaultRegistry, defaultGroup, group ->
                group.ensureTextIsAbsent("Personal tool group was not found in registry.")
        );
        logout();
        loginAs(admin);
    }

    @Test(priority = 10)
    @TestCase({"EPMCMBIBPC-1296"})
    public void toolCustomSettingsRunWithoutDefaults() {
        final String priceType = "On-demand";
        final String image = String.format("%s/%s/%s",
                defaultRegistryId, defaultGroup, Utils.nameWithoutGroup(toolWithoutDefaultSettings)
        );

        tools()
                .perform(defaultRegistry, defaultGroup, toolWithoutDefaultSettings, tool ->
                        tool.settings()
                                .ensure(DISK, empty)
                                .ensure(INSTANCE_TYPE, text("Instance type"))
                                .ensure(DEFAULT_COMMAND, empty)
                                .runWithCustomSettings()
                )
                .ensure(LAUNCH, visible)
                .ensure(PIPELINE, text(nameWithoutGroup(toolWithoutDefaultSettings)))
                .ensure(VERSION, text("latest"))
                .ensure(ESTIMATED_PRICE, not(visible))
                .ensure(INFORMATION_ICON, not(visible))
                .expandTabs(EXEC_ENVIRONMENT, ADVANCED_PANEL, PARAMETERS_PANEL)
                .ensure(DOCKER_IMAGE, valueContains(image))
                .ensure(DEFAULT_COMMAND, empty)
                .ensure(INSTANCE_TYPE, text("Instance type"))
                .ensure(DISK, empty)
                .setDefaultLaunchOptions()
                .setPriceType(priceType)
                .setTimeOut("1")
                .setCommand("sleep infinity")
                .launchTool(this, Utils.nameWithoutGroup(toolWithoutDefaultSettings))
                .log(getRunId(), log ->
                        log
                                .instanceParameters(parameters ->
                                        parameters.ensure(DEFAULT_COMMAND, text("sleep infinity"))
                                                .ensure(PRICE_TYPE, text(priceType))
                                                .ensure(TYPE, text(PipelineRunFormAO.DEFAULT_TYPE))
                                                .ensure(DISK, diskSize(PipelineRunFormAO.DEFAULT_DISK))
                                )
                                .waitFor(LogAO.Status.FAILURE)
                );
    }

    @Test(dependsOnMethods = {"toolCustomSettingsRunWithoutDefaults"}, priority = 10)
    @TestCase({"EPMCMBIBPC-1436"})
    public void validateEstimatedPriceAvailabilityForFilledTool() {
        final String priceType = "On-demand";
        tools()
                .perform(defaultRegistry, defaultGroup, testingTool, tool ->
                        tool.settings()
                                .runWithCustomSettings()
                )
                .expandTab(EXEC_ENVIRONMENT)
                .ensure(INSTANCE_TYPE, not(empty))
                .ensure(DISK, not(empty))
                .ensure(LAUNCH, visible)
                .ensure(PIPELINE, text(nameWithoutGroup(testingTool)))
                .ensure(VERSION, text("latest"))
                .ensure(ESTIMATED_PRICE, visible)
                .ensure(INFORMATION_ICON, visible)
                .expandTab(ADVANCED_PANEL)
                .setPriceType(priceType)
                .hover(INFORMATION_ICON)
                .ensure(PRICE_TABLE, visible)
                .expandTab(PARAMETERS_PANEL)
                .clickAddStringParameter()
                .ensureVisible(PARAMETER_NAME, PARAMETER_VALUE, REMOVE_PARAMETER);
    }

    private By message(final String text) {
        return withText(text);
    }

    private Consumer<ToolsPage> registryIsPresented(final String registry) {
        return tools -> tools.get(REGISTRIES_LIST).shouldHave(text(registry));
    }

    private Consumer<Registry> selectGroup(final String defaultGroup) {
        return registry -> registry.get(GROUPS_LIST).find(button(defaultGroup)).click();
    }

    private Function<ToolDescription, RunsMenuAO> runTool() {
        return tool -> tool.run(this);
    }

    private Function<ToolDescription, PipelineRunFormAO> runToolWithCustomSettings() {
        return ToolTab::runWithCustomSettings;
    }

    private Consumer<ToolGroup> deleteGroup(final String groupName) {
        return group -> group.deleteGroup(confirmGroupDeletion(groupName));
    }

    public Consumer<ConfirmationPopupAO<Registry>> confirmGroupDeletion(final String groupName) {
        return confirmation ->
                confirmation.ensureTitleIs(String.format("Are you sure you want to delete '%s'?", groupName))
                        .sleep(1, SECONDS)
                        .ok();
    }

    private String personalGroupActualName(final String login) {
        return login.toLowerCase().replaceAll("[._@]", "-");
    }

    private Consumer<ToolDescription> shouldContainOptions(final Primitive... primitives) {
        return tool -> {
            tool.hover(TOOL_SETTINGS);
            Arrays.stream(primitives).forEach(primitive -> tool.ensure(primitive, visible));
        };
    }

    private Condition diskSize(final String disk) {
        return text(String.format("%s Gb", disk));
    }
}
