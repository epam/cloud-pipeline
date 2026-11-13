/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static com.epam.pipeline.autotests.ao.Primitive.ADVANCED_PANEL;
import static com.epam.pipeline.autotests.ao.Primitive.EXEC_ENVIRONMENT;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.screenshot;
import static java.lang.String.format;
import static java.util.regex.Pattern.compile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class DnsHostsManagementTest extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {
    private static final String CP_CAP_AUTOSCALE_DNS_HOSTS = "CP_CAP_AUTOSCALE_DNS_HOSTS";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;
    private static final String SERVER_IP = "Server:         10.96.0.10";
    private static String[] command = {
            "cat /etc/hosts | grep %s",
            "yum install -y bind-utils",
            "nslookup pipeline-%s",
            "cat /etc/hosts | grep %s | wc -l"
    };

    @AfterMethod(alwaysRun = true)
    public void logoutUser() {
        open(C.ROOT_ADDRESS);
        logoutIfNeeded();
        loginAs(admin);
    }

    @Test
    @TestCase(value = "1900_1")
    public void implementKubernetesDnsCustomHostsManagement() {
        final String[] userRunIP = new String[1];
        final List<String> userRunIds = new ArrayList<>();
        logoutIfNeeded();
        loginAs(user);
        try {
            tools()
                    .perform(registry, group, tool, tool -> tool.run(this));
            final String userRunId1 = getLastRunId();
            userRunIds.add(userRunId1);

            tools()
                    .perform(registry, group, tool, tool -> tool.run(this));
            final String userRunId2 = getLastRunId();
            userRunIds.add(userRunId2);
            runsMenu()
                    .showLog(userRunId1)
                    .waitForSshLink()
                    .sshInNewTab(shell -> {
                        userRunIP[0] = getRunIP(shell
                                .waitUntilTextAppears(userRunId1)
                                .execute(format(command[0], "$HOSTNAME"))
                                .lastCommandResult(format(command[0], "$HOSTNAME")));
                    });
            runsMenu()
                    .showLog(userRunId2)
                    .waitForSshLink()
                    .sshInNewTab(shell -> shell
                            .waitUntilTextAppears(userRunId2)
                            .execute(command[1])
                            .sleep(6, TimeUnit.MINUTES)
                            .assertNextStringIsVisible("Complete!",
                                    format("pipeline-%s", userRunId2))
                            .execute(format(command[2], userRunId1))
                            .assertPageAfterCommandContainsStrings(format(command[2], userRunId1), SERVER_IP,
                                    format("Address: %s", userRunIP[0]))
                    );
        } finally {
            stopRunsAsOwner(userRunIds);
        }
    }

    @Test
    @TestCase(value = "1900_2")
    public void supportDnsHostsManagementInSgeAutoscaler() {
        logoutIfNeeded();
        loginAs(user);
        final List<String> userRunIds = new ArrayList<>();
        try {
            tools()
                    .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                    .expandTab(EXEC_ENVIRONMENT)
                    .expandTab(ADVANCED_PANEL)
                    .setPriceType(ON_DEMAND)
                    .doNotMountStoragesSelect(true)
                    .enableClusterLaunch()
                    .clusterSettingsForm("Auto-scaled cluster")
                    .setWorkingNodesCount("1")
                    .ok()
                    .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:20 sleep infinity && sleep infinity")
                    .clickAddBooleanParameter()
                    .setName(CP_CAP_AUTOSCALE_DNS_HOSTS)
                    .setEnableParameter(true)
                    .close()
                    .doNotMountStoragesSelect(true)
                    .launchTool(this, nameWithoutGroup(tool));
            final String parentRunId = getLastRunId();
            userRunIds.add(parentRunId);
            final LogAO logAO = runsMenu()
                    .showLog(parentRunId)
                    .waitForSshLink();
            final String childRunID = logAO
                    .waitForNestedRunsLink()
                    .getNestedRunID(1);
            logAO
                    .waitForNestedRunWorking(childRunID)
                    .sshInNewTab(shell -> {
                        shell
                                .waitUntilTextAppears(parentRunId)
                                .execute(command[1])
                                .sleep(6, TimeUnit.MINUTES)
                                .assertNextStringIsVisible("Complete!",
                                        format("pipeline-%s", parentRunId))
                                .execute(format(command[2], childRunID))
                                .assertPageAfterCommandContainsStrings(format(command[2], childRunID), SERVER_IP);
                        final String childRunIP = getRunIP(shell.lastCommandResult("Name:"));
                        final String hostsCountCommand = format(command[3], childRunIP);
                        // the count is the only output row, so it is followed directly by the next prompt, which is
                        // "[root@pipeline-<id> /]#" in the Rocky Linux images and "root@pipeline-<id>:...#" in others
                        shell
                                .executeAndWaitForPrompt(hostsCountCommand, format("pipeline-%s", parentRunId))
                                .assertOutputOfLastCommandMatches(hostsCountCommand,
                                        format("^\\s*0\\s*\\[?root@pipeline-%s\\b", parentRunId));
                    });
        } finally {
            stopRunsAsOwner(userRunIds);
        }
    }

    /**
     * Stops the runs while their non-admin owner is logged in: the active runs page of the admin lists only
     * the admin's own runs, so the cleanup of the class does not stop runs of other users.
     */
    private void stopRunsAsOwner(final List<String> runIds) {
        runIds.forEach(runId -> {
            try {
                open(C.ROOT_ADDRESS);
                runsMenu().stopRunIfPresent(runId);
            } catch (Throwable e) {
                System.out.printf("Run %s could not be stopped: %s%n", runId, e.getMessage());
            }
        });
    }

    private String getRunIP(String logMessage) {
        final Matcher matcher = compile("\\d+\\.\\d+\\.\\d+\\.\\d+")
                .matcher(logMessage);
        if (!matcher.find()) {
            final String screenName = format("DnsHostsManagementTest_%s", Utils.randomSuffix());
            screenshot(screenName);
        }
        return matcher.group();
    }
}

