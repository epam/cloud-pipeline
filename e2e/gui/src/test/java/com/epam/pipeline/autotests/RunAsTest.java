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

import static com.codeborne.selenide.Condition.exist;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static java.lang.String.format;

public class RunAsTest extends AbstractSeveralPipelineRunningTest implements Navigation, Authorization {

    public static final String RUN_AS_RESOURCE = "run-as";
    private final String pipeline = resourceName(RUN_AS_RESOURCE);
    private final String storage = resourceName(RUN_AS_RESOURCE);
    private final String pipeline1 = resourceName(RUN_AS_RESOURCE);
    private static final String CONFIG_JSON = "/runAsTemplate.json";
    private static final String CONFIG_JSON2 = "/runAsTemplate2.json";
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
                    .resetConfigureRunAs(user.login)
                    .resetConfigureRunAs(userWithoutCompletedRuns.login)
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
                .createPipeline(pipeline)
                .clickOnPipeline(pipeline)
                .firstVersion()
                .codeTab()
                .clickOnFile("config.json")
                .editFile(configuration -> Utils.readResourceFully(CONFIG_JSON)
                        .replace("{{instance_type}}", C.DEFAULT_INSTANCE)
                        .replace("{{user_name}}", admin.login.toUpperCase())
                        .replace("{{user_role}}", C.ROLE_USER.toUpperCase()))
                .saveAndCommitWithMessage(format("test: Change configuration to run as %s user",
                        admin.login.toUpperCase()));
        addAccountToPipelinePermissions(user, pipeline);
        givePermissions(user,
                PipelinePermission.allow(READ, pipeline),
                PipelinePermission.allow(EXECUTE, pipeline),
                PipelinePermission.allow(WRITE, pipeline)
        );
        logout();
        loginAs(user);
        library()
                .clickOnPipeline(pipeline)
                .firstVersion()
                .runPipeline()
                .selectDockerImage(registry, group, tool, "latest")
                .launch();
        runsMenu()
                .activeRuns()
                .viewAvailableActiveRuns();
        this.addRunId(Utils.getPipelineRunId(pipeline));
        RunsMenuAO runs = new RunsMenuAO();
        runs
                .showLog(getLastRunId())
                .expandTab(PARAMETERS)
                .ensure(configurationParameter("ORIGINAL_OWNER", user.login), exist);
        runs
                .ensureHasOwner(getUserNameByAccountLogin(admin.login));
        logout();
        loginAs(admin);
        runsMenu()
                .showLog(getLastRunId())
                .waitForSshLink()
                .validateShareLink(user.login)
                .validateShareLink(C.ROLE_USER);
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
                .codeTab()
                .clickOnFile("config.json")
                .editFile(configuration -> Utils.readResourceFully(CONFIG_JSON2)
                        .replace("{{instance_type}}", C.DEFAULT_INSTANCE)
                        .replace("{{user_name}}", admin.login.toUpperCase())
                        .replace("{{docker_image}}", C.TESTING_TOOL_NAME)
                        .replace("{{output}}", format("%s/%s", storagePath, fileName))
                        .replace("{{input}}", format("%s/%s", storagePath, folderName)))
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
        configureRunAsList(userWithoutCompletedRuns);
        logout();
        loginAs(userWithoutCompletedRuns);
        library()
                .clickOnPipeline(pipeline1)
                .firstVersion()
                .runPipeline()
                .checkLaunchMessage("error", "Access is denied", false)
                .launch();
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
