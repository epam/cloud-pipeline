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

import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PipelinePermission;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.ToolPermission;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.epam.pipeline.autotests.ao.Primitive.INSTANCE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.utils.Json.editingConfiguration;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static java.lang.String.format;

public class RunAsTest extends AbstractSeveralPipelineRunningTest implements Navigation, Authorization {

    public static final String RUN_AS_RESOURCE = "run-as";
    private final String pipeline = resourceName(RUN_AS_RESOURCE);
    private final String storage = resourceName(RUN_AS_RESOURCE);
    private final String pipeline1 = resourceName(RUN_AS_RESOURCE);
    private final String fileName = "file1.txt";
    private final String folderName = "folder1";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        loginAsAdminAndPerform(() -> {
            library()
                    .removePipeline(pipeline)
                    .removeStorage(storage)
                    .removePipeline(pipeline1);
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchUserEntry(admin.login.toUpperCase())
                    .edit()
                    .resetConfigureRunAs(user.login.toLowerCase())
                    .resetConfigureRunAs(userWithoutCompletedRuns.login.toLowerCase())
                    .ok();
            tools()
                    .performWithin(registry, group, tool, tool ->
                            tool.permissions()
                                    .deleteIfPresent(userWithoutCompletedRuns.login)
                                    .closeAll()
                    );
        });
    }

    @CloudProviderOnly(values = {Cloud.AWS})
    @Test
    @TestCase(value = "EPMCMBIBPC-3233")
    public void checkRunAsForGeneralUser() {
        configureRunAsList(user);
        library()
                .createPipeline(pipeline);
        addAccountToPipelinePermissions(user, pipeline);
        givePermissions(user,
                PipelinePermission.allow(READ, pipeline),
                PipelinePermission.allow(EXECUTE, pipeline),
                PipelinePermission.allow(WRITE, pipeline)
        );
        library()
                .clickOnPipeline(pipeline)
                .firstVersion()
                .codeTab()
                .clickOnFile("config.json")
                .editFile(editingConfiguration("default", configuration -> {
                    configuration.put("run_as", admin.login.toUpperCase());
                    configuration.putArray("share_with_roles")
                            .addObject()
                            .put("name", C.ROLE_USER.toUpperCase());
                    // the run has to stay active until its Share with and parameters are checked by the admin
                    configuration.put("cmd_template", "sleep infinity");
                    configuration.put("instance_size", C.DEFAULT_INSTANCE);
                }))
                .saveAndCommitWithMessage(format("test: Change configuration to run as %s user",
                        admin.login.toUpperCase()));
        logout();
        loginAs(user);
        library()
                .clickOnPipeline(pipeline)
                .firstVersion()
                .runPipeline()
		.setPriceType(ON_DEMAND)
                .launch();
        runsMenu()
                .activeRuns()
                .viewAvailableActiveRuns();
        this.addRunId(Utils.getPipelineRunId(pipeline));
        RunsMenuAO runs = new RunsMenuAO();
        runs
                .shouldContainRun(pipeline, getLastRunId())
                .showLog(getLastRunId());
        runs
                .ensureHasOwner(getUserNameByAccountLogin(admin.login));
        logout();
        loginAs(admin);
        runsMenu()
                .showLog(getLastRunId())
                .waitForSshLink()
                .validateShareLink(user.login)
                .validateShareLink(C.ROLE_USER)
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("ORIGINAL_OWNER", user.login), exist);
    }

    @Test
    @TestCase(value = {"1949"})
    public void userAndWriteAccessToBucketWithRunAsOption() {
        logoutIfNeeded();
        loginAs(admin);
        String storagePath = library()
                .createStorage(storage)
                .selectStorage(storage)
                .createFolder(folderName)
                .createFileWithContent(fileName, "output file")
                .getStoragePath();
        library()
                .createPipeline(pipeline1)
                .clickOnPipeline(pipeline1)
                .firstVersion()
                .configurationTab()
                .editConfiguration("default", profile -> {
                    profile.expandTab(EXEC_ENVIRONMENT);
                    profile
                            .selectDockerImage(dockerImage -> dockerImage
                                    .selectRegistry(registry)
                                    .selectGroup(group)
                                    .selectTool(tool)
                                    .click(OK))
                            .selectValue(INSTANCE_TYPE, C.DEFAULT_INSTANCE)
                            .addInputParameter("in", format("%s/%s", storagePath, folderName))
                            .addOutputParameter("out", format("%s/%s", storagePath, fileName))
                            .click(SAVE)
                            .waitUntilSaveEnding("default");
                })
                .codeTab()
                .clickOnFile("config.json")
                .editFile(editingConfiguration("default", configuration ->
                        configuration.put("run_as", admin.login.toUpperCase())))
                .saveAndCommitWithMessage(format("test: Change configuration to run as %s user",
                        admin.login.toUpperCase()));
        addAccountToPipelinePermissions(userWithoutCompletedRuns, pipeline1);
        givePermissions(userWithoutCompletedRuns,
                PipelinePermission.allow(READ, pipeline1),
                PipelinePermission.allow(EXECUTE, pipeline1),
                PipelinePermission.deny(WRITE, pipeline1)
        );
        tools()
                .performWithin(registry, group, tool, tool ->
                        tool.permissions()
                                .deleteIfPresent(userWithoutCompletedRuns.login)
                                .addNewUser(userWithoutCompletedRuns.login)
                                .closeAll()
                );
        givePermissions(userWithoutCompletedRuns,
                ToolPermission.deny(READ, tool, registry, group),
                ToolPermission.deny(EXECUTE, tool, registry, group)
        );
        final SettingsPageAO.UserManagementAO userManagement = navigationMenu()
                .settings()
                .switchToUserManagement();
        $(byClassName("section-users")).shouldHave(cssClass("cp-table-element-selected"));
        userManagement
                .switchToUsers()
                .searchUserEntry(admin.login.toUpperCase())
                .edit()
                .configureRunAs(userWithoutCompletedRuns.login.toUpperCase(), false)
                .ok();
        logout();
        loginAs(userWithoutCompletedRuns);
        library()
                .clickOnPipeline(pipeline1)
                .firstVersion()
                .runPipeline()
                .launchWithoutErrorMessages();
        runsMenu()
                .activeRuns()
                .viewAvailableActiveRuns()
                .shouldContainRun(pipeline1, Utils.getPipelineRunId(pipeline1));
        this.addRunId(Utils.getPipelineRunId(pipeline1));
        logout();
    }

    private void configureRunAsList(Account userName) {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login.toUpperCase())
                .edit()
                .configureRunAs(userName.login.toUpperCase(), false)
                .ok();
    }
}
