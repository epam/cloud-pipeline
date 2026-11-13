/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.codeborne.selenide.WebDriverRunner;
import com.epam.pipeline.autotests.ao.AbstractPipelineTabAO;
import com.epam.pipeline.autotests.ao.ConfirmationPopupAO;
import com.epam.pipeline.autotests.ao.PipelineCodeTabAO;
import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.PipelineConfigurationTabAO;
import com.epam.pipeline.autotests.ao.PipelineRunFormAO;
import com.epam.pipeline.autotests.ao.Profile;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.ConfigurationProfile;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.ao.ParameterFieldAO.parameterByName;
import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.have;
import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byValue;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.webdriver;
import static com.codeborne.selenide.WebDriverConditions.url;
import static com.codeborne.selenide.WebDriverConditions.urlContaining;
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
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PipelineConfigurationTest extends AbstractSeveralPipelineRunningTest {
    private final String configurationFileName = "config.json";
    private final String defaultConfigurationName = "default";
    private final String defaultPriceType = C.DEFAULT_INSTANCE_PRICE_TYPE;
    private final String defaultDisk = "15";
    private String configurationName = "test_conf";
    private final String pipeline795 = resourceName("epmcmbibpc-795");
    private final String pipeline1241 = resourceName("epmcmbibpc-1241");
    private final String pipeline1256 = resourceName("epmcmbibpc-1256");
    private final String pipeline1263 = resourceName("epmcmbibpc-1263");
    private final String pipeline1500 = resourceName("epmcmbibpc-1500");
    private final String spotPriceName = C.SPOT_PRICE_NAME;
    private final String onDemandPriceName = ON_DEMAND;

    @AfterClass(alwaysRun = true)
    public void removePipelines() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .removePipelineIfExists(pipeline795)
                .removePipelineIfExists(pipeline1241)
                .removePipelineIfExists(pipeline1256)
                .removePipelineIfExists(pipeline1263)
                .removePipelineIfExists(pipeline1500);
    }

    @Test
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

    @Test(dependsOnMethods = "changeInstancePriceTypeInConfigurationFile")
    @TestCase("EPMCMBIBPC-1257")
    public void changeInstancePriceTypeToSpotInConfigurationFile() {
        library()
            .clickOnPipeline(pipeline1256)
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
            .createPipeline(Template.SHELL, pipeline1263)
            .clickOnPipeline(pipeline1263)
            .firstVersion()
            .runPipeline()
            .expandTab(collapsiblePanel("Advanced"))
            .click(combobox("Price type"))
            .ensure(menu(), contains(menuitem(spotPriceName), menuitem(onDemandPriceName)))
            .also(priceTypeMenuHasItems(2));
    }

    @Test
    @TestCase("EPMCMBIBPC-1241")
    public void validationOfRunPipelineUsingOnDemandInstance() {
        library()
            .createPipeline(Template.SHELL, pipeline1241)
            .clickOnPipeline(pipeline1241)
            .firstVersion()
            .runPipeline()
            .createNewNode()
            .expandTab(collapsiblePanel("Advanced"))
            .click(combobox("Price type"))
            .ensure(menu(), contains(menuitem(spotPriceName), menuitem(onDemandPriceName)))
            .also(priceTypeMenuHasItems(2))
            .click(combobox("Price type"))
            .selectValue(combobox("Price type"), menuitem(onDemandPriceName))
            .launch(this)
            .showLog(getLastRunId())
            .instanceParameters(p -> p.ensure(parameterWithName("Price type"), have(text(onDemandPriceName))))
            .waitForTask("InitializeNode")
            .click(taskWithName("InitializeNode"))
            .ensure(logMessage("- IsSpot: False"), visible);
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
            .resetMouse()
            .clickCreatePipelineButton()
            .setName(pipeline1500)
            .ok()
            .validatePopupClosed()
            .clickOnPipeline(pipeline1500)
            .firstVersion()
            .configurationTab()
            .createConfiguration(anotherProfile)
            .editConfiguration(anotherProfile, profile -> {
                profile.clickAddStringParameter().setName(firstParameter).setValue(firstParameterValue);
                profile.click(SAVE);
            })
            .sleep(5, SECONDS)
            .editConfiguration(defaultProfile, profile -> {
                profile.clickAddStringParameter().setName(secondParameter).setValue(secondParameterValue);
                profile.click(SAVE);
            })
            .sleep(5, SECONDS)
            .refresh()
            .editConfiguration(anotherProfile, profile ->
                profile.ensure(parameterByName(firstParameter), visible)
                       .ensure(byValue(firstParameterValue), visible)
            )
            .editConfiguration(defaultProfile, profile ->
                profile.ensure(parameterByName(secondParameter), visible)
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
        library()
                .resetMouse()
                .clickCreatePipelineButton()
                .setName(pipeline795)
                .ok()
                .validatePopupClosed()
                .clickOnPipeline(pipeline795)
                .draft()
                .configurationTab()
                .ensure(ADD_CONFIGURATION, visible, enabled)
                .ensure(profileWithName(defaultConfigurationName), visible)
                // the profile tabs are the only antd tabs of the configuration page
                .ensureAll(byClassName("ant-tabs-tab"), size(1))
                // the "default" profile is selected when the tab opens, so its sections show their initial state
                .also(() -> new Profile()
                        .ensure(NAME, visible, enabled, attribute("value", defaultConfigurationName))
                        .ensure(SAVE, visible)
                        // the label and the price value are in the same element
                        .ensure(ESTIMATE_PRICE, visible, matchText("\\d+\\.\\d{2}"))
                        .ensure(EXEC_ENVIRONMENT, collapsedTab)
                        .ensure(ADVANCED_PANEL, collapsedTab)
                        .ensure(PARAMETERS, expandedTab)
                        .ensure(ADD_PARAMETER, visible, enabled));
    }

    @Test(priority = 1, dependsOnMethods = "checkConfigurationTabOnPipelineEditPage")
    @TestCase("EPMCMBIBPC-1517")
    public void priceTypeValidation() {
        onLaunchPage()
                .ensure(collapsiblePanel("Advanced"), collapsedTab)
                .expandTab(collapsiblePanel("Advanced"))
                .ensure(combobox("Price type"), have(selectedValue(defaultPriceType)))
                .ensure(hintOf(fieldWithLabel("Price type")), visible)
                .ensure(TIMEOUT, visible)
                .ensure(byText("Timeout (min)"), visible)
                .ensure(hintOf(fieldWithLabel("Timeout (min)")), visible)
                .ensure(LIMIT_MOUNTS, visible)
                .ensure(hintOf(fieldWithLabel("Limit mounts")), visible)
                .ensure(byText("Cmd template"), visible)
                .ensure(START_IDLE, visible)
                // the form item of the Cmd template has no hint of its own: the one in it belongs to "Start idle"
                .ensure(hintOf(fieldWithLabel("Cmd template")), visible)
                .ensure(ADD_SYSTEM_PARAMETER, visible);
    }

    @Test(priority = 2, dependsOnMethods = "checkConfigurationTabOnPipelineEditPage")
    @TestCase("EPMCMBIBPC-796")
    public void validationOfAddingNewConfigurationFeature() {
        onPipelinePage()
                .onTab(PipelineConfigurationTabAO.class)
                .createConfiguration(popup -> popup.clear(NAME).setValue(NAME, configurationName)
                        .ensure(TEMPLATE, visible, not(empty)).ok())
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
                               .selectValue(INSTANCE_TYPE, C.DEFAULT_INSTANCE)
                               .sleep(2, SECONDS)
                               .click(SAVE)
                )
                .messageShouldAppear(String.format("Updating '%s' configuration", configurationName))
                // Because the page refreshes
                .sleep(5, SECONDS)
                .onTab(PipelineCodeTabAO.class)
                .clickOnFile(configurationFileName)
                .profileShouldHave("conf", profile -> {
                    assertFalse(profile.byDefault, "Configuration 'conf' should not be the default one.");
                    assertEquals(profile.configuration.instanceDisk, "23");
                    assertEquals(profile.configuration.timeout, "2");
                })
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
                .runPipelineWithConfiguration(configurationName)
                .ensure(DISK, attribute("value", defaultDisk))
                .ensure(TIMEOUT, attribute("value", "1"));
    }

    @Test(priority = 6, dependsOnMethods = "launchPipelineThatHaveSeveralConfigurations")
    @TestCase("EPMCMBIBPC-803")
    public void launchAndCheckLaunchParametersInPipelineLogsPage() {
        onLaunchPage()
		.setPriceType(onDemandPriceName)
                .launch(this)
                .showLog(getLastRunId())
                .instanceParameters(instance ->
                        instance.ensure(TYPE, text(C.DEFAULT_INSTANCE))
                                .ensure(DISK, matchText("^" + defaultDisk + "\\s?Gb$"))
                )
                .waitFor(LogAO.Status.SUCCESS);
    }

    @Test(priority = 7, dependsOnMethods = "launchAndCheckLaunchParametersInPipelineLogsPage")
    @TestCase("EPMCMBIBPC-804")
    public void validationOfSettingAsDefaultBehavior() {
        final String[] urlBeforeChange = new String[1];
        navigationMenu()
                .library()
                .clickOnPipeline(pipeline795)
                .draft()
                .configurationTab()
                .editConfiguration(configurationName, profile -> {
                    urlBeforeChange[0] = WebDriverRunner.url();
                    profile.click(SET_AS_DEFAULT);
                })
                // the page is reloaded for the new version of the pipeline
                .also(() -> webdriver().shouldNotHave(url(urlBeforeChange[0]))
                        .shouldHave(urlContaining("/configuration")))
                .createConfiguration(popup -> popup.ensure(TEMPLATE, text(configurationName)).cancel())
                .onTab(PipelineCodeTabAO.class)
                .clickOnFile(configurationFileName)
                .profileShouldHave(configurationName, profile ->
                        assertTrue(profile.byDefault, String.format("%s should be the default profile.", configurationName)))
                .profileShouldHave(defaultConfigurationName, profile ->
                        assertFalse(profile.byDefault,
                                String.format("%s should not be the default profile.", defaultConfigurationName)))
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
        final String[] urlBeforeRemoval = new String[1];
        onPipelinePage()
                .onTab(PipelineConfigurationTabAO.class)
                .deleteConfiguration(configurationName, ConfirmationPopupAO::cancel)
                .ensure(profileWithName(configurationName), exist.because(deletionWasCancelled))
                .deleteConfiguration(configurationName, popup -> {
                    urlBeforeRemoval[0] = WebDriverRunner.url();
                    popup.ensureTitleIs(expectedTitle).ok();
                })
                // the page is reloaded for the new version of the pipeline
                .also(() -> webdriver().shouldNotHave(url(urlBeforeRemoval[0])))
                .ensure(profileWithName(configurationName), not(exist).because(deletionWasConfirmed))
                .onTab(PipelineCodeTabAO.class)
                .clickOnFile(configurationFileName)
                .profileShouldNotExist(configurationName)
                .close();
    }

    private static AbstractPipelineTabAO<?> onPipelinePage() {
        return new PipelineCodeTabAO("");
    }

    private static PipelineRunFormAO onLaunchPage() {
        return new PipelineRunFormAO();
    }

    private static Runnable priceTypeMenuHasItems(final int count) {
        return () -> $(visible(menu())).findAll(byClassName("ant-select-dropdown-menu-item")).shouldHave(size(count));
    }
}
