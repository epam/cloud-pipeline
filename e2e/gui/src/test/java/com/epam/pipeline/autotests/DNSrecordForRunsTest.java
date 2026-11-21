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

import org.apache.commons.lang3.StringUtils;
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
    private static final String JOB_NAME = "{job_name}";
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

    /**
     * The DNS zone of the sub-domain endpoints: the one of the cloud region of the edge whose link the GUI shows
     * (e2e.ui.dns.hosted.zone.base, as run-e2e.sh writes it), else the instance.dns.hosted.zone.base preference,
     * which the API takes for a region without a zone of its own.
     */
    @BeforeClass
    public void instanceDnsHostedZoneBase() {
        edgeExternalAddress = StringUtils.isNotBlank(C.DNS_HOSTED_ZONE_BASE)
                ? C.DNS_HOSTED_ZONE_BASE
                : navigationMenu()
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
        domainBasedToolEndpointLink = domainBasedLink(format("pipeline-%s-%s-0", runID1, VALID_ENDPOINT));
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
        domainBasedToolFriendlyPathLink = domainBasedLink(format("%s-e2e-endpoint", friendlyURL));
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

    /**
     * The link of a sub-domain endpoint: the edge names its DNS record with CP_EDGE_DNS_RECORD_FORMAT, the endpoint
     * location for {job_name} (e2e.ui.edge.dns.record.format, with {region_name} filled in by run-e2e.sh for the edge
     * of the default.edge.region preference; "{job_name}" if not set), and the API appends the DNS zone.
     */
    private String domainBasedLink(final String jobName) {
        final String recordFormat = StringUtils.isNotBlank(C.EDGE_DNS_RECORD_FORMAT)
                ? C.EDGE_DNS_RECORD_FORMAT
                : JOB_NAME;
        return format("https://%s.%s/", recordFormat.replace(JOB_NAME, jobName), edgeExternalAddress);
    }
}
