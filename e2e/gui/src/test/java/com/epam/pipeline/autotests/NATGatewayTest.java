/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.ConfirmationPopupAO;
import com.epam.pipeline.autotests.ao.NATGatewayAO;
import com.epam.pipeline.autotests.ao.SystemManagementAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.apache.commons.lang3.StringUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.testng.internal.collections.Pair;

import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.Primitive.ADD;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_PORT;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_ROUTE;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.COMMENT;
import static com.epam.pipeline.autotests.ao.Primitive.IP;
import static com.epam.pipeline.autotests.ao.Primitive.PORT;
import static com.epam.pipeline.autotests.ao.Primitive.REFRESH;
import static com.epam.pipeline.autotests.ao.Primitive.RESOLVE;
import static com.epam.pipeline.autotests.ao.Primitive.REVERT;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.SERVER_NAME;
import static com.epam.pipeline.autotests.ao.Primitive.SPECIFY_IP;
import static com.epam.pipeline.autotests.ao.Primitive.SYSTEM_LOGS_TAB;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public class NATGatewayTest extends AbstractSinglePipelineRunningTest implements Authorization {

    private static final String FIELD_IS_REQUIRED_WARNING = "Field is required";
    private static final String GOOGLE_COM_SERVER_NAME = "google.com";
    private static final String YAHOO_COM_SERVER_NAME = "yahoo.com";
    private static final String DUCKDUCKGO_COM_SERVER_NAME = "duckduckgo.com";
    private static final String BING_COM_SERVER_NAME = "bing.com";
    private static final String PORT_80 = "80";
    private static final String PORT_443 = "443";
    private static final String COMMENT_1 = "port1";
    private static final String COMMENT_2 = "port2";
    private static final String COMMAND_1 = "unset http_proxy https_proxy";
    private static final String COMMAND_2 = "curl %s -v -ipv4";
    private static final String CONNECTED_FORMAT = "Connected to %s (%s) port %s";
    private static final String TRYING_FORMAT = "Trying %s...";
    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private String google80ExternalIPAddress;
    private String google80InternalIPAddress;
    private String yahoo80InternalIPAddress;

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        refresh();
        logoutIfNeeded();
        loginAs(admin);
        Stream.of(
                Pair.of(google80ExternalIPAddress, PORT_80),
                Pair.of(google80ExternalIPAddress, PORT_443),
                Pair.of(YAHOO_COM_SERVER_NAME, PORT_80),
                Pair.of(YAHOO_COM_SERVER_NAME, PORT_443),
                Pair.of(DUCKDUCKGO_COM_SERVER_NAME, PORT_80),
                Pair.of(DUCKDUCKGO_COM_SERVER_NAME, PORT_443),
                Pair.of(BING_COM_SERVER_NAME, PORT_80),
                Pair.of(BING_COM_SERVER_NAME, PORT_443)
        ).forEach(p -> deleteRoute(p.first(), p.second()));
    }

    @Test
    @TestCase(value = {"2232_1"})
    public void checkAddNewRouteForm() {
        final NATGatewayAO.NATAddRouteAO natAddRouteAO = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .ensure(REFRESH, visible, enabled)
                .ensure(ADD_ROUTE, visible, enabled)
                .ensure(SAVE, visible, disabled)
                .ensure(REVERT, visible, disabled)
                .addRoute()
                .ensure(CANCEL, visible, enabled)
                .ensure(ADD_PORT, visible, enabled)
                .ensure(ADD, visible, disabled)
                .click(SPECIFY_IP)
                .ensure(RESOLVE, visible, disabled)
                .setServerName("test")
                .click(COMMENT)
                .messageShouldAppear("Unable to resolve the given hostname: test")
                .checkFieldWarning(IP, FIELD_IS_REQUIRED_WARNING)
                .checkFieldWarning(PORT, FIELD_IS_REQUIRED_WARNING)
                .clear(SERVER_NAME)
                .setServerName(GOOGLE_COM_SERVER_NAME)
                .click(COMMENT)
                .sleep(2, SECONDS)
                .checkIPAddress()
                .clear(IP)
                .setValue(IP, "127.1.1")
                .checkFieldWarning(IP, "Invalid format")
                .click(RESOLVE)
                .sleep(2, SECONDS)
                .checkIPAddress()
                .setValue(PORT, PORT_80)
                .ensure(ADD, enabled)
                .setValue(COMMENT, "port1");
        final String ipAddress = natAddRouteAO.getIPAddress();
        natAddRouteAO
                .addRoute()
                .ensure(SAVE, visible, enabled)
                .checkRouteRecord(ipAddress, GOOGLE_COM_SERVER_NAME, PORT_80)
                .ensure(REVERT, visible, enabled)
                .click(REVERT)
                .ensure(SAVE, visible, disabled)
                .ensure(REVERT, visible, disabled)
                .checkNoRouteRecord(ipAddress, PORT_80);
    }

    @Test
    @TestCase(value = {"2232_2"})
    public void checkNewRouteCreationWithSpecifiedIPAddress() {
        final NATGatewayAO.NATAddRouteAO natAddRouteAO = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .sleep(1, SECONDS)
                .addRoute()
                .click(SPECIFY_IP)
                .setServerName(GOOGLE_COM_SERVER_NAME)
                .click(COMMENT)
                .sleep(2, SECONDS)
                .checkIPAddress()
                .setValue(PORT, PORT_80)
                .ensure(ADD, enabled)
                .setValue(COMMENT, COMMENT_1);
        google80ExternalIPAddress = natAddRouteAO.getIPAddress();
        google80InternalIPAddress = natAddRouteAO
                .addRoute()
                .checkRouteRecord(google80ExternalIPAddress, GOOGLE_COM_SERVER_NAME, PORT_80)
                .click(SAVE)
                .ensure(SAVE, visible, disabled)
                .ensure(REVERT, visible, disabled)
                .checkCreationScheduled(google80ExternalIPAddress, PORT_80)
                .waitRouteRecordCreationScheduled(google80ExternalIPAddress, PORT_80)
                .checkActiveRouteRecord(google80ExternalIPAddress, GOOGLE_COM_SERVER_NAME, COMMENT_1, PORT_80)
                .getInternalIP(google80ExternalIPAddress, PORT_80);
        tools().perform(registry, group, tool, tool -> tool.run(this))
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, GOOGLE_COM_SERVER_NAME))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, google80InternalIPAddress),
                                format(CONNECTED_FORMAT, GOOGLE_COM_SERVER_NAME, google80InternalIPAddress, PORT_80))
                        .close());
    }

    @Test(dependsOnMethods = "checkNewRouteCreationWithSpecifiedIPAddress")
    @TestCase(value = {"2232_3"})
    public void checkNewRouteCreationWithoutSpecifiedIPAddress() {
        yahoo80InternalIPAddress = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .sleep(1, SECONDS)
                .addRoute()
                .setServerName(YAHOO_COM_SERVER_NAME)
                .setValue(PORT, PORT_80)
                .setValue(COMMENT, COMMENT_1)
                .addRoute()
                .checkRouteRecordByServerName(YAHOO_COM_SERVER_NAME, PORT_80)
                .click(SAVE)
                .ensure(SAVE, visible, disabled)
                .ensure(REVERT, visible, disabled)
                .checkCreationScheduled(YAHOO_COM_SERVER_NAME, PORT_80)
                .waitRouteRecordCreationScheduled(YAHOO_COM_SERVER_NAME, PORT_80)
                .checkActiveRouteRecord(StringUtils.EMPTY, YAHOO_COM_SERVER_NAME, COMMENT_1, PORT_80)
                .getInternalIP(YAHOO_COM_SERVER_NAME, PORT_80);
        runsMenu()
                .showLog(getRunId())
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, YAHOO_COM_SERVER_NAME))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, yahoo80InternalIPAddress),
                                format(CONNECTED_FORMAT, YAHOO_COM_SERVER_NAME, yahoo80InternalIPAddress, PORT_80))
                        .close());
    }

    @Test(dependsOnMethods = "checkNewRouteCreationWithSpecifiedIPAddress")
    @TestCase(value = {"2232_4"})
    public void checkRouteWithExistingNameAndDifferentIP() {
        final String[] eightBitNumbers = google80ExternalIPAddress.split("\\.");
        final String invalidExternalIP = format("%s.%s.%s.%s", eightBitNumbers[3], eightBitNumbers[2],
                eightBitNumbers[1], eightBitNumbers[0]);
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .sleep(1, SECONDS)
                .addRoute()
                .setServerName(GOOGLE_COM_SERVER_NAME)
                .click(SPECIFY_IP)
                .clear(IP)
                .setValue(IP, invalidExternalIP)
                .setValue(PORT, PORT_443)
                .addRoute()
                .checkRouteRecord(invalidExternalIP, GOOGLE_COM_SERVER_NAME, PORT_443)
                .click(SAVE)
                .checkCreationScheduled(GOOGLE_COM_SERVER_NAME, PORT_443)
                .waitRouteRecordCreationScheduled(GOOGLE_COM_SERVER_NAME, PORT_443)
                .checkFailedRouteRecord(invalidExternalIP, GOOGLE_COM_SERVER_NAME, PORT_443)
                .deleteRoute(invalidExternalIP, PORT_443)
                .click(SAVE)
                .waitRouteRecordTerminationScheduled(invalidExternalIP, PORT_443)
                .checkNoRouteRecord(invalidExternalIP, PORT_443);
    }

    @Test(dependsOnMethods = "checkNewRouteCreationWithSpecifiedIPAddress")
    @TestCase(value = {"2232_5"})
    public void checkAddingRouteWithoutResolvedIPToExistingRouteWithResolvedIP() {
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .addRoute()
                .setServerName(GOOGLE_COM_SERVER_NAME)
                .setValue(PORT, PORT_443)
                .addRoute()
                .checkRouteRecordByServerName(GOOGLE_COM_SERVER_NAME, PORT_443)
                .click(SAVE)
                .checkCreationScheduled(GOOGLE_COM_SERVER_NAME, PORT_443)
                .waitRouteRecordCreationScheduled(GOOGLE_COM_SERVER_NAME, PORT_443)
                .checkFailedRouteRecord("", GOOGLE_COM_SERVER_NAME, PORT_443)
                .deleteRoute(GOOGLE_COM_SERVER_NAME, PORT_443)
                .click(SAVE)
                .waitRouteRecordTerminationScheduled(GOOGLE_COM_SERVER_NAME, PORT_443)
                .checkNoRouteRecord(GOOGLE_COM_SERVER_NAME, PORT_443);
    }

    @Test(dependsOnMethods = {"checkNewRouteCreationWithSpecifiedIPAddress",
            "checkNewRouteCreationWithoutSpecifiedIPAddress"})
    @TestCase(value = {"2232_6"})
    public void checkAddingRouteWithResolvedIPToExistingRouteWithoutResolvedIP() {
        final NATGatewayAO.NATAddRouteAO natAddRouteAO = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .addRoute()
                .setServerName(YAHOO_COM_SERVER_NAME)
                .click(SPECIFY_IP)
                .setValue(PORT, PORT_443);
        final String externalIPAddress = natAddRouteAO.getIPAddress();
        natAddRouteAO
                .addRoute()
                .click(SAVE)
                .checkCreationScheduled(externalIPAddress, PORT_443)
                .waitRouteRecordCreationScheduled(externalIPAddress, PORT_443)
                .checkFailedRouteRecord(externalIPAddress, YAHOO_COM_SERVER_NAME, PORT_443)
                .deleteRoute(externalIPAddress, PORT_443)
                .click(SAVE)
                .waitRouteRecordTerminationScheduled(externalIPAddress, PORT_443)
                .checkNoRouteRecord(externalIPAddress, PORT_443);
    }

    @Test(dependsOnMethods = {"checkNewRouteCreationWithSpecifiedIPAddress"}, priority = 1)
    @TestCase(value = {"2232_7"})
    public void checkAddingRouteWithResolvedIPToExistingRoute() {
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .addRoute()
                .setServerName(GOOGLE_COM_SERVER_NAME)
                .click(SPECIFY_IP)
                .clear(IP)
                .setValue(IP, google80ExternalIPAddress)
                .setValue(PORT, PORT_80)
                .checkFieldWarning(PORT, "Value should be unique")
                .clear(PORT)
                .setValue(PORT, PORT_443)
                .addRoute()
                .click(SAVE)
                .checkCreationScheduled(GOOGLE_COM_SERVER_NAME, PORT_443)
                .waitRouteRecordCreationScheduled(GOOGLE_COM_SERVER_NAME, PORT_443)
                .checkActiveRouteRecord(google80ExternalIPAddress, GOOGLE_COM_SERVER_NAME, "", PORT_443);
        runsMenu()
                .showLog(getRunId())
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", GOOGLE_COM_SERVER_NAME, PORT_443)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, google80InternalIPAddress),
                                format(CONNECTED_FORMAT, GOOGLE_COM_SERVER_NAME, google80InternalIPAddress, PORT_443))
                        .close());
    }

    @Test(dependsOnMethods = {"checkNewRouteCreationWithoutSpecifiedIPAddress"}, priority = 2)
    @TestCase(value = {"2232_8"})
    public void checkAddingRouteWithoutResolvedIPToExistingRoute() {
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .addRoute()
                .setServerName(YAHOO_COM_SERVER_NAME)
                .setValue(PORT, PORT_80)
                .checkFieldWarning(PORT, "Value should be unique")
                .clear(PORT)
                .setValue(PORT, PORT_443)
                .addRoute()
                .click(SAVE)
                .checkCreationScheduled(YAHOO_COM_SERVER_NAME, PORT_443)
                .waitRouteRecordCreationScheduled(YAHOO_COM_SERVER_NAME, PORT_443)
                .checkActiveRouteRecord(yahoo80InternalIPAddress, YAHOO_COM_SERVER_NAME, "", PORT_443);
        runsMenu()
                .showLog(getRunId())
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", YAHOO_COM_SERVER_NAME, PORT_443)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, yahoo80InternalIPAddress),
                                format(CONNECTED_FORMAT, YAHOO_COM_SERVER_NAME, yahoo80InternalIPAddress, PORT_443))
                        .close());
    }

    @Test(dependsOnMethods = {"checkNewRouteCreationWithSpecifiedIPAddress"}, priority = 1)
    @TestCase(value = {"2232_9"})
    public void checkAddingRouteWithSeveralPorts() {
        final SystemManagementAO systemManagementAO = navigationMenu()
                .settings()
                .switchToSystemManagement();
        final NATGatewayAO natGatewayAO = systemManagementAO
                .switchToNATGateway()
                .addRoute()
                .setServerName(DUCKDUCKGO_COM_SERVER_NAME)
                .setValue(PORT, PORT_80)
                .click(ADD_PORT)
                .ensure(ADD, disabled)
                .addMorePorts(PORT_443)
                .ensure(ADD, enabled)
                .addRoute()
                .checkRouteRecord(DUCKDUCKGO_COM_SERVER_NAME, DUCKDUCKGO_COM_SERVER_NAME, PORT_80)
                .checkRouteRecord(DUCKDUCKGO_COM_SERVER_NAME, DUCKDUCKGO_COM_SERVER_NAME, PORT_443);
        systemManagementAO
                .click(SYSTEM_LOGS_TAB)
                .also(() -> new ConfirmationPopupAO<>(this)
                        .ensureTitleIs("You have unsaved changes. Continue?")
                        .cancel());
        final String internalIPPort80 = natGatewayAO
                .click(SAVE)
                .checkCreationScheduled(DUCKDUCKGO_COM_SERVER_NAME, PORT_80)
                .checkCreationScheduled(DUCKDUCKGO_COM_SERVER_NAME, PORT_443)
                .waitRouteRecordCreationScheduled(DUCKDUCKGO_COM_SERVER_NAME, PORT_80)
                .waitRouteRecordCreationScheduled(DUCKDUCKGO_COM_SERVER_NAME, PORT_443)
                .getInternalIP(DUCKDUCKGO_COM_SERVER_NAME, PORT_80);
        final String internalIPPort443 = natGatewayAO.getInternalIP(DUCKDUCKGO_COM_SERVER_NAME, PORT_443);
        final String internalPortPort80 = natGatewayAO.getInternalPort(DUCKDUCKGO_COM_SERVER_NAME, PORT_80);
        final String internalPortPort443 = natGatewayAO.getInternalPort(DUCKDUCKGO_COM_SERVER_NAME, PORT_443);
        assertEquals(internalIPPort80, internalIPPort443);
        assertNotEquals(internalPortPort80, internalPortPort443);
        runsMenu()
                .showLog(getRunId())
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", DUCKDUCKGO_COM_SERVER_NAME, PORT_80)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, internalIPPort80),
                                format(CONNECTED_FORMAT, DUCKDUCKGO_COM_SERVER_NAME, internalIPPort80, PORT_80))
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", DUCKDUCKGO_COM_SERVER_NAME, PORT_443)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, internalIPPort80),
                                format(CONNECTED_FORMAT, DUCKDUCKGO_COM_SERVER_NAME, internalIPPort80, PORT_443))
                        .close());
    }

    @Test(dependsOnMethods = {"checkNewRouteCreationWithSpecifiedIPAddress"}, priority = 1)
    @TestCase(value = {"2232_10"})
    public void checkAddingSeveralRoutesWithSameServerNameButDiffPorts() {
        final NATGatewayAO natGatewayAO = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .sleep(1, SECONDS)
                .addRoute()
                .setServerName(BING_COM_SERVER_NAME)
                .click(SPECIFY_IP)
                .setValue(PORT, PORT_80)
                .setValue(COMMENT, COMMENT_1)
                .addRoute()
                .sleep(1, SECONDS)
                .addRoute()
                .setServerName(BING_COM_SERVER_NAME)
                .click(SPECIFY_IP)
                .sleep(1, SECONDS)
                .setValue(PORT, PORT_443)
                .setValue(COMMENT, COMMENT_2)
                .addRoute()
                .click(SAVE)
                .checkCreationScheduled(BING_COM_SERVER_NAME, PORT_80)
                .checkCreationScheduled(BING_COM_SERVER_NAME, PORT_443)
                .waitRouteRecordCreationScheduled(BING_COM_SERVER_NAME, PORT_80)
                .waitRouteRecordCreationScheduled(BING_COM_SERVER_NAME, PORT_443);
        final String internalIPPort80 = natGatewayAO.getInternalIP(BING_COM_SERVER_NAME, PORT_80);
        final String internalIPPort443 = natGatewayAO.getInternalIP(BING_COM_SERVER_NAME, PORT_443);
        final String internalPortPort80 = natGatewayAO.getInternalPort(BING_COM_SERVER_NAME, PORT_80);
        final String internalPortPort443 = natGatewayAO.getInternalPort(BING_COM_SERVER_NAME, PORT_443);
        final String commentPort80 = natGatewayAO.getComment(BING_COM_SERVER_NAME, PORT_80);
        final String commentPort443 = natGatewayAO.getComment(BING_COM_SERVER_NAME, PORT_443);
        assertEquals(internalIPPort80, internalIPPort443);
        assertNotEquals(internalPortPort80, internalPortPort443);
        assertNotEquals(commentPort80, commentPort443);
        runsMenu()
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", BING_COM_SERVER_NAME, PORT_80)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, internalIPPort80),
                                format(CONNECTED_FORMAT, BING_COM_SERVER_NAME, internalIPPort80, PORT_80))
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", BING_COM_SERVER_NAME, PORT_443)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, internalIPPort80),
                                format(CONNECTED_FORMAT, BING_COM_SERVER_NAME, internalIPPort80, PORT_443))
                        .close());
    }

    private void deleteRoute(final String externalIPAddress, final String port) {
        if (StringUtils.isBlank(externalIPAddress)) {
            return;
        }
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .deleteRoute(externalIPAddress, port)
                .click(SAVE);
    }
}
