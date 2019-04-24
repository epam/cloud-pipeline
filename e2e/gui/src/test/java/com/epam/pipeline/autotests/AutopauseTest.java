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

import com.codeborne.selenide.Condition;
import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO.PreferencesAO.SystemTabAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.function.Function;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.hidden;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.AUTO_PAUSE;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.runWithId;
import static java.util.concurrent.TimeUnit.SECONDS;

public class AutopauseTest extends AbstractSeveralPipelineRunningTest implements Tools, Authorization {

    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String instanceType = C.DEFAULT_INSTANCE;
    private final String defaultPriceType = C.DEFAULT_INSTANCE_PRICE_TYPE;
    private final String diskSize = "15";
    private final String onDemand = "On-demand";

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

        launchTool(onDemand, enabled);

        if (Cloud.AZURE.name().equals(C.CLOUD_PROVIDER)) {
            runsMenu()
                    .activeRuns()
                    .ensure(runWithId(getLastRunId()), visible)
                    .waitForCompletion(getLastRunId())
                    .completedRuns()
                    .ensure(runWithId(getLastRunId()), visible);
            return;
        }
        launchTool(defaultPriceType, hidden);
        runsMenu()
                .activeRuns()
                .ensure(runWithId(getLastRunId()), visible)
                .ensure(runWithId(String.valueOf(Integer.parseInt(getLastRunId()) - 1)), visible)
                .waitForCompletion(getLastRunId())
                .completedRuns()
                .ensure(runWithId(getLastRunId()), visible)
                .ensure(runWithId(String.valueOf(Integer.parseInt(getLastRunId()) - 1)), visible);
    }

    @Test
    @TestCase({"EPMCMBIBPC-2634"})
    public void autopauseValidationPauseOrStop() {
        setSystemPreferences("2", "2", "30", "PAUSE_OR_STOP");
        relogin();

        launchTool(onDemand, enabled);

        if (Cloud.AZURE.name().equals(C.CLOUD_PROVIDER)) {
            runsMenu()
                    .activeRuns()
                    .waitUntilResumeButtonAppear(getLastRunId())
                    .validateStatus(getLastRunId(), LogAO.Status.PAUSED)
                    .resume(getLastRunId(), getToolName())
                    .waitUntilStopButtonAppear(getLastRunId())
                    .stopRun(getLastRunId());
            return;
        }
        launchTool(defaultPriceType, hidden);
        runsMenu()
                .activeRuns()
                .waitUntilResumeButtonAppear(getLastRunId())
                .validateStatus(getLastRunId(), LogAO.Status.PAUSED)
                .waitForCompletion(String.valueOf(Integer.parseInt(getLastRunId()) - 1))
                .ensure(runWithId(String.valueOf(Integer.parseInt(getLastRunId()) - 1)), hidden)
                .completedRuns()
                .ensure(runWithId(String.valueOf(Integer.parseInt(getLastRunId()) - 1)), visible)
                .activeRuns()
                .resume(getLastRunId(), getToolName())
                .waitUntilStopButtonAppear(getLastRunId())
                .stopRun(getLastRunId());
    }

    @Test
    @TestCase({"EPMCMBIBPC-2635"})
    public void autopauseValidationPause() {
        setSystemPreferences("2", "2", "30", "PAUSE");
        relogin();

        launchTool(onDemand, enabled);

        runsMenu()
                .activeRuns()
                .waitUntilResumeButtonAppear(getLastRunId())
                .validateStatus(getLastRunId(), LogAO.Status.PAUSED)
                .resume(getLastRunId(), getToolName())
                .waitUntilStopButtonAppear(getLastRunId())
                .stopRun(getLastRunId());
    }

    private void launchTool(final String priceType, final Condition autoPause) {
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setLaunchOptions(diskSize, instanceType, null)
                .setPriceType(priceType)
                .ensure(AUTO_PAUSE, autoPause)
                .launchTool(this, Utils.nameWithoutGroup(tool));
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
                        .sleep(2, SECONDS)
                        .click(OK)
        );
    }

    private void relogin() {
        logout();
        loginAs(user);
    }

    private String getSystemValue(final Function<SystemTabAO, String> getValueFunction) {
        final String value = getValueFunction.apply(navigationMenu()
                .settings()
                .switchToPreferences()
                .switchToSystem());
        new SettingsPageAO(new PipelinesLibraryAO()).click(OK);
        return value;
    }

    private String getToolName() {
        final String[] toolAndGroup = tool.split("/");
        return toolAndGroup[toolAndGroup.length - 1];
    }
}
