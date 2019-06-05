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

import com.epam.pipeline.autotests.ao.ShellAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PipelinePermission;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.SSH_LINK;
import static com.epam.pipeline.autotests.utils.Privilege.*;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SshPermissionTest extends AbstractSeveralPipelineRunningTest implements Authorization {
    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String sleepingCommand = "sleep 20m";
    private final String defaultCommand = "/start.sh";

    private final String pipelineName = "ssh-permission-test-pipeline-" + Utils.randomSuffix();

    @BeforeClass
    public void createPipeline() {
        navigationMenu()
                .library()
                .createPipeline(Template.SHELL, pipelineName)
                .sleep(3, SECONDS);
    }

    @Override
    @AfterClass(alwaysRun = true, enabled = false)
    public void removeNodes() {
        logoutIfNeededAndPerform(() -> {
            loginAs(admin);
            sleep(6, MINUTES);
            super.removeNodes();
        });
    }

    @AfterClass(alwaysRun = true, enabled = false)
    public void tearDown() {
        logoutIfNeededAndPerform(() -> {
            loginAs(admin);
            navigationMenu()
                    .library()
                    .removePipeline(pipelineName);
        });
    }

    @Test
    @TestCase({"EPMCMBIBPC-646"})
    public void checkAccessToSSHForNonOwnerUser() {
        navigationMenu()
                .library()
                .sleep(1, SECONDS)
                .clickOnPipeline(pipelineName)
                .clickEditButton()
                .clickOnPermissionsTab()
                .addNewUser(user.login)
                .closeAll();
        givePermissions(user,
                PipelinePermission.allow(READ, pipelineName),
                PipelinePermission.allow(WRITE, pipelineName),
                PipelinePermission.allow(EXECUTE, pipelineName));
        navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .runPipeline()
                .setCommand(sleepingCommand)
                .launch(this);
        runsMenu()
                .activeRuns()
                .showLog(getLastRunId())
                .waitForSshLink();
        logout();
        loginAs(user)
                .runs()
                .viewAvailableActiveRuns()
                .showLog(getLastRunId())
                .ensure(SSH_LINK, not(visible));
        logout();
    }

    @Test(dependsOnMethods = {"checkAccessToSSHForNonOwnerUser"})
    @TestCase({"EPMCMBIBPC-647"})
    public void checkAccessToSSHByDirectLinkForNonOwnerUser() {
        loginAs(admin)
                .tools().perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .setCommand(defaultCommand)
                .launchTool(this, Utils.nameWithoutGroup(tool));
        final String sshLink =
                runsMenu()
                        .activeRuns()
                        .showLog(getLastRunId())
                        .waitForSshLink()
                        .getSshLink();
        logout();
        loginAs(user);
        ShellAO.open(sshLink)
                .execute("ls -la")
                .assertOutputDoesNotContain("ls -la");
        open(C.ROOT_ADDRESS);
        logout();
    }
}
