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

import com.epam.pipeline.autotests.ao.ConfirmationPopupAO;
import com.epam.pipeline.autotests.ao.NATGatewayAO;
import com.epam.pipeline.autotests.ao.SystemManagementAO;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.apache.commons.lang3.StringUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.internal.collections.Pair;

import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.Primitive.ADD;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_PORT;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_ROUTE;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.COMMENT;
import static com.epam.pipeline.autotests.ao.Primitive.IP;
import static com.epam.pipeline.autotests.ao.Primitive.PORT;
import static com.epam.pipeline.autotests.ao.Primitive.PROTOCOL;
import static com.epam.pipeline.autotests.ao.Primitive.REFRESH;
import static com.epam.pipeline.autotests.ao.Primitive.RESOLVE;
import static com.epam.pipeline.autotests.ao.Primitive.REVERT;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.SERVER_NAME;
import static com.epam.pipeline.autotests.ao.Primitive.SPECIFY_IP;
import static com.epam.pipeline.autotests.ao.Primitive.SYSTEM_LOGS_TAB;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.tagName;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public class NATGatewayTest extends AbstractSinglePipelineRunningTest implements Authorization {

    private static final int NAT_PROXY_SERVER_NAMES_SIZE = 4;

    static {
        if (C.NAT_PROXY_SERVER_NAMES.isEmpty() || C.NAT_PROXY_SERVER_NAMES.size() < NAT_PROXY_SERVER_NAMES_SIZE) {
            throw new IllegalArgumentException(
                    format("Nat proxy server names is not defined or not enough. It should be set %s server names",
                            NAT_PROXY_SERVER_NAMES_SIZE));
        }
        SERVER_NAME_1 = C.NAT_PROXY_SERVER_NAMES.get(0);
        SERVER_NAME_2 = C.NAT_PROXY_SERVER_NAMES.get(1);
        SERVER_NAME_3 = C.NAT_PROXY_SERVER_NAMES.get(2);
        SERVER_NAME_4 = C.NAT_PROXY_SERVER_NAMES.get(3);
        SERVER_NAME_5 = C.NAT_PROXY_SERVER_NAMES.get(4);
    }

    private static final String FIELD_IS_REQUIRED_WARNING = "Field is required";
    private static final String PORT_IS_REQUIRED_WARNING = "Port is required";
    private static final String SERVER_NAME_1;
    private static final String SERVER_NAME_2;
    private static final String SERVER_NAME_3;
    private static final String SERVER_NAME_4;
    private static final String SERVER_NAME_5;
    private static final String PORT_80 = "80";
    private static final String PORT_443 = "443";
    private static final String PROTOCOL_UDP = "UDP";
    private static final String COMMENT_1 = "port1";
    private static final String COMMENT_2 = "port2";
    private static final String COMMAND_1 = "unset http_proxy https_proxy";
    private static final String COMMAND_2 = "curl %s -v -ipv4";
    private static final String CONNECTED_FORMAT = "Connected to %s (%s) port %s";
    private static final String TRYING_FORMAT = "Trying %s...";
    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private String server1Port80ExternalIPAddress;
    private String server1Port80InternalIPAddress;
    private String server2Port80InternalIPAddress;

    @BeforeClass
    @AfterClass(alwaysRun = true)
    public void checkExistenceRoutes() {
        refresh();
        logoutIfNeeded();
        loginAs(admin);
        final NATGatewayAO natGatewayAO = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .sleep(2, SECONDS);
        Stream.of(
                Pair.of(SERVER_NAME_1, PORT_80),
                Pair.of(SERVER_NAME_1, PORT_443),
                Pair.of(SERVER_NAME_2, PORT_80),
                Pair.of(SERVER_NAME_2, PORT_443),
                Pair.of(SERVER_NAME_3, PORT_80),
                Pair.of(SERVER_NAME_3, PORT_443),
                Pair.of(SERVER_NAME_4, PORT_80),
                Pair.of(SERVER_NAME_4, PORT_443),
                Pair.of(SERVER_NAME_5, PORT_80),
                Pair.of(SERVER_NAME_5, PORT_443)
                )
                .filter(p -> natGatewayAO
                        .expandGroup(p.first(), p.second())
                        .routeRecordExist(p.first(), p.second()))
                .forEach(p -> deleteRoute(p.first(), p.second()));
        sleep(1, MINUTES);
        int attempt = 0;
        int maxAttempts = 60;
        while (natGatewayAO.context().$$(byClassName("ant-table-row")).stream()
                .filter(element -> element.findAll(".external-column").get(0)
                        .find(tagName("i")).has(cssClass("anticon-clock-circle-o"))).count() > 0
                && attempt < maxAttempts) {
            natGatewayAO.click(REFRESH);
            sleep(10, SECONDS);
            attempt++;
        }
    }

    @BeforeMethod
    void openApplication() {
        open(C.ROOT_ADDRESS);
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
                .ensure(PROTOCOL, text("TCP"))
                .click(SPECIFY_IP)
                .ensure(RESOLVE, visible, disabled)
                .setServerName("test")
                .click(COMMENT)
                .messageShouldAppear("Unable to resolve the given hostname: test")
                .checkFieldWarning(IP, FIELD_IS_REQUIRED_WARNING)
                .checkFieldWarning(PORT, PORT_IS_REQUIRED_WARNING)
                .clear(SERVER_NAME)
                .setServerName(SERVER_NAME_1)
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
                .checkRouteRecord(ipAddress, SERVER_NAME_1, PORT_80)
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
                .setServerName(SERVER_NAME_1)
                .click(COMMENT)
                .sleep(2, SECONDS)
                .checkIPAddress()
                .setValue(PORT, PORT_80)
                .ensure(ADD, enabled)
                .setValue(COMMENT, COMMENT_1);
        server1Port80ExternalIPAddress = natAddRouteAO.getIPAddress();
        server1Port80InternalIPAddress = natAddRouteAO
                .addRoute()
                .checkRouteRecord(server1Port80ExternalIPAddress, SERVER_NAME_1, PORT_80)
                .sleep(2, SECONDS)
                .click(SAVE)
                .ensure(SAVE, visible, disabled)
                .ensure(REVERT, visible, disabled)
                .checkCreationScheduled(server1Port80ExternalIPAddress, PORT_80)
                .waitRouteRecordCreationScheduled(server1Port80ExternalIPAddress, PORT_80)
                .checkActiveRouteRecord(server1Port80ExternalIPAddress, SERVER_NAME_1, COMMENT_1, PORT_80)
                .getInternalIP(server1Port80ExternalIPAddress, PORT_80);
        tools().perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .doNotMountStoragesSelect(true)
                .setPriceType(ON_DEMAND)
                .launch(this)
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, SERVER_NAME_1))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, server1Port80InternalIPAddress),
                                format(CONNECTED_FORMAT, SERVER_NAME_1, server1Port80InternalIPAddress, PORT_80))
                        .close());
    }

    @Test(dependsOnMethods = "checkNewRouteCreationWithSpecifiedIPAddress")
    @TestCase(value = {"2232_3"})
    public void checkNewRouteCreationWithoutSpecifiedIPAddress() {
        server2Port80InternalIPAddress = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .sleep(1, SECONDS)
                .addRoute()
                .setServerName(SERVER_NAME_2)
                .setValue(PORT, PORT_80)
                .setValue(COMMENT, COMMENT_1)
                .addRoute()
                .checkRouteRecordByServerName(SERVER_NAME_2, PORT_80)
                .click(SAVE)
                .ensure(SAVE, visible, disabled)
                .ensure(REVERT, visible, disabled)
                .checkCreationScheduled(SERVER_NAME_2, PORT_80)
                .waitRouteRecordCreationScheduled(SERVER_NAME_2, PORT_80)
                .checkActiveRouteRecord(StringUtils.EMPTY, SERVER_NAME_2, COMMENT_1, PORT_80)
                .getInternalIP(SERVER_NAME_2, PORT_80);
        runsMenu()
                .showLog(getRunId())
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, SERVER_NAME_2))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, server2Port80InternalIPAddress),
                                format(CONNECTED_FORMAT, SERVER_NAME_2, server2Port80InternalIPAddress, PORT_80))
                        .close());
    }

    @Test(dependsOnMethods = "checkNewRouteCreationWithSpecifiedIPAddress")
    @TestCase(value = {"2232_4"})
    public void checkRouteWithExistingNameAndDifferentIP() {
        final String[] eightBitNumbers = server1Port80ExternalIPAddress.split("\\.");
        final String invalidExternalIP = format("%s.%s.%s.%s", eightBitNumbers[3], eightBitNumbers[2],
                eightBitNumbers[1], eightBitNumbers[0]);
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .sleep(1, SECONDS)
                .addRoute()
                .setServerName(SERVER_NAME_1)
                .click(SPECIFY_IP)
                .clear(IP)
                .setValue(IP, invalidExternalIP)
                .setValue(PORT, PORT_443)
                .addRoute()
                .checkRouteRecord(invalidExternalIP, SERVER_NAME_1, PORT_443)
                .click(SAVE)
                .checkCreationScheduled(SERVER_NAME_1, PORT_443)
                .waitRouteRecordCreationScheduled(SERVER_NAME_1, PORT_443)
                .checkFailedRouteRecord(invalidExternalIP, SERVER_NAME_1, PORT_443)
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
                .setServerName(SERVER_NAME_1)
                .setValue(PORT, PORT_443)
                .addRoute()
                .checkRouteRecordByServerName(SERVER_NAME_1, PORT_443)
                .click(SAVE)
                .checkCreationScheduled(SERVER_NAME_1, PORT_443)
                .waitRouteRecordCreationScheduled(SERVER_NAME_1, PORT_443)
                .checkFailedRouteRecord("", SERVER_NAME_1, PORT_443)
                .deleteRoute(SERVER_NAME_1, PORT_443)
                .click(SAVE)
                .waitRouteRecordTerminationScheduled(SERVER_NAME_1, PORT_443)
                .checkNoRouteRecord(SERVER_NAME_1, PORT_443);
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
                .setServerName(SERVER_NAME_2)
                .click(SPECIFY_IP)
                .setValue(PORT, PORT_443);
        final String externalIPAddress = natAddRouteAO.getIPAddress();
        natAddRouteAO
                .addRoute()
                .click(SAVE)
                .checkCreationScheduled(externalIPAddress, PORT_443)
                .waitRouteRecordCreationScheduled(externalIPAddress, PORT_443)
                .checkFailedRouteRecord(externalIPAddress, SERVER_NAME_2, PORT_443)
                .deleteRoute(externalIPAddress, PORT_443)
                .click(SAVE)
                .waitRouteRecordTerminationScheduled(externalIPAddress, PORT_443)
                .checkNoRouteRecord(externalIPAddress, PORT_443);
    }

    @Test(dependsOnMethods = "checkNewRouteCreationWithSpecifiedIPAddress", priority = 1)
    @TestCase(value = {"2232_7"})
    public void checkAddingRouteWithResolvedIPToExistingRoute() {
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .addRoute()
                .setServerName(SERVER_NAME_1)
                .click(SPECIFY_IP)
                .clear(IP)
                .setValue(IP, server1Port80ExternalIPAddress)
                .setValue(PORT, PORT_80)
                .checkFieldWarning(PORT, format("Duplicate port %s", PORT_80))
                .clear(PORT)
                .setValue(PORT, PORT_443)
                .addRoute()
                .click(SAVE)
                .checkCreationScheduled(SERVER_NAME_1, PORT_443)
                .waitRouteRecordCreationScheduled(SERVER_NAME_1, PORT_443)
                .checkGroupPortsList(SERVER_NAME_1, PORT_443, asList(PORT_80, PORT_443))
                .expandGroup(SERVER_NAME_1, PORT_443)
                .checkActiveRouteRecord(server1Port80ExternalIPAddress, SERVER_NAME_1, "", PORT_443);
        runsMenu()
                .showLog(getRunId())
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", SERVER_NAME_1, PORT_443)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, server1Port80InternalIPAddress),
                                format(CONNECTED_FORMAT, SERVER_NAME_1, server1Port80InternalIPAddress, PORT_443))
                        .close());
    }

    @Test(dependsOnMethods = "checkNewRouteCreationWithoutSpecifiedIPAddress", priority = 2)
    @TestCase(value = {"2232_8"})
    public void checkAddingRouteWithoutResolvedIPToExistingRoute() {
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .addRoute()
                .setServerName(SERVER_NAME_2)
                .setValue(PORT, PORT_80)
                .checkFieldWarning(PORT, format("Duplicate port %s", PORT_80))
                .clear(PORT)
                .setValue(PORT, PORT_443)
                .selectValue(PROTOCOL, PROTOCOL_UDP)
                .addRoute()
                .click(SAVE)
                .checkCreationScheduled(SERVER_NAME_2, PORT_443)
                .waitRouteRecordCreationScheduled(SERVER_NAME_2, PORT_443)
                .checkActiveRouteRecord(StringUtils.EMPTY, SERVER_NAME_2, "", PORT_443);
        runsMenu()
                .showLog(getRunId())
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", SERVER_NAME_2, PORT_443)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, server2Port80InternalIPAddress),
                                format(CONNECTED_FORMAT, SERVER_NAME_2, server2Port80InternalIPAddress, PORT_443))
                        .close());
    }

    @Test(dependsOnMethods = "checkNewRouteCreationWithSpecifiedIPAddress", priority = 1)
    @TestCase(value = {"2232_9"})
    public void checkAddingRouteWithSeveralPorts() {
        final SystemManagementAO systemManagementAO = navigationMenu()
                .settings()
                .switchToSystemManagement();
        final NATGatewayAO natGatewayAO = systemManagementAO
                .switchToNATGateway()
                .addRoute()
                .setServerName(SERVER_NAME_3)
                .setValue(PORT, PORT_80)
                .click(ADD_PORT)
                .ensure(ADD, disabled)
                .addMorePorts(PORT_443, 2)
                .ensure(ADD, enabled)
                .addRoute()
                .expandGroup(SERVER_NAME_3, PORT_80)
                .checkRouteRecord(SERVER_NAME_3, SERVER_NAME_3, PORT_80)
                .checkRouteRecord(SERVER_NAME_3, SERVER_NAME_3, PORT_443)
                .sleep(1, SECONDS);
        systemManagementAO
                .click(SYSTEM_LOGS_TAB)
                .also(() -> new ConfirmationPopupAO<>(this)
                        .ensureTitleIs("You have unsaved changes. Continue?")
                        .cancel())
                .sleep(2, SECONDS);
        final String internalIPPort80 = natGatewayAO
                .click(SAVE)
                .checkCreationScheduled(SERVER_NAME_3, PORT_80)
                .checkCreationScheduled(SERVER_NAME_3, PORT_443)
                .waitRouteRecordCreationScheduled(SERVER_NAME_3, PORT_80)
                .waitRouteRecordCreationScheduled(SERVER_NAME_3, PORT_443)
                .getInternalIP(SERVER_NAME_3, PORT_80);
        final String internalIPPort443 = natGatewayAO.getInternalIP(SERVER_NAME_3, PORT_443);
        final String internalPortPort80 = natGatewayAO.getInternalPort(SERVER_NAME_3, PORT_80);
        final String internalPortPort443 = natGatewayAO.getInternalPort(SERVER_NAME_3, PORT_443);
        assertEquals(internalIPPort80, internalIPPort443);
        assertNotEquals(internalPortPort80, internalPortPort443);
        runsMenu()
                .showLog(getRunId())
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", SERVER_NAME_3, PORT_80)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, internalIPPort80),
                                format(CONNECTED_FORMAT, SERVER_NAME_3, internalIPPort80, PORT_80))
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", SERVER_NAME_3, PORT_443)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, internalIPPort80),
                                format(CONNECTED_FORMAT, SERVER_NAME_3, internalIPPort80, PORT_443))
                        .close());
    }

    @Test(dependsOnMethods = "checkNewRouteCreationWithSpecifiedIPAddress", priority = 1)
    @TestCase(value = {"2232_10"})
    public void checkAddingSeveralRoutesWithSameServerNameButDiffPorts() {
        final NATGatewayAO natGatewayAO = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .sleep(1, SECONDS)
                .addRoute()
                .setServerName(SERVER_NAME_4)
                .click(SPECIFY_IP)
                .setValue(PORT, PORT_80)
                .setValue(COMMENT, COMMENT_1)
                .addRoute()
                .sleep(1, SECONDS)
                .addRoute()
                .setServerName(SERVER_NAME_4)
                .click(SPECIFY_IP)
                .sleep(1, SECONDS)
                .setValue(PORT, PORT_443)
                .setValue(COMMENT, COMMENT_2)
                .addRoute()
                .sleep(2, SECONDS)
                .click(SAVE)
                .expandGroup(SERVER_NAME_4, PORT_80)
                .checkCreationScheduled(SERVER_NAME_4, PORT_80)
                .checkCreationScheduled(SERVER_NAME_4, PORT_443)
                .waitRouteRecordCreationScheduled(SERVER_NAME_4, PORT_80)
                .waitRouteRecordCreationScheduled(SERVER_NAME_4, PORT_443);
        final String internalIPPort80 = natGatewayAO.getInternalIP(SERVER_NAME_4, PORT_80);
        final String internalIPPort443 = natGatewayAO.getInternalIP(SERVER_NAME_4, PORT_443);
        final String internalPortPort80 = natGatewayAO.getInternalPort(SERVER_NAME_4, PORT_80);
        final String internalPortPort443 = natGatewayAO.getInternalPort(SERVER_NAME_4, PORT_443);
        final String commentPort80 = natGatewayAO.getComment(SERVER_NAME_4, PORT_80);
        final String commentPort443 = natGatewayAO.getComment(SERVER_NAME_4, PORT_443);
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
                        .execute(format(COMMAND_2, format("%s:%s", SERVER_NAME_4, PORT_80)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, internalIPPort80),
                                format(CONNECTED_FORMAT, SERVER_NAME_4, internalIPPort80, PORT_80))
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", SERVER_NAME_4, PORT_443)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, internalIPPort80),
                                format(CONNECTED_FORMAT, SERVER_NAME_4, internalIPPort80, PORT_443))
                        .close());
    }

    @Test(dependsOnMethods = "checkNewRouteCreationWithSpecifiedIPAddress", priority = 1)
    @TestCase(value = {"2444_1"})
    public void checkAddingRouteWithSeveralPortsDifferentProtocols() {
        final SystemManagementAO systemManagementAO = navigationMenu()
                .settings()
                .switchToSystemManagement();
        final NATGatewayAO natGatewayAO = systemManagementAO
                .switchToNATGateway()
                .addRoute()
                .setServerName(SERVER_NAME_5)
                .setValue(PORT, PORT_80)
                .selectValue(PROTOCOL, PROTOCOL_UDP)
                .click(ADD_PORT)
                .ensure(ADD, disabled)
                .addMorePorts(PORT_443, 2)
                .ensure(ADD, enabled)
                .addRoute()
                .checkRouteRecord(SERVER_NAME_5, SERVER_NAME_5, PORT_80)
                .checkRouteRecord(SERVER_NAME_5, SERVER_NAME_5, PORT_443)
                .sleep(1, SECONDS);
        final String internalIPPort80 = natGatewayAO
                .click(SAVE)
                .checkCreationScheduled(SERVER_NAME_5, PORT_80)
                .checkCreationScheduled(SERVER_NAME_5, PORT_443)
                .waitRouteRecordCreationScheduled(SERVER_NAME_5, PORT_80)
                .waitRouteRecordCreationScheduled(SERVER_NAME_5, PORT_443)
                .getInternalIP(SERVER_NAME_5, PORT_80);
        final String internalIPPort443 = natGatewayAO.getInternalIP(SERVER_NAME_5, PORT_443);
        final String internalPortPort80 = natGatewayAO.getInternalPort(SERVER_NAME_5, PORT_80);
        final String internalPortPort443 = natGatewayAO.getInternalPort(SERVER_NAME_5, PORT_443);
        assertEquals(internalIPPort80, internalIPPort443);
        assertNotEquals(internalPortPort80, internalPortPort443);
        runsMenu()
                .showLog(getRunId())
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", SERVER_NAME_5, PORT_80)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, internalIPPort80),
                                format(CONNECTED_FORMAT, SERVER_NAME_5, internalIPPort80, PORT_80))
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, format("%s:%s", SERVER_NAME_5, PORT_443)))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format(TRYING_FORMAT, internalIPPort80),
                                format(CONNECTED_FORMAT, SERVER_NAME_5, internalIPPort80, PORT_443))
                        .close());
    }

    private void deleteRoute(final String serverName, final String port) {
        if (StringUtils.isBlank(serverName)) {
            return;
        }
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .expandGroup(serverName, port)
                .deleteRoute(serverName, port)
                .sleep(1, SECONDS)
                .click(SAVE);
    }
}
