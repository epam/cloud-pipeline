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
import com.epam.pipeline.autotests.ao.ShellAO;
import com.epam.pipeline.autotests.ao.Template;
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
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SshPermissionTest extends AbstractSeveralPipelineRunningTest implements Authorization {
    private final String sleepingCommand = "sleep 20m";

    private final String pipelineName = "ssh-permission-test-pipeline-" + Utils.randomSuffix();

    @BeforeClass
    public void createPipeline() {
        navigationMenu()
                .library()
                .createPipeline(Template.SHELL, pipelineName)
                .sleep(3, SECONDS);
    }

    @Override
    @AfterClass(alwaysRun = true)
    public void removeNodes() {
        logoutIfNeededAndPerform(() -> {
            loginAs(admin);
            sleep(6, MINUTES);
            super.removeNodes();
        });
    }

    @AfterClass(alwaysRun = true)
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
                .addNewUser(userWithoutCompletedRuns.login)
                .closeAll();
        givePermissions(user,
                PipelinePermission.allow(READ, pipelineName),
                PipelinePermission.allow(WRITE, pipelineName),
                PipelinePermission.allow(EXECUTE, pipelineName));
        givePermissions(userWithoutCompletedRuns,
                PipelinePermission.allow(READ, pipelineName),
                PipelinePermission.allow(WRITE, pipelineName),
                PipelinePermission.allow(EXECUTE, pipelineName));
        String ownerRunId = null;
        try {
            logout();
            loginAs(userWithoutCompletedRuns);
            ownerRunId = launchPipelineWithSleepingCommand();
            new RunsMenuAO()
                    .shouldContainRun(pipelineName, ownerRunId);
            runsMenu()
                    .activeRuns()
                    .showLog(ownerRunId)
                    .waitForSshLink();
            logout();
            loginAs(user)
                    .runs()
                    .viewAvailableActiveRuns()
                    .showLog(ownerRunId)
                    .shouldHaveRunningStatus()
                    // the SSH link of a running run is rendered after the page loads, so wait before checking its absence
                    .sleep(10, SECONDS)
                    .ensure(SSH_LINK, not(visible));
        } finally {
            stopRunAsOwner(userWithoutCompletedRuns, ownerRunId);
        }
    }

    @Test(dependsOnMethods = {"checkAccessToSSHForNonOwnerUser"})
    @TestCase({"EPMCMBIBPC-647"})
    public void checkAccessToSSHByDirectLinkForNonOwnerUser() {
        String ownerRunId = null;
        try {
            loginAs(userWithoutCompletedRuns);
            ownerRunId = launchPipelineWithSleepingCommand();
            new RunsMenuAO()
                    .shouldContainRun(pipelineName, ownerRunId);
            final ShellAO ownerTerminal = runsMenu()
                    .activeRuns()
                    .showLog(ownerRunId)
                    .waitForSshLink()
                    .clickSshLinkInNewTab();
            final String sshLink = Utils.getCurrentURL();
            ownerTerminal.closeTab();
            logout();
            loginAs(user);
            navigationMenu()
                    .settings()
                    .switchToMyProfile()
                    .validateUserName(user.login);
            ShellAO.open(sshLink)
                    // an accessible terminal shows the prompt of the run within this time
                    .sleep(30, SECONDS)
                    .assertOutputDoesNotContain(String.format("pipeline-%s", ownerRunId))
                    .execute("ls -la")
                    .assertOutputDoesNotContain("ls -la");
            open(C.ROOT_ADDRESS);
        } finally {
            stopRunAsOwner(userWithoutCompletedRuns, ownerRunId);
        }
    }

    private String launchPipelineWithSleepingCommand() {
        navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .firstVersion()
                .runPipeline()
                .setCommand(sleepingCommand)
                .setPriceType(ON_DEMAND)
                .launch(this);
        return getLastRunId();
    }

    /**
     * Stops the run while its non-admin owner is logged in, then logs out: the active runs page of the admin
     * lists only the admin's own runs, so the cleanup of the class does not stop runs of other users.
     * A failure here is only printed, so that it does not replace the failure of the test itself.
     */
    private void stopRunAsOwner(final Account owner, final String runId) {
        if (runId == null) {
            return;
        }
        try {
            logoutIfNeeded();
            loginAs(owner);
            runsMenu()
                    .stopRunIfPresent(runId);
            logout();
        } catch (Throwable e) {
            System.out.printf("Run %s could not be stopped: %s%n", runId, e.getMessage());
        }
    }
}
