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
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.ToolGroup;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Tools;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.openqa.selenium.support.Colors;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.LAUNCH_CLUSTER;
import static com.epam.pipeline.autotests.ao.Primitive.RUN;
import static com.epam.pipeline.autotests.ao.ToolVersions.toolVersion;
import static com.epam.pipeline.autotests.ao.ToolVersions.versionTab;
import static com.epam.pipeline.autotests.utils.Conditions.backgroundColor;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ToolsScanTest extends AbstractAutoRemovingPipelineRunningTest implements Tools, Authorization {

    private static final Condition backgroundColorNotBlackOrWhite = Condition.and(
            "background color neither white nor black",
            not(backgroundColor(Colors.WHITE.getColorValue())),
            not(backgroundColor(Colors.BLACK.getColorValue()))
    );
    private static final String UNSCANNED_MESSAGE =
            "The version shall be scanned for security vulnerabilities. Run anyway?";
    private static final String CLAUSE_MESSAGE =
            "The latest version shall be scanned for vulnerabilities. You can try an older one.";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = "shell";
    private final String version = "latest";
    private final String fullToolName = String.format("%s/%s", group, tool);

    private String graceHours;
    private boolean policyDenyNotScanned;

    @BeforeClass(alwaysRun = true)
    public void getDefaultPreferences() {
        loginAsAdminAndPerform(() -> {
            graceHours =
                    navigationMenu()
                            .settings()
                            .switchToPreferences()
                            .switchToDockerSecurity()
                            .getGraceHours();
            ok();
            policyDenyNotScanned =
                    navigationMenu()
                            .settings()
                            .switchToPreferences()
                            .switchToDockerSecurity()
                            .getPolicyDenyNotScanned();
            ok();
        });
    }

    @AfterClass(alwaysRun = true)
    public void fallBackPreferences() {
        loginAsAdminAndPerform(() -> {
            navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .switchToDockerSecurity()
                    .setGraceHours(graceHours)
                    .save()
                    .sleep(1, SECONDS)
                    .ok();
            if (!policyDenyNotScanned) {
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .switchToDockerSecurity()
                        .clickPolicyDenyNotScanned()
                        .save()
                        .sleep(1, SECONDS)
                        .ok();
            }
        });
    }

    @Test(priority = 0)
    @TestCase({"EPMCMBIBPC-1994"})
    public void runUnscannedToolValidation() {
        tools().perform(registry, group, group ->
                group.sleep(3, SECONDS)
                        .performIf(ToolGroup.tool(fullToolName), visible, this::deleteTool)
                        .enableTool(tool)
                        .findTool(fullToolName)
                        .description()
                        .messageShouldAppear("The latest version shall be scanned for vulnerabilities.")
                        .versions()
                        .versionTableShouldBeEmpty()
                        .viewUnscannedVersionsAvailable()
                        .runUnscannedTool(UNSCANNED_MESSAGE)
                        .setDefaultLaunchOptions()
                        .setCommand("sleep infinity")
                        .launch(this)
                        .activeRuns()
                        .shouldContainRun("pipeline", getRunId())
        );
    }

    @Test(dependsOnMethods = {"runUnscannedToolValidation"})
    @TestCase({"EPMCMBIBPC-1995"})
    public void toolScanningValidation() {
        tools()
                .perform(registry, group, fullToolName, tool ->
                        tool
                                .versions()
                                .viewUnscannedVersions()
                                .validateUnscannedVersionsPage()
                                .selectVersion(version)
                                .validateVersionPage("SETTINGS")
                                .click(versionTab("VULNERABILITIES REPORT"))
                                .versionTableShouldBeEmpty()
                                .validateReportTableColumns()
                                .messageShouldAppear("No vulnerabilities found")
                                .click(versionTab("PACKAGES"))
                                .validateEcosystem(Collections.singletonList("Not Found"))
                                .arrow()
                                .viewUnscannedVersions()
                                .scanVersion(version)
                                .validateScanningProcess(version)
                                .validateScannedVersionsPage()
                                .runWithCustomSettings()
                );
    }

    @Test(dependsOnMethods = {"toolScanningValidation"})
    @TestCase({"EPMCMBIBPC-1996"})
    public void toolScanResultsValidation() {
        tools()
                .perform(registry, group, fullToolName, tool ->
                        tool
                                .versions()
                                .checkDiagram()
                                .selectVersion(version)
                                .validateVersionPage("VULNERABILITIES REPORT")
                                .validateReportTableColumns()
                                .selectComponent("kernel-headers")
                                .click(versionTab("PACKAGES"))
                                .selectEcosystem("Python.Dist")
                                .validatePackageList(Arrays.asList("pip", "py"), true)
                                .validateEcosystem(Arrays.asList("Python.Dist", "System"))
                                .selectEcosystem("System")
                                .validatePackageList(Arrays.asList("bash", "bzip2", "gcc"), false)
                                .filterPackages("krb5")
                                .validateSearchResult("krb5")
                                .validatePackageList(Collections.singletonList("krb5"), false)
                                .filterPackages("krb55")
                                .validatePackageList(Collections.emptyList(), false)
                );
    }

    @Test(priority = 1)
    @TestCase({"EPMCMBIBPC-2004"})
    public void denyNotScannedToolOptionValidation() {
        loginAsAdminAndPerform(() ->
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .switchToDockerSecurity()
                        .enablePolicyDenyNotScanned()
                        .setGraceHours("0")
                        .save()
                        .sleep(1, SECONDS)
                        .ok()
        );
        tools().perform(registry, group, group ->
                group.sleep(3, SECONDS)
                        .performIf(ToolGroup.tool(fullToolName), visible, this::deleteTool)
                        .enableTool(tool)
        );
        logout();
        loginAs(user);
        tools().perform(registry, group, fullToolName, tool ->
                tool
                        .sleep(2, SECONDS)
                        .messageShouldAppear(CLAUSE_MESSAGE)
                        .ensure(RUN, disabled)
                        .versions()
                        .viewUnscannedVersions()
                        .validateUnscannedVersionsPage()
                        .ensure(RUN, disabled)
                        .hover(RUN)
                        .messageShouldAppear("The latest version shall be scanned for vulnerabilities.")
                        .runVersion(version)
                        .ensure(LAUNCH_CLUSTER, enabled)
        );
    }

    @Test(priority = 2)
    @TestCase({"EPMCMBIBPC-2629"})
    public void gracePeriodOptionValidation() {
        loginAsAdminAndPerform(() ->
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .switchToDockerSecurity()
                        .enablePolicyDenyNotScanned()
                        .setGraceHours("1")
                        .save()
                        .sleep(1, SECONDS)
                        .ok()
        );
        tools().perform(registry, group, group ->
                group.sleep(3, SECONDS)
                        .performIf(ToolGroup.tool(fullToolName), visible, this::deleteTool)
                        .enableTool(tool)
        );
        logout();
        loginAs(user);
        tools()
                .perform(registry, group, fullToolName, toolDescription ->
                        toolDescription
                                .messageShouldAppear("The latest version shall be scanned for vulnerabilities.")
                                .ensure(RUN, enabled)
                                .runUnscannedTool("The version shall be scanned for security vulnerabilities, " +
                                        "but you can launch it during the grace period .* Run anyway?"));
    }

    @Test(priority = 3)
    @TestCase({"EPMCMBIBPC-2630"})
    public void uncheckedDenyNotScannedToolOptionValidation() {
        loginAsAdminAndPerform(() ->
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .switchToDockerSecurity()
                        .disablePolicyDenyNotScanned()
                        .setGraceHours("0")
                        .save()
                        .sleep(1, SECONDS)
                        .ok()
        );
        tools().perform(registry, group, group ->
                group.sleep(3, SECONDS)
                        .performIf(ToolGroup.tool(fullToolName), visible, this::deleteTool)
                        .enableTool(tool)
        );
        logout();
        loginAs(user);
        tools().perform(registry, group, fullToolName, tool ->
                tool
                        .sleep(2, SECONDS)
                        .messageShouldAppear(CLAUSE_MESSAGE)
                        .ensure(RUN, enabled)
                        .versions()
                        .viewUnscannedVersions()
                        .validateUnscannedVersionsPage()
                        .ensure(RUN, enabled)
                        .hover(RUN)
                        .messageShouldAppear(CLAUSE_MESSAGE)
                        .runUnscannedTool(UNSCANNED_MESSAGE)
        );
    }

    @Test(priority = 4)
    @TestCase({"EPMCMBIBPC-2631"})
    public void whiteListFlagValidation() {
        loginAsAdminAndPerform(() ->
                navigationMenu()
                        .settings()
                        .switchToPreferences()
                        .switchToDockerSecurity()
                        .enablePolicyDenyNotScanned()
                        .setGraceHours("0")
                        .save()
                        .sleep(3, SECONDS)
                        .ok()
        );
        tools().perform(registry, group, group ->
                group.sleep(3, SECONDS)
                        .performIf(ToolGroup.tool(fullToolName), visible, this::deleteTool)
                        .enableTool(tool)
                        .findTool(fullToolName)
                        .versions()
                        .viewUnscannedVersions()
                        .addToWhiteList(version)
                        .ensure(toolVersion(version), backgroundColorNotBlackOrWhite)
        );
        logout();
        loginAs(user);
        tools().perform(registry, group, fullToolName, tool ->
                tool
                        .versions()
                        .viewUnscannedVersions()
                        .validateUnscannedVersionsPage()
                        .ensure(toolVersion(version), backgroundColorNotBlackOrWhite)
                        .ensure(RUN, enabled)
                        .hover(RUN)
                        .messageShouldAppear(CLAUSE_MESSAGE)
                        .runUnscannedTool(UNSCANNED_MESSAGE)
        );
    }

    private ToolGroup deleteTool(final ToolGroup t) {
        return t.tool(fullToolName, tool -> tool.sleep(1, SECONDS)
                .delete()
                .messageShouldAppear("Are you sure you want to delete the tool?")
                .delete());
    }

    private void ok() {
        new SettingsPageAO(new PipelinesLibraryAO()).ok();
    }
}
