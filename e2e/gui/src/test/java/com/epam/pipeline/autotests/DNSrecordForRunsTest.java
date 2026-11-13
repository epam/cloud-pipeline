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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.epam.pipeline.autotests.ao.LogAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import static com.epam.pipeline.autotests.ao.Primitive.FRIENDLY_URL;
import static com.epam.pipeline.autotests.utils.C.VALID_ENDPOINT;
import static com.epam.pipeline.autotests.utils.C.ROOT_ADDRESS;
import static com.epam.pipeline.autotests.utils.C.LOGIN;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class DNSrecordForRunsTest extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private static final String SUB_DOMAIN = "Use sub-domain";
    private static final String testToolEndpoint = "E2E-Endpoint";
    private static final String otherEndpoint = "Test-Endpoint";
    private final String defaultRegistry = C.DEFAULT_REGISTRY;
    private final String defaultGroup = C.DEFAULT_GROUP;
    private final String testingTool = C.TESTING_TOOL_NAME;
    private final String testCapability = "NoMachine";
    private final String friendlyURL = "tool_page" + Utils.randomSuffix();
    private String runID1 = "";
    private String runID2 = "";
    private String edgeExternalAddress = "";
    private String edgeAddress = "";
    private String domainBasedToolEndpointLink = "";
    private String domainBasedToolFriendlyPathLink = "";
    private String suffixBasedNoMachineEndpointLink = "";
    private String suffixBasedNoMachineFriendlyPathLink = "";

    @BeforeClass
    public void instanceDnsHostedZoneBase() {
        edgeExternalAddress = navigationMenu()
                    .settings()
                    .switchToPreferences()
                    .getLinePreference("instance.dns.hosted.zone.base")[0];
    edgeAddress = ROOT_ADDRESS.replace("/pipeline/","")
                .replaceFirst("\\.", "-edge.");
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
                .launchTool(this, Utils.nameWithoutGroup(testingTool))
                .showLog(runID1 = getLastRunId());
        domainBasedToolEndpointLink = format("https://pipeline-%s-%s-0.%s/",
                runID1, VALID_ENDPOINT, edgeExternalAddress);
        suffixBasedNoMachineEndpointLink = format("%s/pipeline-%s-8089-0",
                edgeAddress, runID1);
        logAO.waitForSshLink()
                .waitForEndpointLink()
                .sleep(20, SECONDS)
                .refresh()
                .checkEndpointLink(testToolEndpoint, domainBasedToolEndpointLink)
                .checkEndpointLink(testCapability, suffixBasedNoMachineEndpointLink)
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
        domainBasedToolFriendlyPathLink = format("https://%s-e2e-endpoint.%s/", friendlyURL, edgeExternalAddress);
        suffixBasedNoMachineFriendlyPathLink = format("%s/%s-NoMachine", edgeAddress, friendlyURL);
        logAO.waitForSshLink()
                .waitForEndpointLink()
                .sleep(20, SECONDS)
                .refresh()
                .checkEndpointLink(testCapability, suffixBasedNoMachineFriendlyPathLink)
                .checkEndpointLink(testToolEndpoint, domainBasedToolFriendlyPathLink)
                .clickOnEndpointLink(testToolEndpoint)
                .sleep(3, SECONDS)
                .validateEndpointPage(LOGIN)
                .closeTab();
    }
}

