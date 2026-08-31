/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.epam.pipeline.autotests.ao.LogAO.Status.SUCCESS;
import static com.epam.pipeline.autotests.ao.LogAO.containsMessages;
import static com.epam.pipeline.autotests.ao.LogAO.log;
import static com.epam.pipeline.autotests.ao.Primitive.FRIENDLY_URL;
import static com.epam.pipeline.autotests.utils.C.ANOTHER_CLOUD_REGION;
import static com.epam.pipeline.autotests.utils.C.LOGIN;
import static com.epam.pipeline.autotests.utils.C.VALID_ENDPOINT;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class DNSrecordForRunsTest extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private static final String SUB_DOMAIN = "Use sub-domain";
    private static final String CREATE_DNS_RECORD = "CreateDNSRecord";
    private static final String testToolEndpoint = "E2E-Endpoint";
    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String testCapability = "NoMachine";
    private final String friendlyURL = "tool_page" + Utils.randomSuffix();
    private String runID1 = "";
    private String runID2 = "";
    private String edgeExternalAdress = "";
    private String instanceDnsHostedZoneBase = "";
    private String domainBasedToolEndpointLink = "";
    private String domainBasedToolFriendlyPathLink = "";
    private String suffixBasedToolEndpointLink = "";
    private String suffixBasedNoMachineEndpointLink = "";
    private String suffixBasedNoMachineFriendlyPathLink = "";

    @BeforeClass
    public void instanceDnsHostedZoneBase() {
        String[] pref = navigationMenu()
                .settings()
                .switchToPreferences()
                .getLinePreference("instance.dns.hosted.zone.base");
        edgeExternalAdress = pref[0]
                .replace("global.jobs", format("edge-%s.aws",
                        ANOTHER_CLOUD_REGION.substring(0, 2)));
        instanceDnsHostedZoneBase = pref[0]
                .replace("global", format("%s.%s", ANOTHER_CLOUD_REGION,
                        ANOTHER_CLOUD_REGION.substring(0, 2)));
    }

    @BeforeClass
    public void setToolEndpoint() {
        navigationMenu()
                .tools()
                .perform(defaultRegistry, defaultGroup, testingTool, tool ->
                        tool.settings()
                                .changeEndpointName(testToolEndpoint)
                                .save());
    }

    @AfterClass
    public void restoreToolConfiguration() {
        navigationMenu()
                .tools()
                .perform(defaultRegistry, defaultGroup, testingTool, tool ->
                        tool.settings()
                                .untickConfigureEndpoint(SUB_DOMAIN)
                                .changeEndpointPort(VALID_ENDPOINT)
                                .save());
    }

    @Test
    @TestCase(value = "1615_1")
    public void checkDNSrecordsForRunsWithoutPrettyURL() {
        LogAO logAO =
                navigationMenu()
                        .tools()
                        .perform(defaultRegistry, defaultGroup, testingTool, tool ->
                                tool.settings()
                                        .configureEndpoint(SUB_DOMAIN)
                                        .save())
                        .runWithCustomSettings()
                        .selectRunCapability(testCapability)
                        .doNotMountStoragesSelect(true)
                        .launch(this)
                        .showLog(runID1 = getLastRunId());
        domainBasedToolEndpointLink = format("pipeline-%s-%s-0.%s",
                runID1, VALID_ENDPOINT, instanceDnsHostedZoneBase);
        suffixBasedNoMachineEndpointLink = format("https://%s/pipeline-%s-8089-0", edgeExternalAdress, runID1);
        logAO.waitForSshLink()
                .waitForEndpointLink()
                .waitForTaskStatus(CREATE_DNS_RECORD, SUCCESS)
                .sleep(20, SECONDS)
                .refresh()
                .checkEndpointLink(testCapability, suffixBasedNoMachineEndpointLink)
                .checkEndpointLink(testToolEndpoint, format("https://%s/", domainBasedToolEndpointLink))
                .clickTaskWithName(CREATE_DNS_RECORD)
                .ensure(log(), containsMessages(format("Created DNS record %s", domainBasedToolEndpointLink)))
                .clickOnEndpointLink(testToolEndpoint)
                .sleep(3, SECONDS)
                .validateEndpointPage(LOGIN)
                .closeTab();
    }

    @Test (dependsOnMethods = "checkDNSrecordsForRunsWithoutPrettyURL")
    @TestCase(value = "1615_2")
    public void checkDNSrecordsForRunsWithPrettyURL() {
        LogAO logAO = navigationMenu()
                .tools()
                .perform(defaultRegistry, defaultGroup, testingTool, ToolTab::runWithCustomSettings)
                .selectRunCapability(testCapability)
                .doNotMountStoragesSelect(true)
                .setValue(FRIENDLY_URL, friendlyURL)
                .launch(this)
                .showLog(runID2 = getLastRunId());
        domainBasedToolFriendlyPathLink = format("%s.%s", friendlyURL, instanceDnsHostedZoneBase);
        suffixBasedNoMachineFriendlyPathLink = format("https://%s/%s-NoMachine", edgeExternalAdress, friendlyURL);
        logAO.waitForSshLink()
                .waitForEndpointLink()
                .waitForTaskStatus(CREATE_DNS_RECORD, SUCCESS)
                .sleep(20, SECONDS)
                .refresh()
                .checkEndpointLink(testCapability, suffixBasedNoMachineFriendlyPathLink)
                .checkEndpointLink(testToolEndpoint, format("https://%s/", domainBasedToolFriendlyPathLink))
                .clickTaskWithName(CREATE_DNS_RECORD)
                .ensure(log(), containsMessages(format("Created DNS record %s", domainBasedToolFriendlyPathLink)))
                .clickOnEndpointLink(testToolEndpoint)
                .sleep(3, SECONDS)
                .validateEndpointPage(LOGIN)
                .closeTab();
    }

    @Test (dependsOnMethods = "checkDNSrecordsForRunsWithPrettyURL")
    @TestCase(value = "1615_3")
    public void checkEndpointChangeInActiveRun() {
        navigationMenu()
                .tools()
                .perform(defaultRegistry, defaultGroup, testingTool, tool ->
                        tool.settings()
                                .untickConfigureEndpoint(SUB_DOMAIN)
                                .save());
        suffixBasedToolEndpointLink = format("https://%s/pipeline-%s-%s-0", edgeExternalAdress,
                runID1, VALID_ENDPOINT);
        runsMenu()
                .showLog(runID1)
                .checkEndpointLink(testToolEndpoint, format("https://%s/", domainBasedToolEndpointLink))
                .shareWithUser(user.login, false)
                .validateShareLink(user.login)
                .sleep(3, MINUTES)
                .refresh()
                .checkEndpointLink(testToolEndpoint, suffixBasedToolEndpointLink);
        runsMenu()
                .showLog(runID2)
                .checkEndpointLink(testToolEndpoint, format("https://%s/", domainBasedToolFriendlyPathLink));
        navigationMenu()
                .tools()
                .perform(defaultRegistry, defaultGroup, testingTool, tool ->
                        tool.settings()
                                .changeEndpointPort("8082")
                                .save());
        runsMenu()
                .showLog(runID1)
                .checkEndpointLink(testToolEndpoint, suffixBasedToolEndpointLink);
        runsMenu()
                .showLog(runID2)
                .checkEndpointLink(testToolEndpoint, suffixBasedToolEndpointLink);
    }
}

