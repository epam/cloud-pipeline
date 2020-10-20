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

import com.codeborne.selenide.Condition;
import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO.PreferencesAO.SystemTabAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.function.Function;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.hidden;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.AUTO_PAUSE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.runWithId;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static java.util.concurrent.TimeUnit.SECONDS;

public class AutopauseTest extends AbstractSeveralPipelineRunningTest implements Tools, Authorization {

    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String instanceType = C.DEFAULT_INSTANCE;
    private final String defaultPriceType = C.DEFAULT_INSTANCE_PRICE_TYPE;
    private final String diskSize = "15";
    private final String onDemand = ON_DEMAND;

    private String maxIdleTimeout;
    private String idleActionTimeout;
    private String idleCpuThreshold;
    private String idleAction;

    @BeforeClass(alwaysRun = true)
    public void getDefaultPreferences() {
        loginAsAdminAndPerform(() -> {
            maxIdleTimeout = getSystemValue(SystemTabAO::getMaxIdleTimeout);
            idleActionTimeout = getSystemValue(SystemTabAO::getIdleActionTimeout);
            idleCpuThreshold = getSystemValue(SystemTabAO::getIdleCpuThreshold);
            idleAction = getSystemValue(SystemTabAO::getIdleAction);
        });
    }

    @AfterClass(alwaysRun = true)
    public void fallBackPreferences() {
        setSystemPreferences(maxIdleTimeout, idleActionTimeout, idleCpuThreshold, idleAction);
    }

    @Test
    @TestCase({"EPMCMBIBPC-2633"})
    public void autopauseValidationStop() {
        setSystemPreferences("2", "2", "30", "STOP");
        relogin();

        final String run1 = launchTool(onDemand, enabled);
        final String run2 = launchTool(defaultPriceType, hidden);
        runsMenu()
                .activeRuns()
                .ensure(runWithId(run2), visible)
                .ensure(runWithId(run1), visible)
                .waitForCompletion(run2)
                .waitForCompletion(run1)
                .completedRuns()
                .ensure(runWithId(run2), visible)
                .ensure(runWithId(run1), visible);
    }

    @Test
    @TestCase({"EPMCMBIBPC-2634"})
    public void autopauseValidationPauseOrStop() {
        setSystemPreferences("2", "2", "30", "PAUSE_OR_STOP");
        relogin();

        final String run1 = launchTool(onDemand, enabled);
        final String run2 = launchTool(defaultPriceType, hidden);
        runsMenu()
                .activeRuns()
                .waitUntilResumeButtonAppear(run1)
                .validateStatus(run1, LogAO.Status.PAUSED)
                .waitForCompletion(run2)
                .ensure(runWithId(run2), hidden)
                .completedRuns()
                .ensure(runWithId(run2), visible)
                .activeRuns()
                .resume(run1, getToolName())
                .waitUntilStopButtonAppear(run1)
                .stopRun(run1);
    }

    @Test
    @TestCase({"EPMCMBIBPC-2635"})
    public void autopauseValidationPause() {
        setSystemPreferences("2", "2", "30", "PAUSE");
        relogin();

        final String runId = launchTool(onDemand, enabled);

        runsMenu()
                .activeRuns()
                .waitUntilResumeButtonAppear(runId)
                .validateStatus(runId, LogAO.Status.PAUSED)
                .resume(runId, getToolName())
                .waitUntilStopButtonAppear(runId)
                .stopRun(runId);
    }

    private String launchTool(final String priceType, final Condition autoPause) {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setLaunchOptions(diskSize, instanceType, null)
                .setPriceType(priceType)
                .ensure(AUTO_PAUSE, autoPause)
                .launchTool(this, Utils.nameWithoutGroup(tool));
        return getLastRunId();
    }

    private void setSystemPreferences(final String maxIdleTimeout,
                                      final String idleActionTimeout,
                                      final String idleCpuThreshold,
                                      final String idleAction) {
        loginAsAdminAndPerform(() ->
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .switchToSystem()
                        .setMaxIdleTimeout(maxIdleTimeout)
                        .setIdleActionTimeout(idleActionTimeout)
                        .setIdleCpuThreshold(idleCpuThreshold)
                        .setIdleAction(idleAction)
                        .save()
                        .sleep(1, SECONDS)
        );
    }

    private void relogin() {
        logout();
        loginAs(user);
    }

    private String getSystemValue(final Function<SystemTabAO, String> getValueFunction) {
        return getValueFunction.apply(navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToSystem());
    }

    private String getToolName() {
        final String[] toolAndGroup = tool.split("/");
        return toolAndGroup[toolAndGroup.length - 1];
    }
}
