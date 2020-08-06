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

import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.text;
import static com.epam.pipeline.autotests.ao.Primitive.DISK;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Profile.execEnvironmentTab;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.PrivilegeValue.ALLOW;
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
    private final String configuration = "restrictionTestConfiguration" + Utils.randomSuffix();
    private final String customDisk = "22";
    private final String configurationName = "customConfig";

    @AfterClass(alwaysRun = true)
    public void deletingEntities() {
        loginAs(admin);
        library()
                .removeNotEmptyFolder(folder);
        logout();
    }

    @Test
    @TestCase({"EPMCMBIBPC-2637"})
    public void preparationForValidationOfInstanceTypesRestrictions() {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchForUserEntry(user.login)
                .edit()
                .addAllowedLaunchOptions("Allowed instance types mask", String.format("%s.*", instanceFamilyName))
                .ok();
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
                        configuration.expandTabs(execEnvironmentTab)
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
        logout();
    }

    @Test(dependsOnMethods = {"preparationForValidationOfInstanceTypesRestrictions"})
    @TestCase({"EPMCMBIBPC-2638"})
    public void validationOfInstanceTypesRestrictionsExistingObjects() {
        loginAs(user);
        library()
                .cd(folder)
                .clickOnDraftVersion(pipeline)
                .configurationTab()
                .editConfiguration(configurationName, profile ->
                        profile
                                .expandTab(EXEC_ENVIRONMENT)
                                .ensure(INSTANCE_TYPE, empty)
                );
        logout();
    }

}
