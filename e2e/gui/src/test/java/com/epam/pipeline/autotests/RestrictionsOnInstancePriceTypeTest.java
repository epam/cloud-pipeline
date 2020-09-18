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

import com.epam.pipeline.autotests.ao.SettingsPageAO.UserManagementAO.UsersTabAO.UserEntry.EditUserPopup;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.appears;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.value;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.ao.Primitive.SHOW_METADATA;
import static com.epam.pipeline.autotests.ao.Profile.advancedTab;
import static com.epam.pipeline.autotests.ao.Profile.execEnvironmentTab;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.attributesMenu;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.showInstanceManagement;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
import com.epam.pipeline.autotests.ao.ToolDescription.InstanceManagementSectionAO;

import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

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
    private final String testRole = "ROLE_USER";
    private final String instanceTypesMask = "Allowed instance types mask";
    private final String toolInstanceTypesMask = "Allowed tool instance types mask";
    private final String onDemandPrice = "On demand";
    private final String clusterAllowedPrice = "on_demand";
    private final String allowedPriceTypes="Allowed price types";
    private final String spotPriceName=C.SPOT_PRICE_NAME;
    private final String defaultClusterAllowedInstanceTypes = C.DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES;
    private final String defaultClusterAllowedPriceTypes = C.DEFAULT_CLUSTER_ALLOWED_PRICE_TYPES;
    private final String clusterAllowedPriceTypesValue = C.CLUSTER_ALLOWED_PRICE_TYPES_VALUE;
    private final String clusterAllowedMasks = C.CLUSTER_ALLOWED_MASKS;
    private final String clusterAllowedInstanceTypes = "cluster.allowed.instance.types";
    private final String clusterAllowedPriceTypes = "cluster.allowed.price.types";
    private final String clusterAllowedInstanceTypesDocker = "cluster.allowed.instance.types.docker";


    @BeforeClass
    public void initialLogout() {
        logout();
    }

    @AfterClass(alwaysRun = true)
    public void deletingEntities() {
        loginAs(admin);
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
                            profile
                                    .expandTab(EXEC_ENVIRONMENT)
                                    .selectValue(INSTANCE_TYPE, defaultInstanceType)
                                    .clear(NAME).setValue(NAME, configurationName)
                                    .sleep(1, SECONDS)
                                    .click(SAVE)
                                    .sleep(3, SECONDS)
                                    .expandTab(EXEC_ENVIRONMENT)
                                    .ensure(INSTANCE_TYPE, text(defaultInstanceType))
                    );
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
            setMaskForRole(testRole, instanceTypesMask, format("%s.*", instanceFamilyName));
            logout();
            validationOfInstanceTypesRestrictions();
        } finally {
            logout();
            loginAs(admin);
            setMaskForRole(testRole, instanceTypesMask, "");
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
            setMaskForRole(testRole, toolInstanceTypesMask, format("%s.*", instanceFamilyName));
            logout();
            loginAs(user);
            tools()
                    .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                    .sleep(1, SECONDS)
                    .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName);
        } finally {
            logout();
            loginAs(admin);
            setMaskForRole(testRole, toolInstanceTypesMask, "");
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
                                            .clickApply()
                                            .sleep(2, SECONDS)));
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
                                            .clickApply()
                                            .sleep(2, SECONDS)));
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
            setMaskForRole(testRole, instanceTypesMask, format("%s*", masks[1]));
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
                                    .ensure(DOCKER_IMAGE, value(defaultGroup), value(testingTool))
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
            setMaskForRole(testRole, instanceTypesMask, "");
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
                                            .clickApply()
                                            .sleep(2, SECONDS)));
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
                                            .clickApply()
                                            .sleep(2, SECONDS)));
        }
    }

    @CloudProviderOnly(values={Cloud.AWS,Cloud.GCP})
    @Test(dependsOnMethods={"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2648"})
    public void validationOfPriceTypesRestrictionsOverUserManagement() {
        try {
            loginAs(admin);
            openEditUserTab(user.login)
                    .setAllowedPriceType(onDemandPrice)
                    .ok();
            logout();
            loginAs(user);
            validationOfPriceTypesRestrictions("On-demand", "On-demand", "On-demand");
        } finally {
            logout();
            loginAs(admin);
            openEditUserTab(user.login)
                    .clearAllowedPriceTypeField()
                    .ok();
        }
    }

    @CloudProviderOnly(values={Cloud.AWS,Cloud.GCP})
    @Test(dependsOnMethods={"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2649"})
    public void validationOfPriceTypesRestrictionsForUserGroup() {
        try {
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToRoles()
                    .editRole(testRole)
                    .setAllowedPriceType(onDemandPrice)
                    .ok();
            logout();
            loginAs(user);
            validationOfPriceTypesRestrictions("On-demand", "On-demand", "On-demand");
        } finally {
            logout();
            loginAs(admin);
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToRoles()
                    .editRole(testRole)
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
                                            .clickApply()
                                            .sleep(2, SECONDS)));
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

    @CloudProviderOnly(values={Cloud.AWS,Cloud.GCP})
    @Test(dependsOnMethods={"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2651"})
    public void validationOfPriceTypesRestrictionsSystemSettings() {
        try {
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedPriceTypes, clusterAllowedPrice);
            logout();
            loginAs(user);
            validationOfPriceTypesRestrictions("On-demand", "On-demand", "On-demand");
        } finally {
            logout();
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedPriceTypes, defaultClusterAllowedPriceTypes);
        }
    }

    @CloudProviderOnly(values={Cloud.AWS,Cloud.GCP})
    @Test(dependsOnMethods={"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2652"})
    public void validationOfPriceTypesRestrictionsHierarchy() {
        try {
            loginAs(admin);
            setClusterAllowedStringPreference(clusterAllowedPriceTypes, clusterAllowedPriceTypesValue);
            openEditUserTab(user.login)
                    .setAllowedPriceType(onDemandPrice)
                    .ok();
            tools()
                    .performWithin(defaultRegistry,defaultGroup,testingTool,tool->
                            tool.showInstanceManagement(instanceManagement->
                                    instanceManagement
                                            .clearAllowedPriceTypeField()
                                            .setPriceType(spotPriceName)
                                            .clickApply()
                                            .sleep(2,SECONDS)));
            logout();
            loginAs(user);
            validationOfPriceTypesRestrictions("On-demand", "On-demand", "On-demand");
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

    private void setMaskForRole(String role, String mask, String value){
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToRoles()
                .editRole(role)
                .addAllowedLaunchOptions(mask, value)
                .ok();
    }

    private void setClusterAllowedStringPreference(String pref, String value) {
        navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToCluster()
                .setClusterAllowedStringPreference(pref, value)
                .save();
        sleep(10, SECONDS);
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
                                .ensure(DOCKER_IMAGE, value(defaultGroup), value(testingTool))
                                .ensure(INSTANCE_TYPE, empty)
                                .checkValueIsInDropDown(INSTANCE_TYPE, instanceFamilyName)
                )
                .exitFromConfigurationWithoutSaved();
    }

    private void validationOfPriceTypesRestrictions(String pipelinePriceTypes, String configurationPriceTypes, String toolPriceTypes) {
        library()
                .cd(folder)
                .clickOnDraftVersion(pipeline)
                .configurationTab()
                .editConfiguration(configurationName,profile->
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
