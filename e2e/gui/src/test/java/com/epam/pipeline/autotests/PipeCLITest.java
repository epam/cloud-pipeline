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

import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.ao.SettingsPageAO.UserManagementAO.UsersTabAO.UserEntry.EditUserPopup;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.Condition.exist;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.String.format;
import static java.util.regex.Pattern.compile;
import static org.testng.Assert.assertTrue;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;

public class PipeCLITest extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private static final String COMMAND = "pipe run -di %s:latest -u %s -y";
    private static final String RESULT = "Pipeline run scheduled with RunId: ";
    private static final String ORIGINAL_OWNER = "ORIGINAL_OWNER";
    private static final String CONFIG_STORE_OPTION = "\\s+(--config-store|-cs)(\\s+|=)\\S+";
    private static final String HOME_DIR_CONFIG_STORE =
            "Config storing mode is `home-dir`, target path `/root/.pipe/config.json`";
    private static final String INSTALL_DIR_CONFIG_STORE = "Config storing mode is `install-dir`, target path `";
    private static final String INSTALL_DIR_CONFIG_FILE = "/config.json`";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;

    @AfterClass(alwaysRun = true)
    public void resetPreference() {
        logoutIfNeeded();
        loginAs(admin);
        final EditUserPopup popUp = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login.toUpperCase())
                .edit();
        if (popUp.checkConfigureRunAs(user.login)) {
                popUp.resetConfigureRunAs(user.login)
                    .ok();
        }
    }

    @Test
    @TestCase(value = {"2115_1"})
    public void checkPipeCLIInstallationContent() {
        final String installationContent = Utils.readFile(C.PIPE_INSTALLATION_CONTENT).trim();
        navigationMenu()
                .settings()
                .switchToCLI()
                .switchPipeCLI()
                .selectOperationSystem(C.PIPE_OPERATION_SYSTEM)
                .checkOperationSystemInstallationContent(installationContent);
    }

    @Test
    @TestCase(value = {"2115_2"})
    public void checkPipeCLIConfigStore() {
        final SettingsPageAO.CliAO cliAO = navigationMenu()
                .settings()
                .switchToCLI()
                .switchPipeCLI()
                .selectOperationSystem("Linux-Binary");

        final String operationSystemInstallationContent = cliAO.getOperationSystemInstallationContent();
        // the configure command template of the deployment may already choose a config store
        final String pipeConfigCommand = cliAO
                .generateAccessKey()
                .ensureConfigureCommandHasAccessKey()
                .getCLIConfigureCommand()
                .replaceAll(CONFIG_STORE_OPTION, "");
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setPriceType(ON_DEMAND)
                .launchTool(this, nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForSshLink()
                .sshInNewTab(shell -> shell
                        .waitUntilTextAppears(getLastRunId())
                        .execute(operationSystemInstallationContent)
                        .waitUntilTextAppears(getLastRunId())
                        .execute(pipeConfigCommand)
                        .assertOutputContains(HOME_DIR_CONFIG_STORE)
                        .execute(format("%s --config-store install-dir", pipeConfigCommand))
                        .assertPageContains(INSTALL_DIR_CONFIG_STORE, INSTALL_DIR_CONFIG_FILE));
    }

    @Test
    @TestCase(value = {"1948_1"})
    public void checkPipeCLIAdminRunAsUser() {
        final String runID = launchRunAsUser(user.login);
        runsMenu()
                .viewAvailableActiveRuns()
                .shouldContainRun("pipeline", runID)
                .validatePipelineOwner(runID, user.login.toLowerCase())
                .showLog(runID)
                .ensureParameterIsNotPresent(ORIGINAL_OWNER);
        runsMenu()
                .stopRun(runID);
    }

    @Test
    @TestCase(value = {"1948_2"})
    public void checkPipeCLIUserRunAsUser() {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login.toUpperCase())
                .edit()
                .configureRunAs(user.login.toUpperCase(), false)
                .ok();
        logout();
        loginAs(user);
        final String runID = launchRunAsUser(admin.login);
        logout();
        loginAs(admin);
        runsMenu()
                .viewAvailableActiveRuns()
                .shouldContainRun("pipeline", runID)
                .validatePipelineOwner(runID, admin.login.toLowerCase())
                .showLog(runID)
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(ORIGINAL_OWNER, user.login), exist);
        runsMenu()
                .stopRun(runID);
    }

    private String launchRunAsUser(String userName) {
        final String[] output = new String[1];
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .doNotMountStoragesSelect(true)
                .setPriceType(ON_DEMAND)
                .launchTool(this, nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForSshLink()
                .ssh(shell -> {
                    output[0] = shell
                            .waitUntilTextAppears(getLastRunId())
                            .execute(format(COMMAND, tool, userName))
                            .assertOutputContains(RESULT)
                            .lastCommandResult(format(COMMAND, tool, userName));
                    shell.close();
                });
        final Pattern pattern = compile(format("%s(\\d+).*", RESULT));
        final Matcher matcher = pattern.matcher(output[0]);
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}

