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
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.utils.Privilege.EXECUTE;
import static com.epam.pipeline.autotests.utils.Privilege.READ;
import static com.epam.pipeline.autotests.utils.Privilege.WRITE;
import static com.epam.pipeline.autotests.utils.Utils.resourceName;
import static java.lang.String.format;

public class RunAsTest extends AbstractAutoRemovingPipelineRunningTest implements Navigation, Authorization {

    private final String pipeline = resourceName("run-as");
    private static final String CONFIG_JSON = "/runAsTemplate.json";

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        loginAsAdminAndPerform(() -> {
            navigationMenu()
                    .settings()
                    .switchToUserManagement()
                    .switchToUsers()
                    .searchUserEntry(admin.login.toUpperCase())
                    .edit()
                    .resetConfigureRunAs(user.login)
                    .ok();
        });
    }

    @Test
    @TestCase(value = "")
    public void checkRunAsForGeneralUser() {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login.toUpperCase())
                .edit()
                .configureRunAs(user.login.toUpperCase(), false)
                .ok();
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
                .launch();
        runsMenu()
                .activeRuns()
                .viewAvailableActiveRuns();
        this.setRunId(Utils.getPipelineRunId(pipeline));
        RunsMenuAO runs = new RunsMenuAO();
        runs
                .showLog(getRunId());
        runs
                .ensureHasOwner(getUserNameByAccountLogin(admin.login));
        logout();
        loginAs(admin);
        runsMenu()
                .showLog(getRunId())
                .waitForSshLink()
                .validateShareLink(user.login)
                .validateShareLink(C.ROLE_USER);
    }
}
