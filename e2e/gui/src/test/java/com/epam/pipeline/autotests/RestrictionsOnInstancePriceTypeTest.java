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

import com.epam.pipeline.autotests.ao.SettingsPageAO.UserManagementAO.UsersTabAO.UserEntry.EditUserPopup;
import com.epam.pipeline.autotests.ao.ToolDescription.InstanceManagementSectionAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import com.epam.pipeline.autotests.utils.listener.ConditionalTestAnalyzer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.appears;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.Configuration.confirmConfigurationChange;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.CODE_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.FOLDERS;
import static com.epam.pipeline.autotests.ao.Primitive.IMAGE;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.PRICE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.SHOW_METADATA;
import static com.epam.pipeline.autotests.ao.Primitive.TREE;
import static com.epam.pipeline.autotests.ao.Profile.advancedTab;
import static com.epam.pipeline.autotests.ao.Profile.execEnvironmentTab;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.attributesMenu;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.showInstanceManagement;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

@Listeners(value = ConditionalTestAnalyzer.class)
public class RestrictionsOnInstancePriceTypeTest extends AbstractBfxPipelineTest
        implements Navigation, Authorization {

    private final String instanceFamilyName = C.DEFAULT_INSTANCE_FAMILY_NAME;
    private final String defaultInstanceType = C.DEFAULT_INSTANCE;
    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String folder = "restrictionTestFolder-" + Utils.randomSuffix();
    private final String pipeline = "restrictionTestPipeline" + Utils.randomSuffix();
    private final String secondPipeline = "restrictionTestPipeline" + Utils.randomSuffix();
    private final String configuration = "restrictionTestConfiguration" + Utils.randomSuffix();
    private final String configuration1 = "restrictionTestConfiguration" + Utils.randomSuffix();
    private final String secondConfiguration = "restrictionTestConfiguration" + Utils.randomSuffix();
    private final String customDisk = "22";
    private final String configurationName = "customConfig";
    private final String testGroup = C.ROLE_USER;
    private final String instanceTypesMask = "Allowed instance types mask";
    private final String toolInstanceTypesMask = "Allowed tool instance types mask";
    private final String onDemandPrice = "On demand";
    private final String clusterAllowedPrice = "on_demand";
    private final String spotPriceName = C.SPOT_PRICE_NAME;
    private final String defaultClusterAllowedInstanceTypes = C.DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES;
    private final String defaultClusterAllowedPriceTypes = C.DEFAULT_CLUSTER_ALLOWED_PRICE_TYPES;
    private final String clusterAllowedMasks = C.CLUSTER_ALLOWED_MASKS;
    private final String clusterAllowedInstanceTypes = "cluster.allowed.instance.types";
    private final String clusterAllowedPriceTypes = "cluster.allowed.price.types";
    private final String clusterAllowedInstanceTypesDocker = "cluster.allowed.instance.types.docker";
    private int instanceTypesCount = 0;

    @BeforeClass
    public void initialLogout() {
        refresh();
        logout();
    }

    @AfterClass(alwaysRun = true)
    public void deletingEntities() {
        loginAs(admin);
        setMaskForUser(user.login, instanceTypesMask, "");
        setMaskForUser(user.login, toolInstanceTypesMask, "");
        setMaskForGroup(testGroup, instanceTypesMask, "");
        setClusterAllowedStringPreference(clusterAllowedInstanceTypes, defaultClusterAllowedInstanceTypes);
        setClusterAllowedStringPreference(clusterAllowedInstanceTypesDocker, defaultClusterAllowedInstanceTypes);
        setClusterAllowedStringPreference(clusterAllowedPriceTypes, defaultClusterAllowedPriceTypes);
        openEditUserTab(user.login)
                .clearAllowedPriceTypeField()
                .ok();
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .editGroup(testGroup)
                .clearAllowedPriceTypeField()
                .ok();
        tools()
                .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                        tool.showInstanceManagement(instanceManagement ->
                                instanceManagement
                                        .addAllowedToolInstanceTypesMask("")
                                        .clearAllowedPriceTypeField()
                                        .sleep(2, SECONDS)));
        library()
                .removeNotEmptyFolder(folder);
        logout();
    }

    @AfterMethod(alwaysRun = true)
    public void logoutUser() {
        logout();
    }

    @Test
    @TestCase({"EPMCMBIBPC-2637"})
    public void preparationForValidationOfInstanceTypesRestrictions() {
        loginAs(admin);
        library()
                .createFolder(folder)
                .clickOnFolder(folder)
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .selectByName(user.login)
                .showPermissions()
                .set(READ, ALLOW)
                .set(WRITE, ALLOW)
                .set(EXECUTE, ALLOW)
                .closeAll();
        library()
                .cd(folder)
                .createPipeline(pipeline)
                .clickOnDraftVersion(pipeline)
                .configurationTab()
                .editConfiguration("default", profile ->
                        instanceTypesCount = profile
                                .expandTab(EXEC_ENVIRONMENT)
                                .sleep(2, SECONDS)
                                .selectValue(INSTANCE_TYPE, defaultInstanceType)
                                .clear(NAME).setValue(NAME, configurationName)
                                .sleep(1, SECONDS)
                                .click(SAVE)
                                .sleep(3, SECONDS)
                                .ensureDisable(SAVE)
                                .expandTab(EXEC_ENVIRONMENT)
                                .ensure(INSTANCE_TYPE, text(defaultInstanceType))
                                .dropDownCount(INSTANCE_TYPE));
        library()
                .cd(folder)
                .createConfiguration(configuration)
                .configurationWithin(configuration, configuration ->
                        configuration
                                .expandTabs(execEnvironmentTab)
                                .setValue(DISK, customDisk)
                                .selectValue(INSTANCE_TYPE, defaultInstanceType)
                                .selectDockerImage(dockerImage ->
                                        dockerImage
                                                .selectRegistry(defaultRegistry)
                                                .selectGroup(defaultGroup)
                                                .selectTool(testingTool)
                                                .click(OK)
                                )
                                .click(SAVE)
                                .ensureDisable(SAVE)
                                .ensure(byText("Estimated price per hour:"), visible)
                                .ensure(INSTANCE_TYPE, text(defaultInstanceType)));
    }

    @Test(dependsOnMethods = {"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2638"})
    public void validationOfInstanceTypesRestrictionsExistingObjects() {
        try {
            loginAs(admin);
            setMaskForUser(user.login, instanceTypesMask, format("%s.*", instanceFamilyName));
            logout();
            validationOfInstanceTypesRestrictions();
        } finally {
            logout();
            loginAs(admin);
            setMaskForUser(user.login, instanceTypesMask, "");
        }
    }

    @Test(dependsOnMethods = {"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2639"})
    public void validationOfInstanceTypesRestrictionsCreatingObjects() {
        try {
            loginAs(admin);
            setMaskForUser(user.login, instanceTypesMask, format("%s.*", instanceFamilyName));
            logout();
            loginAs(user);
            library()
                    .cd(folder)
                    .createPipeline(secondPipeline)
                    .clickOnDraftVersion(secondPipeline)
                    .configurationTab()
                    .editConfiguration("default", profile ->
                            profile
                                    .expandTab(EXEC_ENVIRONMENT)
                                    .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName)
                    )
                    .click(CODE_TAB)
                    .exitFromConfigurationWithoutSaved();
            library()
                    .cd(folder)
                    .createConfiguration(secondConfiguration)
                    .configurationWithin(secondConfiguration, configuration ->
                            configuration
                                    .sleep(2, SECONDS)
                                    .expandTabs(execEnvironmentTab)
                                    .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName)
                    );
        } finally {
            logout();
            loginAs(admin);
            setMaskForUser(user.login, instanceTypesMask, "");
        }
    }

    @Test(dependsOnMethods = {"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2640"})
    public void validationOfInstanceTypesRestrictionsForUserGroup() {
        try {
            loginAs(admin);
            setMaskForGroup(testGroup, instanceTypesMask, format("%s.*", instanceFamilyName));
            logout();
            validationOfInstanceTypesRestrictions();
        } finally {
            logout();
            loginAs(admin);
            setMaskForGroup(testGroup, instanceTypesMask, "");
        }
    }

    @Test
    @TestCase({"EPMCMBIBPC-2641"})
    public void validationOfToolsInstanceTypesRestrictionsOverUserManagement() {
        try {
            loginAs(admin);
            setMaskForUser(user.login, toolInstanceTypesMask, format("%s.*", instanceFamilyName));
            logout();
            loginAs(user);
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .sleep(1, SECONDS)
                    .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName);
        } finally {
            logout();
            loginAs(admin);
            setMaskForUser(user.login, toolInstanceTypesMask, "");
        }
    }

    @Test
    @TestCase({"EPMCMBIBPC-2643"})
    public void validationOfToolsInstanceTypesRestrictionsForUserGroup() {
        try {
            loginAs(admin);
            setMaskForGroup(testGroup, toolInstanceTypesMask, format("%s.*", instanceFamilyName));
            logout();
            loginAs(user);
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .sleep(1, SECONDS)
                    .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName);
        } finally {
            logout();
            loginAs(admin);
            setMaskForGroup(testGroup, toolInstanceTypesMask, "");
        }
    }

    @Test
    @TestCase({"EPMCMBIBPC-2642"})
    public void validationOfToolsInstanceTypesRestrictionsOverInstanceManagement() {
        try {
            loginAs(admin);
            tools()
                    .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                            tool.showInstanceManagement(instanceManagement ->
                                    instanceManagement
                                            .addAllowedToolInstanceTypesMask(format("%s.*", instanceFamilyName))
                                            .clickApply()));
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName);
            logout();
            loginAs(user);
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName);
        } finally {
            logout();
            loginAs(admin);
            tools()
                    .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                            tool.showInstanceManagement(instanceManagement ->
                                    instanceManagement
                                            .addAllowedToolInstanceTypesMask("")
                                            .clickApply()));
        }
    }

    @Test(dependsOnMethods = {"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2644"})
    public void validationOfInstanceTypesRestrictionsSystemSettings() {
        try {
            loginAs(admin);
            library()
                    .cd(folder)
                    .createConfiguration(configuration1)
                    .configurationWithin(configuration1, configuration ->
                            configuration
                                    .selectPipeline(selection ->
                                            selection.ensureVisible(TREE, FOLDERS)
                                                    .selectFolder(folder)
                                                    .selectPipeline(pipeline)
                                                    .sleep(2, SECONDS)
                                                    .selectFirstVersion()
                                                    .ok()
                                                    .also(confirmConfigurationChange())
                                    )
                                    .setValue(DISK, customDisk)
                                    .sleep(3, SECONDS)
                                    .click(SAVE)
                                    .ensureDisable(SAVE)
                                    .ensure(byText("Estimated price per hour:"), visible))
                    .sleep(2, SECONDS);
            setClusterAllowedStringPreference(clusterAllowedInstanceTypes, format("%s.*", instanceFamilyName));
            logout();
            loginAs(user);
            library()
                    .cd(folder)
                    .clickOnDraftVersion(pipeline)
                    .configurationTab()
                    .editConfiguration(configurationName, profile ->
                            profile
                                    .expandTab(EXEC_ENVIRONMENT)
                                    .ensure(INSTANCE_TYPE, empty)
                                    .ensure(DISK, not(empty))
                                    .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName)
                    )
                    .click(CODE_TAB)
                    .exitFromConfigurationWithoutSaved();
            library()
                    .cd(folder)
                    .configurationWithin(configuration, configuration ->
                            configuration
                                    .expandTabs(execEnvironmentTab)
                                    .sleep(4, SECONDS)
                                    .ensure(DISK, value(customDisk))
                                    .ensure(IMAGE, value(defaultGroup), value(testingTool))
                                    .ensure(INSTANCE_TYPE, not(empty))
                                    .checkDropDownCount(INSTANCE_TYPE, instanceTypesCount)
                    );
            library()
                    .cd(folder)
                    .configurationWithin(configuration1, configuration ->
                            configuration
                                    .expandTabs(execEnvironmentTab)
                                    .sleep(4, SECONDS)
                                    .ensure(DISK, value(customDisk))
                                    .ensure(INSTANCE_TYPE, empty)
                                    .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName)
                    )
                    .sleep(5, SECONDS)
                    .exitFromConfigurationWithoutSaved();
        } finally {
            logout();
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedInstanceTypes, defaultClusterAllowedInstanceTypes);
        }
    }

    @Test
    @TestCase({"EPMCMBIBPC-2645"})
    public void validationOfToolsInstanceTypesRestrictionsSystemSettings() {
        try {
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedInstanceTypesDocker, format("%s.*", instanceFamilyName));
            logout();
            loginAs(user);
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName);
        } finally {
            logout();
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedInstanceTypesDocker,
                    defaultClusterAllowedInstanceTypes);
        }
    }

    @Test(dependsOnMethods = {"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2646"})
    public void validationOfInstanceTypesRestrictionsHierarchy() {
        try {
            String[] masks = clusterAllowedMasks.split(",");
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedInstanceTypes, format("%s*", masks[0]));
            setMaskForGroup(testGroup, instanceTypesMask, format("%s*", masks[1]));
            setMaskForUser(user.login, instanceTypesMask, format("%s*", masks[2]));
            setMaskForUser(user.login, toolInstanceTypesMask, format("%s*", masks[3]));
            logout();
            loginAs(user);
            library()
                    .cd(folder)
                    .clickOnDraftVersion(pipeline)
                    .configurationTab()
                    .editConfiguration(configurationName, profile ->
                            profile
                                    .expandTab(EXEC_ENVIRONMENT)
                                    .ensure(INSTANCE_TYPE, empty)
                                    .checkValueIsInDropDown(INSTANCE_TYPE, masks[2])
                    )
                    .click(CODE_TAB)
                    .exitFromConfigurationWithoutSaved();
            library()
                    .cd(folder)
                    .configurationWithin(configuration, configuration ->
                            configuration
                                    .expandTabs(execEnvironmentTab)
                                    .sleep(4, SECONDS)
                                    .ensure(DISK, value(customDisk))
                                    .ensure(IMAGE, value(defaultGroup), value(testingTool))
                                    .ensure(INSTANCE_TYPE, empty)
                                    .checkValueIsInDropDown(INSTANCE_TYPE, masks[3])
                    )
                    .exitFromConfigurationWithoutSaved();
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .sleep(1, SECONDS)
                    .checkValueIsInDropDown(INSTANCE_TYPE, masks[3]);
        } finally {
            logout();
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedInstanceTypes, defaultClusterAllowedInstanceTypes);
            setMaskForGroup(testGroup, instanceTypesMask, "");
            setMaskForUser(user.login, instanceTypesMask, "");
            setMaskForUser(user.login, toolInstanceTypesMask, "");
        }
    }

    @Test(dependsOnMethods = {"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2647"})
    public void validationOfToolsInstanceTypesRestrictionsHierarchy() {
        try {
            String[] masks = clusterAllowedMasks.split(",");
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedInstanceTypes, format("%s*", masks[0]));
            setClusterAllowedStringPreference(clusterAllowedInstanceTypesDocker, format("%s*", masks[1]));
            setMaskForUser(user.login, instanceTypesMask, format("%s*", masks[2]));
            setMaskForUser(user.login, toolInstanceTypesMask, format("%s*", masks[3]));
            tools()
                    .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                            tool.showInstanceManagement(instanceManagement ->
                                    instanceManagement
                                            .addAllowedToolInstanceTypesMask(format("%s.*", instanceFamilyName))
                                            .clickApply()));
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName);
            logout();
            loginAs(user);
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .checkValueIsInDropDown(INSTANCE_TYPE, masks[3]);
        } finally {
            logout();
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedInstanceTypes, defaultClusterAllowedInstanceTypes);
            setClusterAllowedStringPreference(clusterAllowedInstanceTypesDocker, defaultClusterAllowedInstanceTypes);
            setMaskForUser(user.login, instanceTypesMask, "");
            setMaskForUser(user.login, toolInstanceTypesMask, "");
            tools()
                    .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                            tool.showInstanceManagement(instanceManagement ->
                                    instanceManagement
                                            .addAllowedToolInstanceTypesMask("")
                                            .clickApply()));
        }
    }

    @CloudProviderOnly(values = {Cloud.AWS, Cloud.GCP})
    @Test(dependsOnMethods = {"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2648"})
    public void validationOfPriceTypesRestrictionsOverUserManagement() {
        try {
            loginAs(admin);
            openEditUserTab(user.login)
                    .setAllowedPriceType(onDemandPrice)
                    .ok();
            logout();
            loginAs(user);
            validationOfPriceTypesRestrictions(ON_DEMAND, ON_DEMAND, ON_DEMAND);
        } finally {
            logout();
            loginAs(admin);
            openEditUserTab(user.login)
                    .clearAllowedPriceTypeField()
                    .ok();
        }
    }

    @CloudProviderOnly(values = {Cloud.AWS, Cloud.GCP})
    @Test(dependsOnMethods = {"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2649"})
    public void validationOfPriceTypesRestrictionsForUserGroup() {
        try {
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToGroups()
                    .editGroup(testGroup)
                    .setAllowedPriceType(onDemandPrice)
                    .ok();
            logout();
            loginAs(user);
            validationOfPriceTypesRestrictions(ON_DEMAND, ON_DEMAND, ON_DEMAND);
        } finally {
            logout();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToGroups()
                    .editGroup(testGroup)
                    .clearAllowedPriceTypeField()
                    .ok();
        }
    }

    @CloudProviderOnly(values = {Cloud.AWS, Cloud.GCP})
    @Test
    @TestCase({"EPMCMBIBPC-2650"})
    public void validationOfPriceTypesRestrictionsOverInstanceManagement() {
        try {
            loginAs(admin);
            tools()
                    .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                            tool.showInstanceManagement(instanceManagement ->
                                    instanceManagement
                                            .clearAllowedPriceTypeField()
                                            .setPriceType(onDemandPrice)
                                            .clickApply()));
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .expandTab(ADVANCED_PANEL)
                    .ensurePriceTypeList(ON_DEMAND);
            logout();
            loginAs(user);
            tools()
                    .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                            tool
                                    .hover(SHOW_METADATA)
                                    .ensure(attributesMenu, appears)
                                    .ensure(showInstanceManagement, not(visible)));
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .expandTab(ADVANCED_PANEL)
                    .ensurePriceTypeList(ON_DEMAND);
        } finally {
            logout();
            loginAs(admin);
            tools()
                    .performWithin(defaultRegistry, defaultGroup, testingTool, tool ->
                            tool.showInstanceManagement(InstanceManagementSectionAO::clearAllowedPriceTypeField));
        }
    }

    @CloudProviderOnly(values = {Cloud.AWS, Cloud.GCP})
    @Test(dependsOnMethods = {"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2651"})
    public void validationOfPriceTypesRestrictionsSystemSettings() {
        try {
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedPriceTypes, clusterAllowedPrice);
            logout();
            loginAs(user);
            validationOfPriceTypesRestrictions(ON_DEMAND, ON_DEMAND, ON_DEMAND);
        } finally {
            logout();
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedPriceTypes, defaultClusterAllowedPriceTypes);
        }
    }

    @CloudProviderOnly(values = {Cloud.AWS, Cloud.GCP})
    @Test(dependsOnMethods = {"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2652"})
    public void validationOfPriceTypesRestrictionsHierarchy() {
        try {
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedPriceTypes, clusterAllowedPrice);
            tools()
                    .performWithin(defaultRegistry,defaultGroup,testingTool,tool->
                            tool.showInstanceManagement(instanceManagement->
                                    instanceManagement
                                            .clearAllowedPriceTypeField()
                                            .setPriceType(spotPriceName)
                                            .clickApply()));
            logout();
            loginAs(user);
            validationOfPriceTypesRestrictions(ON_DEMAND, ON_DEMAND, spotPriceName);
            logout();
            loginAs(admin);
            openEditUserTab(user.login)
                    .setAllowedPriceType(onDemandPrice)
                    .ok();
            logout();
            loginAs(user);
            validationOfPriceTypesRestrictions(ON_DEMAND, ON_DEMAND, ON_DEMAND);
        } finally {
            logout();
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedPriceTypes, defaultClusterAllowedPriceTypes);
            openEditUserTab(user.login)
                    .clearAllowedPriceTypeField()
                    .ok();
            tools()
                    .performWithin(defaultRegistry,defaultGroup,testingTool,tool->
                            tool.showInstanceManagement(instanceManagement->
                                    instanceManagement
                                            .clearAllowedPriceTypeField()
                                            .sleep(2,SECONDS)));
        }
    }

    private EditUserPopup openEditUserTab(String user) {
        return navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchForUserEntry(user)
                .edit();
    }

    private void setMaskForUser(String user, String mask, String value) {
        openEditUserTab(user)
                .addAllowedLaunchOptions(mask, value)
                .ok();
    }

    private void setMaskForGroup(String group, String mask, String value) {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToGroups()
                .editGroup(group)
                .addAllowedLaunchOptions(mask, value)
                .ok();
    }

    private void setClusterAllowedStringPreference(String pref, String value) {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToCluster()
                .setClusterAllowedStringPreference(pref, value)
                .saveIfNeeded();
    }

    private void validationOfInstanceTypesRestrictions() {
        loginAs(user);
        library()
                .cd(folder)
                .clickOnDraftVersion(pipeline)
                .configurationTab()
                .editConfiguration(configurationName, profile ->
                        profile
                                .expandTab(EXEC_ENVIRONMENT)
                                .ensure(INSTANCE_TYPE, empty)
                                .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName)
                )
                .click(CODE_TAB)
                .exitFromConfigurationWithoutSaved();
        library()
                .cd(folder)
                .configurationWithin(configuration, configuration ->
                        configuration
                                .expandTabs(execEnvironmentTab)
                                .sleep(4, SECONDS)
                                .ensure(DISK, value(customDisk))
                                .ensure(IMAGE, value(defaultGroup), value(testingTool))
                                .ensure(INSTANCE_TYPE, empty)
                                .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName)
                )
                .exitFromConfigurationWithoutSaved();
    }

    private void validationOfPriceTypesRestrictions(String pipelinePriceTypes, String configurationPriceTypes,
                                                    String toolPriceTypes) {
        library()
                .cd(folder)
                .clickOnDraftVersion(pipeline)
                .configurationTab()
                .editConfiguration(configurationName, profile ->
                        profile
                                .expandTab(ADVANCED_PANEL)
                                .checkValueIsInDropDown(PRICE_TYPE, pipelinePriceTypes));
        library()
                .cd(folder)
                .configurationWithin(configuration, configuration ->
                        configuration
                                .expandTabs(advancedTab)
                                .checkValueIsInDropDown(PRICE_TYPE, configurationPriceTypes));
        tools()
                .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                .expandTab(ADVANCED_PANEL)
                .checkValueIsInDropDown(PRICE_TYPE, toolPriceTypes);
    }
}
