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

import com.epam.pipeline.autotests.ao.AbstractPipelineTabAO;
import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.PipelineCodeTabAO;
import com.epam.pipeline.autotests.ao.PipelineConfigurationTabAO;
import com.epam.pipeline.autotests.ao.PipelineRunFormAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.ConfigurationProfile;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.have;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byValue;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.InstanceParameters.parameterWithName;
import static com.epam.pipeline.autotests.ao.LogAO.logMessage;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.ao.Profile.profileWithName;
import static com.epam.pipeline.autotests.utils.Conditions.collapsedTab;
import static com.epam.pipeline.autotests.utils.Conditions.contains;
import static com.epam.pipeline.autotests.utils.Conditions.expandedTab;
import static com.epam.pipeline.autotests.utils.Conditions.selectedValue;
import static com.epam.pipeline.autotests.utils.Json.selectProfileWithName;
import static com.epam.pipeline.autotests.utils.Json.transferringJsonToObject;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.collapsiblePanel;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.combobox;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.fieldWithLabel;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.hintOf;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.menu;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.menuitem;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static java.util.concurrent.TimeUnit.SECONDS;

public class PipelineConfigurationTest extends AbstractSeveralPipelineRunningTest {
    private final String configurationFileName = "config.json";
    private final String defaultConfigurationName = "default";
    private final String defaultPriceType = C.DEFAULT_INSTANCE_PRICE_TYPE;
    private final String defaultDisk = "15";
    private String configurationName = "test_conf";
    private final String pipeline795 = resourceName("epmcmbibpc-795");
    private final String pipeline1241 = resourceName("epmcmbibpc-1241");
    private final String pipeline1256 = resourceName("epmcmbibpc-1256");
    private final String pipeline1257 = resourceName("epmcmbibpc-1257");
    private final String pipeline1263 = resourceName("epmcmbibpc-1263");
    private final String pipeline1500 = resourceName("epmcmbibpc-1500");
    private final String spotPriceName = C.SPOT_PRICE_NAME;
    private final String onDemandPriceName = "On-demand";

    @AfterClass(alwaysRun = true)
    public void removePipelines() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .removePipelineIfExists(pipeline795)
                .removePipelineIfExists(pipeline1241)
                .removePipelineIfExists(pipeline1256)
                .removePipelineIfExists(pipeline1257)
                .removePipelineIfExists(pipeline1263)
                .removePipelineIfExists(pipeline1500);
    }

    @Test(enabled = false)
    @TestCase("EPMCMBIBPC-1256")
    public void changeInstancePriceTypeInConfigurationFile() {
        library()
            .createPipeline(Template.SHELL, pipeline1256)
            .clickOnPipeline(pipeline1256)
            .firstVersion()
            .onTab(PipelineCodeTabAO.class)
            .clickOnFile(configurationFileName)
            .editFile(transferringJsonToObject(profiles -> {
                final ConfigurationProfile profile = selectProfileWithName(defaultConfigurationName, profiles);
                profile.configuration.spot = false;
                return profiles;
            }))
            .saveAndCommitWithMessage("test: Set spot property of default configuration profile false")
            .sleep(2, SECONDS)
            .runPipeline()
            .expandTab(collapsiblePanel("Advanced"))
            .ensure(combobox("Price type"), have(selectedValue(onDemandPriceName)));
    }

    @Test
    @TestCase("EPMCMBIBPC-1257")
    public void changeInstancePriceTypeToSpotInConfigurationFile() {
        library()
            .createPipeline(Template.SHELL, pipeline1257)
            .clickOnPipeline(pipeline1257)
            .firstVersion()
            .onTab(PipelineCodeTabAO.class)
            .clickOnFile(configurationFileName)
            .editFile(transferringJsonToObject(profiles -> {
                final ConfigurationProfile profile = selectProfileWithName(defaultConfigurationName, profiles);
                profile.configuration.spot = true;
                return profiles;
            }))
            .saveAndCommitWithMessage("test: Set spot property of default configuration profile true")
            .runPipeline()
            .expandTab(collapsiblePanel("Advanced"))
            .ensure(combobox("Price type"), have(selectedValue(spotPriceName)));
    }

    @Test
    @TestCase("EPMCMBIBPC-1263")
    public void validationOfDefaultPriceType() {
        library()
            .createPipeline(pipeline1263)
            .clickOnPipeline(pipeline1263)
            .firstVersion()
            .runPipeline()
            .expandTab(collapsiblePanel("Advanced"))
            .click(combobox("Price type"))
            .ensure(menu(), contains(menuitem(spotPriceName), menuitem(onDemandPriceName)));
    }

    @Test
    @TestCase("EPMCMBIBPC-1241")
    public void validationOfRunPipelineUsingOnDemandInstance() {
        library()
            .createPipeline(Template.SHELL, pipeline1241)
            .clickOnPipeline(pipeline1241)
            .firstVersion()
            .runPipeline()
            .expandTab(collapsiblePanel("Advanced"))
            .click(combobox("Price type"))
            .ensure(menu(), contains(menuitem(spotPriceName), menuitem(onDemandPriceName)))
            .click(combobox("Price type"))
            .selectValue(combobox("Price type"), menuitem(onDemandPriceName))
            .launch(this)
            .showLog(getLastRunId())
            .instanceParameters(p -> p.ensure(parameterWithName("Price type"), have(text(onDemandPriceName))))
            .click(taskWithName("InitializeNode"))
            .ensure(logMessage(withActualRunId("Checking if instance already exists for RunID run_id")), visible);
        if (Cloud.AZURE.name().equalsIgnoreCase(C.CLOUD_PROVIDER)) {
            new LogAO()
                    .ensure(logMessage(withActualRunId("Create VMScaleSet with low priority instance for run: run_id")),
                            not(visible));
            return;
        } else if (Cloud.GCP.name().equalsIgnoreCase(C.CLOUD_PROVIDER)) {
            new LogAO()
                    .ensure(logMessage(withActualRunId("No existing instance found for RunID run_id")), visible)
                    .ensure(logMessage(withActualRunId("Preemptible instance with run id: run_id will be launched")),
                            not(visible));
            return;
        }
        new LogAO()
            .ensure(logMessage(withActualRunId("No existing instance found for RunID run_id")), visible)
            .ensure(logMessage(withActualRunId("Checking if spot request for RunID run_id already exists...")), visible)
            .ensure(logMessage(withActualRunId("No spot request for RunID run_id found")), visible)
            .ensure(logMessage(withActualRunId("Creating on demand instance")), visible);
    }

    @Test
    @TestCase("EPMCMBIBPC-1500")
    public void parametersDoesntChangeOnSwitchingConfigurations() {
        final String defaultProfile = "default";
        final String anotherProfile = "another";
        final String firstParameter = "firstParameter";
        final String firstParameterValue = "firstParameterValue";
        final String secondParameter = "secondParameter";
        final String secondParameterValue = "secondParameterValue";

        library()
            .createPipeline(Template.SHELL, pipeline1500)
            .clickOnPipeline(pipeline1500)
            .firstVersion()
            .configurationTab()
            .createConfiguration(anotherProfile)
            .editConfiguration(defaultProfile, profile -> {
                profile.clickAddStringParameter().setName(firstParameter).setValue(firstParameterValue);
                profile.click(SAVE);
            })
            .sleep(5, SECONDS)
            .editConfiguration(anotherProfile, profile -> {
                profile.clickAddPathParameter().setName(secondParameter).setValue(secondParameterValue);
                profile.click(SAVE);
            })
            .sleep(5, SECONDS)
            .refresh()
            .editConfiguration(defaultProfile, profile ->
                profile.ensure(byValue(firstParameter), visible)
                       .ensure(byValue(firstParameterValue), visible)
            )
            .editConfiguration(anotherProfile, profile ->
                profile.ensure(byValue(secondParameter), visible)
                       .ensure(byValue(secondParameterValue), visible)
            );
    }

    /**
     * {@link Test#priority()} is set to {@code 1} to prevent mixing of independent tests defined above
     * to the continuous chain of tests that starts with this test method.
     */
    @Test(priority = 1)
    @TestCase("EPMCMBIBPC-795")
    public void checkConfigurationTabOnPipelineEditPage() {
        navigationMenu()
                .library()
                .createPipeline(pipeline795)
                .clickOnPipeline(pipeline795)
                .draft()
                .configurationTab()
                .ensure(ADD_CONFIGURATION, visible, enabled)
                .ensure(profileWithName(defaultConfigurationName), visible)
                .editConfiguration(defaultConfigurationName, profile ->
                        profile.ensure(SAVE, visible)
                               .ensure(ESTIMATE_PRICE, visible)
                               .ensure(INSTANCE, expandedTab)
                               .ensure(EXEC_ENVIRONMENT, collapsedTab)
                               .ensure(PARAMETERS, expandedTab)
                               .ensure(ADD_PARAMETER, visible, enabled)
                );
    }

    @Test(priority = 1, dependsOnMethods = "checkConfigurationTabOnPipelineEditPage")
    @TestCase("EPMCMBIBPC-1517")
    public void priceTypeValidation() {
        onLaunchPage()
                .expandTab(collapsiblePanel("Advanced"))
                .ensure(combobox("Price type"), have(selectedValue(defaultPriceType)))
                .ensure(hintOf(fieldWithLabel("Price type")), visible)
                .ensure(byText("Timeout (min)"), visible)
                .ensure(hintOf(fieldWithLabel("Timeout (min)")), visible)
                .ensure(byText("Cmd template"), visible)
                .ensure(START_IDLE, visible)
                .ensure(hintOf(fieldWithLabel("Cmd template")), visible);
    }

    @Test(priority = 2, dependsOnMethods = "checkConfigurationTabOnPipelineEditPage")
    @TestCase("EPMCMBIBPC-796")
    public void validationOfAddingNewConfigurationFeature() {
        onPipelinePage()
                .onTab(PipelineConfigurationTabAO.class)
                .createConfiguration(popup -> popup.clear(NAME).setValue(NAME, configurationName).ensure(TEMPLATE, visible).ok())
                .ensure(profileWithName(configurationName), exist, visible)
                .onTab(PipelineCodeTabAO.class)
                .clickOnFile(configurationFileName)
                .shouldContainInCode(String.format("\"name\" : \"%s\"", configurationName))
                .close();
    }

    @Test(priority = 3, dependsOnMethods = "validationOfAddingNewConfigurationFeature")
    @TestCase("EPMCMBIBPC-797")
    public void validationOfEditNewConfiguration() {
        onPipelinePage()
                .onTab(PipelineConfigurationTabAO.class)
                .editConfiguration(configurationName, profile ->
                        profile.expandTab(EXEC_ENVIRONMENT)
                               .expandTab(ADVANCED_PANEL)
                               .clear(NAME).setValue(NAME, "conf")
                               .clear(DISK).setValue(DISK, "23")
                               .clear(TIMEOUT).setValue(TIMEOUT, "2")
                               .sleep(2, SECONDS)
                               .click(SAVE)
                )
                .messageShouldAppear(String.format("Updating '%s' configuration", configurationName))
                // Because the page refreshes
                .sleep(5, SECONDS)
                .onTab(PipelineCodeTabAO.class)
                .clickOnFile(configurationFileName)
                .shouldContainInCode("\"name\" : \"conf\"")
                .shouldContainInCode("\"instance_disk\" : \"23\"")
                .shouldContainInCode("\"timeout\" : 2")
                .close();
        configurationName = "conf";
    }

    @Test(priority = 4, dependsOnMethods = "validationOfEditNewConfiguration")
    @TestCase("EPMCMBIBPC-799")
    public void validationOfEditConfigurationInConfigurationFile() {
        final String newConf = "new_conf";
        onPipelinePage()
                .onTab(PipelineCodeTabAO.class)
                .clickOnFile(configurationFileName)
                .editFile(transferringJsonToObject(profiles -> {
                    final ConfigurationProfile profile = selectProfileWithName(configurationName, profiles);
                    profile.name = newConf;
                    profile.configuration.instanceDisk = defaultDisk;
                    profile.configuration.timeout = "1";
                    return profiles;
                }))
                .saveAndCommitWithMessage("test: Change configuration using config.json")
                .onTab(PipelineConfigurationTabAO.class)
                .ensure(profileWithName(newConf), exist.because("Profile was renamed during file editing."))
                .ensure(profileWithName("conf"), not(exist).because("Profile was renamed during file editing."))
                .editConfiguration(newConf, profile ->
                        profile.expandTab(EXEC_ENVIRONMENT)
                               .expandTab(ADVANCED_PANEL)
                                .ensure(NAME, value(newConf))
                                .ensure(DISK, value(defaultDisk))
                               .ensure(TIMEOUT, value("1"))
                );
        configurationName = newConf;
    }

    @Test(priority = 5, dependsOnMethods = "validationOfEditConfigurationInConfigurationFile")
    @TestCase("EPMCMBIBPC-800")
    public void launchPipelineThatHaveSeveralConfigurations() {
        onPipelinePage()
                .runPipeline()
                .chooseConfiguration(configurationName)
                .ensure(DISK, value(defaultDisk))
                .ensure(TIMEOUT, value("1"));
    }

    @Test(priority = 6, dependsOnMethods = "launchPipelineThatHaveSeveralConfigurations")
    @TestCase("EPMCMBIBPC-803")
    public void launchAndCheckLaunchParametersInPipelineLogsPage() {
        onLaunchPage()
                .launch(this)
                .showLog(getLastRunId())
                .instanceParameters(instance ->
                        instance.ensure(DISK, text(defaultDisk))
                                .ensure(TYPE, text(C.DEFAULT_INSTANCE))
                )
                .waitForCompletion();
    }

    @Test(priority = 7, dependsOnMethods = "launchAndCheckLaunchParametersInPipelineLogsPage")
    @TestCase("EPMCMBIBPC-804")
    public void validationOfSettingAsDefaultBehavior() {
        navigationMenu()
                .library()
                .clickOnPipeline(pipeline795)
                .draft()
                .configurationTab()
                .editConfiguration(configurationName, profile -> profile.click(SET_AS_DEFAULT))
                // Because the page refreshes
                .sleep(5, SECONDS)
                .createConfiguration(popup -> popup.ensure(TEMPLATE, text(configurationName)).cancel())
                .onTab(PipelineCodeTabAO.class)
                .clickOnFile(configurationFileName)
                .shouldContainInCode("\"default\" : true")
                .close();
    }

    @Test(priority = 8, dependsOnMethods = "validationOfSettingAsDefaultBehavior")
    @TestCase("EPMCMBIBPC-805")
    public void validationOfConfigurationRemoving() {
        final String expectedTitle = String.format(
                "Are you sure you want to remove configuration '%s'?", configurationName
        );
        final String deletionWasCancelled = String.format(
                "Configuration with name %s supposed exists as deletion was cancelled.", configurationName
        );
        final String deletionWasConfirmed = String.format(
                "Configuration with name %s supposed to be deleted.", configurationName
        );
        onPipelinePage()
                .onTab(PipelineConfigurationTabAO.class)
                .deleteConfiguration(configurationName, popup -> popup.cancel())
                .ensure(profileWithName(configurationName), exist.because(deletionWasCancelled))
                .deleteConfiguration(configurationName, popup -> popup.ensureTitleIs(expectedTitle).ok())
                .ensure(profileWithName(configurationName), not(exist).because(deletionWasConfirmed))
                .onTab(PipelineCodeTabAO.class)
                .clickOnFile(configurationFileName)
                .ensure(byText(configurationName), not(visible))
                .close();
    }

    private static AbstractPipelineTabAO<?> onPipelinePage() {
        return new PipelineCodeTabAO("");
    }

    private static PipelineRunFormAO onLaunchPage() {
        return new PipelineRunFormAO();
    }

    private String withActualRunId(final String message) {
        return message.replaceAll("run_id", getLastRunId());
    }
}
