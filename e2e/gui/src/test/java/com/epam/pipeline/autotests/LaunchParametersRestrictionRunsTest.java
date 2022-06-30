/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.stream.IntStream;

import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;

public class LaunchParametersRestrictionRunsTest
        extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private final String LAUNCH_MAX_RUNS_USER_GLOBAL = "launch.max.runs.user.global";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String GLOBAL_MAX_RUNS = "3";
    private String[] launchMaxRunsUserGlobalInitial;
    private String errorMessage = "You have exceeded maximum number of running jobs (%s).";


    @BeforeClass(alwaysRun = true)
    public void getPreferences() {
        launchMaxRunsUserGlobalInitial = navigationMenu()
                .settings()
                .switchToPreferences()
                .getLinePreference(LAUNCH_MAX_RUNS_USER_GLOBAL);
    }

    @Test
    @TestCase(value = {"2642_1"})
    public void checkGlobalRestrictionCountOfRunningInstances() {
        String message = format("Launch of new jobs is restricted as [%s] user " +
                "will exceed [<user-global-limit>] runs limit [%s]", user.login, GLOBAL_MAX_RUNS);
        navigationMenu()
                .settings()
                .switchToPreferences()
                .setPreference(LAUNCH_MAX_RUNS_USER_GLOBAL, GLOBAL_MAX_RUNS, true)
                .saveIfNeeded();
        logout();
        loginAs(user);
        String [] userRunIDs = launchSeveralRuns(parseInt(GLOBAL_MAX_RUNS));
        tools()
            .perform(registry, group, tool, ToolTab::runWithCustomSettings)
            .expandTab(EXEC_ENVIRONMENT)
            .ensure(byText(format(errorMessage, GLOBAL_MAX_RUNS)), visible)
            .checkLaunchMessage("message", format(errorMessage, GLOBAL_MAX_RUNS), true)
            .launchWithError(message);
        IntStream.range(0, parseInt(GLOBAL_MAX_RUNS))
                .forEach(i -> runsMenu().stopRun(userRunIDs[i]));
        logout();
        loginAs(admin);
        String [] adminRunIDs = launchSeveralRuns(parseInt(GLOBAL_MAX_RUNS));
        tools()
            .perform(registry, group, tool, ToolTab::runWithCustomSettings)
            .expandTab(EXEC_ENVIRONMENT)
            .ensure(byText(format(errorMessage, GLOBAL_MAX_RUNS)), not(visible))
            .checkLaunchMessage("message", format(errorMessage, GLOBAL_MAX_RUNS), false)
            .launch(this);
        runsMenu().stopRun(getLastRunId());
        IntStream.range(0, parseInt(GLOBAL_MAX_RUNS))
                .forEach(i -> runsMenu().stopRun(adminRunIDs[i]));
    }
    private String[] launchSeveralRuns(int count) {
        String[] runIDs = new String[count];
        IntStream.range(0, count)
                .forEach(i -> {
                    tools().perform(registry, group, tool, tool ->
                            tool.run(this));
                    runIDs[i] = getLastRunId();
                });
        return runIDs;
    }
}
