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
import com.epam.pipeline.autotests.ao.Configuration;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import java.util.function.Consumer;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.focused;
import static com.codeborne.selenide.Condition.have;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.Configuration.FIRST_PARAMETER_INDEX;
import static com.epam.pipeline.autotests.ao.Configuration.confirmConfigurationChange;
import static com.epam.pipeline.autotests.ao.Configuration.priceType;
import static com.epam.pipeline.autotests.ao.Configuration.startIdle;
import static com.epam.pipeline.autotests.ao.Configuration.template;
import static com.epam.pipeline.autotests.ao.Configuration.timeout;
import static com.epam.pipeline.autotests.ao.DetachedConfigurationCreationPopup.templatesList;
import static com.epam.pipeline.autotests.ao.LogAO.InstanceParameters.parameterWithName;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.ParameterFieldAO.parameter;
import static com.epam.pipeline.autotests.ao.ParameterFieldAO.parameterByName;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.ao.Profile.activeProfileTab;
import static com.epam.pipeline.autotests.ao.Profile.advancedTab;
import static com.epam.pipeline.autotests.ao.Profile.execEnvironmentTab;
import static com.epam.pipeline.autotests.ao.Profile.parametersTab;
import static com.epam.pipeline.autotests.ao.Profile.profileWithName;
import static com.epam.pipeline.autotests.utils.Conditions.contains;
import static com.epam.pipeline.autotests.utils.Conditions.readOnlyEditor;
import static com.epam.pipeline.autotests.utils.Conditions.valueContains;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.configurationWithName;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.hintOf;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.inputOf;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.version;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public class DetachedConfigurationsTest
        extends AbstractSeveralPipelineRunningTest
        implements Navigation {

    private final String pipeline1 = "detached-conf-pipeline-1-" + Utils.randomSuffix();
    private final String pipeline2 = "detached-conf-pipeline-2-" + Utils.randomSuffix();
    private final String mainConfiguration = "configuration-" + Utils.randomSuffix();
    private final String configurationDescription = "configuration-description";
    private final String defaultConfigurationProfile = "default";
    private final String customConfigurationProfile = "custom-profile";
    private final String secondConfigurationProfile = "profile2";
    private final String configurationProfile1 = "profile-1";
    private final String configurationProfile1DiskSize = "19";
    private final String configurationDefaultProfileDiskSize = "17";
    private final String configurationProfile2 = "profile-2";

    private final String command = "/start.sh";

    private final String stringParameterName = "StringParameter";
    private final String stringParameterValue2 = "String parameter value2";
    private final String pathParameterName = "PathParameter";
    private final String pathParameterValue2 = "Path parameter value2";

    private final String pipelineDefaultProfile = "default";
    private final String defaultDisk = "21";
    private final String defaultInstanceType = C.DEFAULT_INSTANCE;
    private final String defaultPriceType = C.DEFAULT_INSTANCE_PRICE_TYPE;

    private final String pipelineCustomProfile = "custom-pipe-conf";
    private final String customDisk = "22";

    private final String pipelineProfile1611 = "pipe-profile-1611";
    private final String stringParameter = "stringParameter";
    private final String pathParameter = "pathParameter";
    private final String commonParameter = "commonParameter";
    private final String inputParameter = "inputParameter";
    private final String outputParameter = "outputParameter";
    private final String stringParameterValue = "stringParameterValue";
    private final String pathParameterValue = "pathParameterValue";
    private final String commonParameterValue = "commonParameterValue";
    private final String inputParameterValue = "inputParameterValue";
    private final String outputParameterValue = "outputParameterValue";

    private final String folder = "configuration-folder-" + Utils.randomSuffix();
    private final String folderConfiguration = "configuration-" + Utils.randomSuffix();

    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultRegistryUrl = C.DEFAULT_REGISTRY_IP;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String testingTool = C.TESTING_TOOL_NAME;

    private final String configuration1544 = "configuration-1544-" + Utils.randomSuffix();
    private final String configuration1494 = "configuration-1494-" + Utils.randomSuffix();
    private final String configuration1495 = "configuration-1595-" + Utils.randomSuffix();
    private final String configuration1495TempName = "configuration-1595-temp-name-" + Utils.randomSuffix();
    private final String runWithParametersConfiguration = "run-with-parameters-configuration-" + Utils.randomSuffix();
    private final String configuration1611 = "configuration-1611-" + Utils.randomSuffix();
    private final String configuration1601 = "configuration-1601-" + Utils.randomSuffix();

    @BeforeClass
    public void createPipelines() {
        library()
            .createPipeline(Template.SHELL, pipeline1)
            .createPipeline(Template.SHELL, pipeline2)
            .clickOnPipeline(pipeline1)
            .firstVersion()
            .configurationTab()
            .createConfiguration(pipelineCustomProfile)
            .createConfiguration(pipelineProfile1611)
            .editConfiguration(pipelineCustomProfile, profile ->
                profile.expandTabs(execEnvironmentTab, advancedTab, parametersTab)
                    .setValue(DISK, customDisk)
                    .setCommand(command)
                    .clickAddStringParameter()
                    .setName(stringParameterName)
                    .close()
                    .clickAddPathParameter()
                    .setName(pathParameterName)
                    .close()
                    .click(SAVE)
            )
            .sleep(5, SECONDS)
            .editConfiguration(pipelineDefaultProfile, profile ->
                profile.expandTab(EXEC_ENVIRONMENT)
                    .setValue(DISK, defaultDisk)
                    .click(SAVE)
            )
            .sleep(5, SECONDS)
            .editConfiguration(pipelineProfile1611, profile ->
                profile
                    .addStringParameter(stringParameter, stringParameterValue)
                    .addPathParameter(pathParameter, pathParameterValue)
                    .addCommonParameter(commonParameter, commonParameterValue)
                    .addInputParameter(inputParameter, inputParameterValue)
                    .addOutputParameter(outputParameter, outputParameterValue)
                    .click(SAVE)
                    .waitUntilSaveEnding(pipelineProfile1611)
            );
        library().clickRoot();
    }

    @BeforeClass
    public void createFolder() {
        library()
            .createFolder(folder);
    }

    @AfterClass(alwaysRun = true)
    public void deletePipelines() {
        open(C.ROOT_ADDRESS);
        library()
            .removePipeline(pipeline1)
            .removePipeline(pipeline2);
    }

    @AfterClass(alwaysRun = true)
    public void removeConfigurations() {
        open(C.ROOT_ADDRESS);
        library()
            .removeConfigurationIfExists(mainConfiguration)
            .removeConfigurationIfExists(configuration1544)
            .removeConfigurationIfExists(configuration1494)
            .removeConfigurationIfExists(configuration1495)
            .removeConfigurationIfExists(configuration1495TempName)
            .removeConfigurationIfExists(runWithParametersConfiguration)
            .removeConfigurationIfExists(configuration1611)
            .removeConfigurationIfExists(configuration1601);
    }

    @AfterClass(alwaysRun = true)
    public void deleteFolderWithConfiguration() {
        open(C.ROOT_ADDRESS);
        library()
            .cd(folder)
            .sleep(2, SECONDS)
            .performIf(configurationWithName(folderConfiguration), visible,
                    library -> library.removeConfigurationIfExists(folderConfiguration)
            )
            .removeFolder(folder);
    }

    @Test(priority = 1)
    @TestCase({"EPMCMBIBPC-1091", "EPMCMBIBPC-1185"})
    public void detachedConfigurationCreatingValidation() {
        sleep(5, SECONDS);

        library()
            .createConfiguration(configuration ->
                    configuration.setName(mainConfiguration).setDescription(configurationDescription).ok()
            )
            .ensure(configurationWithName(mainConfiguration), visible)
            .configurationWithin(mainConfiguration, configuration ->
                    configuration.edit(edition -> edition.ensure(DESCRIPTION, text(configurationDescription)).cancel())
            );
    }

    @Test(priority = 1, dependsOnMethods = {"detachedConfigurationCreatingValidation"})
    @TestCase({"EPMCMBIBPC-1112"})
    public void pipelineAdditionValidation() {
        library().configurationWithin(mainConfiguration, configuration ->
            configuration
                .selectPipeline(selection ->
                    selection.ensureVisible(TREE, FOLDERS)
                        .selectPipeline(pipeline1)
                        .sleep(2, SECONDS)
                        .ensure(version(), visible)
                        .selectFirstVersion()
                        .ensure(version(), isSelected())
                        .ok()
                        .also(confirmConfigurationChange())
                )
                .ensure(DISK, value(defaultDisk))
        );
    }

    @Test(priority = 1, dependsOnMethods = {"pipelineAdditionValidation"})
    @TestCase({"EPMCMBIBPC-1149"})
    public void groupAdditionValidation() {
        library().configurationWithin(mainConfiguration, configuration ->
            configuration.selectPipeline(pipeline1, pipelineCustomProfile)
                    .ensure(DISK, value(customDisk))
        );
    }

    @Test(priority = 1, dependsOnMethods = {"groupAdditionValidation"})
    @TestCase({"EPMCMBIBPC-1113"})
    public void exitWithoutSavingValidation() {
        library().configurationWithin(mainConfiguration, configuration ->
            configuration.expandTabs(execEnvironmentTab, advancedTab)
                .ensure(PIPELINE, empty)
                .ensure(IMAGE, empty)
                .ensure(INSTANCE_TYPE, Condition.or("Empty or placeholder", empty, text("Node type")))
                .ensure(PRICE_TYPE, text(defaultPriceType))
                .ensure(DISK, empty)
        );
    }

    @Test(priority = 1, dependsOnMethods = {"exitWithoutSavingValidation"})
    @TestCase({"EPMCMBIBPC-1114"})
    public void configurationSavingValidation() {
        library()
            .configurationWithin(mainConfiguration, configuration ->
                configuration.selectPipeline(pipeline1, pipelineDefaultProfile)
                    .click(SAVE)
            )
            .configurationWithin(mainConfiguration, configuration ->
                configuration.expandTabs(execEnvironmentTab, advancedTab)
                    .ensure(PIPELINE, valueContains(pipeline1))
                    .ensure(IMAGE, not(empty))
                    .ensure(INSTANCE_TYPE, text(defaultInstanceType))
                    .ensure(PRICE_TYPE, text(defaultPriceType))
                    .ensure(DISK, value(defaultDisk))
            );
    }

    @Test(priority = 1, dependsOnMethods = {"configurationSavingValidation"})
    @TestCase({"EPMCMBIBPC-1115"})
    public void configurationChangingValidation() {
        library()
            .configurationWithin(mainConfiguration, configuration ->
                configuration.expandTabs(execEnvironmentTab, advancedTab)
                    .selectPipeline(pipeline1, pipelineCustomProfile)
                    .ensure(PIPELINE, valueContains(pipeline1))
                    .ensure(IMAGE, not(empty))
                    .ensure(INSTANCE_TYPE, text(defaultInstanceType))
                    .ensure(PRICE_TYPE, text(defaultPriceType))
                    .ensure(DISK, value(customDisk))
            );
    }

    @Test(priority = 1, dependsOnMethods = {"configurationChangingValidation"})
    @TestCase({"EPMCMBIBPC-1130"})
    public void configurationRenamingValidation() {
        final String customProfile = "custom-profile";
        library()
            .configurationWithin(mainConfiguration, configuration ->
                configuration.sleep(1, SECONDS)
                    .setValue(NAME, customProfile)
                    .click(SAVE)
            )
            .configurationWithin(mainConfiguration, configuration ->
                configuration.ensure(NAME, value(customProfile))
            )
            .configurationWithin(mainConfiguration, configuration ->
                configuration.sleep(1, SECONDS)
                    .setValue(NAME, defaultConfigurationProfile)
                    .click(SAVE)
                    .ensure(NAME, value(defaultConfigurationProfile))
            );
    }

    @Test(priority = 1, dependsOnMethods = {"configurationRenamingValidation"})
    @TestCase({"EPMCMBIBPC-1132"})
    public void fieldsEditingProhibitionValidation() {
        library()
            .configurationWithin(mainConfiguration, configuration ->
                configuration.expandTabs(execEnvironmentTab, advancedTab)
                    .ensure(IMAGE, disabled)
                    .ensure(TEMPLATE, readOnlyEditor())
                    .ensure(START_IDLE, disabled)
                    .ensure(ADD_PARAMETER, disabled)
            );
    }

    @Test(priority = 1, dependsOnMethods = {"fieldsEditingProhibitionValidation"})
    @TestCase({"EPMCMBIBPC-1129"})
    public void secondConfigurationProfileAddition() {
        library()
            .configurationWithin(mainConfiguration, configuration ->
                configuration
                    .addProfile(customConfigurationProfile)
                    .ensure(activeProfileTab(), text(customConfigurationProfile))
                    .expandTabs(execEnvironmentTab, advancedTab)
                    .ensure(PIPELINE, valueContains(pipeline1))
                    .ensure(IMAGE, not(empty))
                    .ensure(INSTANCE_TYPE, text(defaultInstanceType))
                    .ensure(PRICE_TYPE, text(defaultPriceType))
                    .ensure(DISK, value(defaultDisk))
                    .ensureVisible(DELETE, SET_AS_DEFAULT)
                    .ensure(RUN, contains(byClassName("anticon-down")))
            );
    }

    @Test(priority = 1, dependsOnMethods = {"secondConfigurationProfileAddition"})
    @TestCase({"EPMCMBIBPC-1139"})
    public void changeSecondConfigurationProfilePipeline() {
        library()
            .configurationWithin(mainConfiguration, configuration ->
                configuration
                    .selectProfile(customConfigurationProfile)
                    .expandTabs(execEnvironmentTab, advancedTab)
                    .selectPipeline(pipeline2)
                    .click(SAVE)
            )
            .refresh()
            .configurationWithin(mainConfiguration, configuration ->
                configuration
                    .selectProfile(customConfigurationProfile)
                    .expandTabs(execEnvironmentTab, advancedTab)
                    .ensure(PIPELINE, valueContains(pipeline2))
            );
    }

    @Test(priority = 1, dependsOnMethods = {"changeSecondConfigurationProfilePipeline"})
    @TestCase({"EPMCMBIBPC-1144"})
    public void configurationProfileSetAsDefaultBehaviour() {
        library()
            .configurationWithin(mainConfiguration, configuration ->
                configuration
                    .addProfile(profile ->
                        profile.setName(configurationProfile1).ensure(TEMPLATE, text(defaultConfigurationProfile)).ok())
                    .selectProfile(configurationProfile1)
                    .expandTabs(execEnvironmentTab, advancedTab)
                    .setValue(DISK, configurationProfile1DiskSize)
                    .click(SAVE)
                    .click(SET_AS_DEFAULT)
                    .addProfile(profile ->
                        profile.setName(configurationProfile2).ensure(TEMPLATE, text(configurationProfile1)).ok())
                    .selectProfile(configurationProfile2)
                    .expandTabs(execEnvironmentTab, advancedTab)
                    .ensure(DISK, value(configurationProfile1DiskSize))
            )
            .configurationWithin(mainConfiguration, configuration ->
                configuration
                    .selectProfile(configurationProfile2)
                    .expandTabs(execEnvironmentTab, advancedTab)
                    .ensure(DISK, value(configurationProfile1DiskSize))
            );
    }

    @Test(priority = 1, dependsOnMethods = {"configurationProfileSetAsDefaultBehaviour"})
    @TestCase({"EPMCMBIBPC-1145"})
    public void templatesListContainsAllProfiles() {
        library()
            .configurationWithin(mainConfiguration, configuration ->
                configuration.addProfile(profile ->
                    profile.click(TEMPLATE)
                        .sleep(1, SECONDS)
                        .ensure(templatesList(), contains(
                            byText(configurationProfile1),
                            byText(configurationProfile2),
                            byText(customConfigurationProfile)
                        ))
                        .click(TEMPLATE)
                        .cancel()
                )
            );
    }

    @Test(priority = 2, dependsOnMethods = {"detachedConfigurationCreatingValidation"})
    @TestCase({"EPMCMBIBPC-1516"})
    public void priceTypeValidation() {
        library()
            .configurationWithin(mainConfiguration, configuration ->
                configuration.expandTabs(execEnvironmentTab, advancedTab)
                    .ensure(priceType(), visible)
                    .ensure(timeout(), visible)
                    .ensure(template(), visible)
                    .ensure(inputOf(startIdle()), exist)
                    .ensure(hintOf(priceType()), visible)
                    .ensure(hintOf(timeout()), visible)
                    .ensure(hintOf(startIdle()), visible)
                    .ensure(priceType(), text(defaultPriceType))
            );
    }

    @Test(priority = 2, dependsOnMethods = {"detachedConfigurationCreatingValidation"})
    @TestCase({"EPMCMBIBPC-1496"})
    public void detachedConfigurationProfileAdditionPopupFocusValidation() {
        final String profileName = "focus-validation-profile";

        library()
            .configurationWithin(mainConfiguration, configuration ->
                configuration
                    .addProfile(profile -> profile.ensure(NAME, focused).setName(profileName).enter())
                    .ensure(profileWithName(profileName), visible)
            );
    }

    @Test
    @TestCase({"EPMCMBIBPC-1601"})
    public void addParameterToDetachedConfiguration() {
        final String parameterName = "name";
        final String parameterValue = "value";

        refresh();
        library()
            .createConfiguration(configuration1601)
            .configurationWithin(configuration1601, configuration -> configuration.expandTabs(parametersTab)
                .addStringParameter(parameterName, parameterValue)
                .also(ensureParameterExists(FIRST_PARAMETER_INDEX, parameterName, parameterValue))
                .resetChanges()
            );
    }

    @Test
    @TestCase({"EPMCMBIBPC-1544"})
    public void changesValidationInAttachedPipelineConfiguration() {
        final String diskSize = "18";
        final String instanceType = C.DEFAULT_INSTANCE;
        final String priceType = "On-demand";

        library()
            .refresh()
            .createConfiguration(configuration1544)
            .configurationWithin(configuration1544, configuration ->
                configuration.selectPipeline(pipeline1)
                    .expandTabs(advancedTab)
                    .setValue(DISK, diskSize)
                    .selectValue(INSTANCE_TYPE, instanceType)
                    .selectValue(PRICE_TYPE, priceType)
                    .click(SAVE)
            )
            .configurationWithin(configuration1544, configuration ->
                configuration
                    .expandTabs(execEnvironmentTab, advancedTab)
                    .ensure(DISK, value(diskSize))
                    .ensure(INSTANCE_TYPE, text(instanceType))
                    .ensure(PRICE_TYPE, text(priceType))
            );
    }

    @Test
    @TestCase({"EPMCMBIBPC-1493"})
    public void detachedConfigurationCreatingPopupFocusValidation() {
        library()
            .cd(folder)
            .createConfiguration(configuration ->
                configuration.ensure(NAME, focused).setName(folderConfiguration).enter())
            .ensure(configurationWithName(folderConfiguration), visible);
    }

    @Test
    @TestCase({"EPMCMBIBPC-1494"})
    public void dockerImageSelection() {
        library()
            .createConfiguration(configuration1494)
            .configurationWithin(configuration1494, configuration ->
                configuration
                    .selectDockerImage(dockerImage ->
                        dockerImage
                            .selectRegistry(defaultRegistry)
                            .selectGroup(defaultGroup)
                            .selectTool(testingTool, "test")
                            .click(OK)
                    )
                    .ensure(IMAGE, valueContains(String.format("%s/%s:test", defaultRegistryUrl, testingTool)))
            );
    }

    @Test
    @TestCase({"EPMCMBIBPC-1495"})
    public void detachedConfigurationEditionPopupFocusValidation() {
        library()
            .createConfiguration(configuration1495)
            .configurationWithin(configuration1495, configuration ->
                configuration.edit(edition -> edition.setValue(NAME, configuration1495TempName).enter())
            )
            .refresh()
            .ensure(configurationWithName(configuration1495TempName), visible)
            .configurationWithin(configuration1495TempName, configuration ->
                configuration.edit(edition -> edition.setValue(NAME, configuration1495).enter())
            )
            .refresh();
    }

    @Test
    @TestCase({"EPMCMBIBPC-1611"})
    public void validationOfConfigWithNonEmptyParametersInDetachConfiguration() {
        library()
            .refresh()
            .createConfiguration(configuration1611)
            .configurationWithin(configuration1611, configuration ->
                configuration.selectPipeline(pipeline1, pipelineProfile1611)
                    .click(SAVE)
            )
            .refresh()
            .configurationWithin(configuration1611, configuration ->
                configuration
                    .expandTabs(parametersTab)
                    .ensure(parameter(stringParameter, stringParameterValue), exist)
                    .ensure(parameter(pathParameter, pathParameterValue), exist)
                    .ensure(parameter(commonParameter, commonParameterValue), exist)
                    .ensure(parameter(inputParameter, inputParameterValue), exist)
                    .ensure(parameter(outputParameter, outputParameterValue), exist)
            );
    }

    @Test(priority = 3)
    @TestCase("EPMCMBIBPC-1140")
    public void validationOfConfigWithEmptyParametersInDetachConfiguration() {
        refresh();
        library()
            .createConfiguration(runWithParametersConfiguration)
            .configurationWithin(runWithParametersConfiguration, configuration ->
                    configuration.selectPipeline(pipeline1)
                            .click(SAVE)
                            .addProfile(secondConfigurationProfile)
                            .selectPipeline(pipeline1, pipelineCustomProfile)
                            .click(SAVE)
                            .expandTabs(execEnvironmentTab, advancedTab, parametersTab)
                            .getParameterByIndex(FIRST_PARAMETER_INDEX)
                            .validateParameter(stringParameterName, "")
                            .setValue(stringParameterValue2)
                            .validateParameter(stringParameterName, stringParameterValue2)
                            .close()
                            .getParameterByIndex(FIRST_PARAMETER_INDEX + 1)
                            .validateParameter(pathParameterName, "")
                            .setValue(pathParameterValue2)
                            .validateParameter(pathParameterName, pathParameterValue2)
                            .ensure(PARAMETER_NAME, disabled)
                            .click(SAVE)
            );
    }

    @Test(priority = 3, dependsOnMethods = "validationOfConfigWithEmptyParametersInDetachConfiguration")
    @TestCase("EPMCMBIBPC-1141")
    public void checkPipelineConfigAfterChangesInDetachedConfig() {
        library()
            .clickOnPipeline(pipeline1)
            .firstVersion()
            .configurationTab()
            .editConfiguration(pipelineCustomProfile, profile ->
                profile.expandTab(PARAMETERS)
                    .ensure(parameterByName(stringParameterName).valueInput, value(""))
                    .ensure(parameterByName(pathParameterName).valueInput, value(""))
                    .getParameterByIndex(FIRST_PARAMETER_INDEX)
            );
    }

    @Test(priority = 3, dependsOnMethods = "checkPipelineConfigAfterChangesInDetachedConfig")
    @TestCase("EPMCMBIBPC-1151")
    public void validateDetachConfigBehaviorIfUserChangesBasePipelineConfig() {
        library()
            .clickOnPipeline(pipeline1)
            .firstVersion()
            .configurationTab()
            .editConfiguration(pipelineCustomProfile, profile -> profile.expandTab(PARAMETERS)
                .removeParameter(parameterByName(stringParameterName))
                .removeParameter(parameterByName(pathParameterName))
                .click(SAVE)
            );
        refresh();
        library()
            .configurationWithin(runWithParametersConfiguration, configuration -> {
                configuration.selectProfile(secondConfigurationProfile)
                    .expandTabs(execEnvironmentTab, advancedTab, parametersTab)
                    .ensure(parameter(stringParameterName, stringParameterValue2), exist)
                    .ensure(parameter(pathParameterName, pathParameterValue2), exist);
                configuration.selectProfile(defaultConfigurationProfile)
                    .expandTab(PARAMETERS)
                    .ensureThereIsNoParameters();
                configuration.selectProfile(secondConfigurationProfile)
                    .click(SET_AS_DEFAULT)
                    .runCluster(this, pipeline1)
                    .openClusterRuns(getLastRunId())
                    .showLog(getLastRunId())
                    .expandTab(PARAMETERS)
                    .ensure(configurationParameter(stringParameterName, stringParameterValue2), exist)
                    .ensure(configurationParameter(pathParameterName, pathParameterValue2), exist);
                runsMenu()
                    .openClusterRuns(getLastRunId())
                    .showLogForChildRun(getLastRunId())
                    .expandTab(PARAMETERS)
                    .ensure(configurationParameter("parent-id", getLastRunId()), exist);
                library();
            });
        runsMenu()
                .stopRun(getLastRunId());
    }

    @Test(priority = 3, dependsOnMethods = "validateDetachConfigBehaviorIfUserChangesBasePipelineConfig")
    @TestCase("EPMCMBIBPC-1146")
    public void validateRunSingleConfigFromDetachedConfiguration() {
        library()
            .configurationWithin(runWithParametersConfiguration, configuration -> {
                configuration.selectProfile(defaultConfigurationProfile)
                    .expandTab(EXEC_ENVIRONMENT)
                    .setValue(DISK, configurationDefaultProfileDiskSize)
                    .click(SAVE)
                    .runSelected(this, pipeline1)
                    .showLog(getLastRunId())
                    .expandTab(INSTANCE)
                    .instanceParameters(p ->
                            p.ensure(DISK, text(configurationDefaultProfileDiskSize)));
                library();
            });
        runsMenu()
            .stopRun(getLastRunId());
    }

    @Test(priority = 3, dependsOnMethods = "validateRunSingleConfigFromDetachedConfiguration")
    @TestCase("EPMCMBIBPC-1147")
    public void validateRunClusterFromDetachConfiguration() {
        library()
            .configurationWithin(runWithParametersConfiguration, configuration -> {
                configuration.selectProfile(secondConfigurationProfile)
                    .expandTab(EXEC_ENVIRONMENT)
                    .selectPipeline(pipeline2)
                    .click(SAVE)
                    .runCluster(this, pipeline2)
                    .showLog(getLastRunId())
                    .validateRunTitle(pipeline2);
                runsMenu()
                    .openClusterRuns(getLastRunId())
                    .showLogForChildRun(getLastRunId())
                    .validateRunTitle(pipeline1);
                library();
            });
        runsMenu()
            .stopRun(getLastRunId());
    }

    @Test(priority = 3, dependsOnMethods = "validateRunClusterFromDetachConfiguration")
    @TestCase("EPMCMBIBPC-1545")
    public void validationPriceTypeFieldForClusterRun() {
        final String onDemandPriceType = "On-demand";
        library()
            .configurationWithin(runWithParametersConfiguration, configuration -> {
                configuration
                    .selectProfile(defaultConfigurationProfile)
                    .expandTab(INSTANCE)
                    .selectValue(PRICE_TYPE, defaultPriceType)
                    .click(SAVE)
                    .selectProfile(secondConfigurationProfile)
                    .expandTabs(execEnvironmentTab, advancedTab)
                    .selectPipeline(pipeline1)
                    .selectValue(PRICE_TYPE, onDemandPriceType)
                    .click(SAVE)
                    .runCluster(this, pipeline1)
                    .showLog(getLastRunId())
                    .instanceParameters(p -> p.ensure(parameterWithName("Price type"), have(text(onDemandPriceType))));

                runsMenu()
                    .openClusterRuns(getLastRunId())
                    .showLogForChildRun(getLastRunId())
                    .instanceParameters(p -> p.ensure(parameterWithName("Price type"), have(text(defaultPriceType))));
                library();
            });
        runsMenu()
                .stopRun(getLastRunId());
    }

    // Test should be run after all others because it removes main configuration
    @Test(priority = 100)
    @TestCase({"EPMCMBIBPC-1604"})
    public void deleteDetachedConfigurationValidation() {
        library()
            .removeConfiguration(mainConfiguration)
            .ensure(configurationWithName(mainConfiguration), not(visible));
    }

    private Consumer<Configuration> ensureParameterExists(final int index,
                                                          final String parameterName,
                                                          final String parameterValue) {
        return configuration -> configuration.getParameterByIndex(index)
                .validateParameter(parameterName, parameterValue);
    }

    private Condition isSelected() {
        return new Condition("pipeline version is selected") {
            @Override
            public boolean apply(final WebElement element) {
                return !element.findElements(byClassName("anticon-check-circle")).isEmpty();
            }

            @Override
            public String toString() {
                return "pipeline version is selected";
            }
        };
    }
}
