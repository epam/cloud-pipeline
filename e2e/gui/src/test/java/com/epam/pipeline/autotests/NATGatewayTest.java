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

import com.epam.pipeline.autotests.ao.NATGatewayAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.apache.commons.lang3.StringUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
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
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class NATGatewayTest extends AbstractSinglePipelineRunningTest implements Authorization {

    private static final String FIELD_IS_REQUIRED_WARNING = "Field is required";
    private static final String GOOGLE_COM_SERVER_NAME = "google.com";
    private static final String YAHOO_COM_SERVER_NAME = "yahoo.com";
    private static final String PORT_80 = "80";
    private static final String COMMENT_1 = "port1";
    private static final String COMMAND_1 = "unset http_proxy https_proxy";
    private static final String COMMAND_2 = "curl %s -v -ipv4";
    private final String tool = C.TESTING_TOOL_NAME;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private String externalIPAddress;

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (StringUtils.isBlank(externalIPAddress)) {
            return;
        }
        logoutIfNeeded();
        loginAs(admin);
        navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .deleteRoute(externalIPAddress)
                .click(SAVE);
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
                .checkRouteRecord(ipAddress, GOOGLE_COM_SERVER_NAME)
                .ensure(REVERT, visible, enabled)
                .click(REVERT)
                .ensure(SAVE, visible, disabled)
                .ensure(REVERT, visible, disabled)
                .checkNoRouteRecord(ipAddress);
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
        externalIPAddress = natAddRouteAO.getIPAddress();
        final String internalIP = natAddRouteAO
                .addRoute()
                .checkRouteRecord(externalIPAddress, GOOGLE_COM_SERVER_NAME)
                .click(SAVE)
                .ensure(SAVE, visible, disabled)
                .ensure(REVERT, visible, disabled)
                .checkCreationScheduled(externalIPAddress)
                .waitRouteRecord(externalIPAddress)
                .checkActiveRouteRecord(externalIPAddress, GOOGLE_COM_SERVER_NAME, COMMENT_1)
                .getInternalIP(externalIPAddress);
        tools().perform(registry, group, tool, tool -> tool.run(this))
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, GOOGLE_COM_SERVER_NAME))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format("Trying %s...", internalIP),
                                format("Connected to %s (%s) port %s", GOOGLE_COM_SERVER_NAME, internalIP, PORT_80))
                        .close());
    }

    @Test(dependsOnMethods = "checkNewRouteCreationWithSpecifiedIPAddress")
    @TestCase(value = {"2232_3"})
    public void checkNewRouteCreationWithoutSpecifiedIPAddress() {
        final String internalIP = navigationMenu()
                .settings()
                .switchToSystemManagement()
                .switchToNATGateway()
                .sleep(1, SECONDS)
                .addRoute()
                .setServerName(YAHOO_COM_SERVER_NAME)
                .setValue(PORT, PORT_80)
                .setValue(COMMENT, COMMENT_1)
                .addRoute()
                .checkRouteRecordByServerName(YAHOO_COM_SERVER_NAME)
                .click(SAVE)
                .ensure(SAVE, visible, disabled)
                .ensure(REVERT, visible, disabled)
                .checkCreationScheduled(YAHOO_COM_SERVER_NAME)
                .waitRouteRecord(YAHOO_COM_SERVER_NAME)
                .checkActiveRouteRecord(StringUtils.EMPTY, YAHOO_COM_SERVER_NAME, COMMENT_1)
                .getInternalIP(YAHOO_COM_SERVER_NAME);
        runsMenu()
                .showLog(getRunId())
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(COMMAND_1)
                        .sleep(3, SECONDS)
                        .execute(format(COMMAND_2, YAHOO_COM_SERVER_NAME))
                        .sleep(3, SECONDS)
                        .assertOutputContains(format("Trying %s...", internalIP),
                                format("Connected to %s (%s) port %s", YAHOO_COM_SERVER_NAME, internalIP, PORT_80))
                        .close());
    }
}
