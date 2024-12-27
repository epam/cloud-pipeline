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

import com.codeborne.selenide.ex.ElementNotFound;
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
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.screenshot;
import static java.lang.String.format;
import static java.util.regex.Pattern.compile;

import java.util.regex.Matcher;

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
        logoutIfNeeded();
        loginAs(user);
        tools()
                .perform(registry, group, tool, tool -> tool.run(this));
        final String userRunId1 = getLastRunId();

        tools()
                .perform(registry, group, tool, tool -> tool.run(this));
        final String userRunId2 = getLastRunId();
        runsMenu()
                .showLog(userRunId1)
                .waitForSshLink()
                .ssh(shell -> {
                    userRunIP[0] = getRunIP(shell
                            .waitUntilTextAppears(userRunId1)
                            .execute(format(command[0], "$HOSTNAME"))
                            .lastCommandResult(format(command[0], "$HOSTNAME")));
                    shell.close();
                });
        runsMenu()
                .showLog(userRunId2)
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(userRunId2)
                        .execute(command[1])
                        .assertNextStringIsVisible("Complete!",
                                format("pipeline-%s", userRunId2))
                        .execute(format(command[2], userRunId1))
                        .assertPageAfterCommandContainsStrings(format(command[2], userRunId1), SERVER_IP,
                                format("Address: %s", userRunIP[0]))
                        .close()
                );
    }

    @Test
    @TestCase(value = "1900_2")
    public void supportDnsHostsManagementInSgeAutoscaler() {
        logoutIfNeeded();
        loginAs(user);
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .expandTab(EXEC_ENVIRONMENT)
                .expandTab(ADVANCED_PANEL)
                .setPriceType(ON_DEMAND)
                .doNotMountStoragesSelect(true)
                .enableClusterLaunch()
                .clusterSettingsForm("Auto-scaled cluster")
                .ok()
                .setCommand("qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:20 sleep infinity && sleep infinity")
                .clickAddBooleanParameter()
                .setName(CP_CAP_AUTOSCALE_DNS_HOSTS)
                .close()
                .doNotMountStoragesSelect(true)
                .launchTool(this, nameWithoutGroup(tool));
        final String parentRunId = getLastRunId();
        LogAO logAO = runsMenu()
                .showLog(parentRunId);
        String childRunID = logAO
                .waitForNestedRunsLink()
                .getNestedRunID(1);
        logAO
                .waitForSshLink()
                .waitForNestedRunWorking(childRunID)
                .ssh(shell -> {
                    shell
                            .waitUntilTextAppears(parentRunId)
                            .execute(command[1])
                            .assertNextStringIsVisible("Complete!",
                                    format("pipeline-%s", parentRunId))
                            .execute(format(command[2], childRunID))
                            .assertPageAfterCommandContainsStrings(format(command[2], childRunID), SERVER_IP);
                    String childRunIP = getRunIP(shell.lastCommandResult("Name:"));
                    shell
                            .execute(format(command[3], childRunIP))
                            .assertPageAfterCommandContainsStrings(format(command[3], childRunIP), "0")
                            .close();
                });
    }

    private String getRunIP(String logMessage) {
        final Matcher matcher = compile("\\d+\\.\\d+\\.\\d+\\.\\d+")
                .matcher(logMessage);
        if (!matcher.find()) {
            final String screenName = format("DnsHostsManagementTest_%s", Utils.randomSuffix());
            screenshot(screenName);
            throw new ElementNotFound(format("Could not get run IP from message: %s. Screenshot: %s.png", logMessage,
                    screenName), exist);
        }
        return matcher.group();
    }
}
