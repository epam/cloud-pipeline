/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests.ao;

import static com.codeborne.selenide.Condition.not;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.disappear;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.ADD;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_PORT;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_ROUTE;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CLOSE;
import static com.epam.pipeline.autotests.ao.Primitive.COMMENT;
import static com.epam.pipeline.autotests.ao.Primitive.IP;
import static com.epam.pipeline.autotests.ao.Primitive.PORT;
import static com.epam.pipeline.autotests.ao.Primitive.PROTOCOL;
import static com.epam.pipeline.autotests.ao.Primitive.REFRESH;
import static com.epam.pipeline.autotests.ao.Primitive.RESOLVE;
import static com.epam.pipeline.autotests.ao.Primitive.REVERT;
import static com.epam.pipeline.autotests.ao.Primitive.ROUTE_TABLE;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.SERVER_NAME;
import static com.epam.pipeline.autotests.ao.Primitive.SPECIFY_IP;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.elementWithText;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.By.tagName;
import static org.openqa.selenium.By.xpath;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class NATGatewayAO implements AccessObject<NATGatewayAO> {

    private static final String IPV4_PATTERN =
            "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$";
    private static final String PORT_PATTERN = "^\\d{1,5}$";

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(REFRESH, context().$(button("REFRESH"))),
            entry(ADD_ROUTE, context().$(button("ADD ROUTE"))),
            entry(SAVE, context().$(button("SAVE"))),
            entry(REVERT, context().$(button("REVERT"))),
            entry(ROUTE_TABLE, context().$(byClassName("ant-table-content")))
    );

    public By route(final String ipAddress, final String port) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return context()
                        .findAll(byClassName("ant-table-row"))
                        .filter(not(cssClass("at-gateway-configuration__divider-row")))
                        .stream()
                        .filter(element -> text(ipAddress).apply(element.findAll(".external-column").get(2))
                                && text(port).apply(element.findAll(".external-column").get(3)))
                        .filter(el -> !el.find(By.className("ant-table-row-expand-icon")).exists() ||
                                el.find(By.className("ant-table-row-spaced")).exists())
                        .collect(toList());
            }
        };
    }

    public By routeByName(final String serverName, final String port) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return context()
                        .findAll(byClassName("ant-table-row"))
                        .filter(not(cssClass("at-gateway-configuration__divider-row")))
                        .stream()
                        .filter(element -> text(serverName).apply(element.findAll(".external-column").get(1))
                                && text(port).apply(element.findAll(".external-column").get(3)))
                        .filter(el -> !el.find(By.className("ant-table-row-expand-icon")).exists() ||
                                el.find(By.className("ant-table-row-spaced")).exists())
                        .collect(toList());
            }
        };
    }

    public By groupRouteByName(final String serverName, final String port) {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return context()
                        .findAll(byClassName("ant-table-row")).stream()
                        .filter(not(cssClass("at-gateway-configuration__divider-row")))
                        .filter(element -> text(serverName).apply(element.findAll(".external-column").get(1))
                                && text(port).apply(element.findAll(".external-column").get(3)))
                        .filter(el -> el.find(By.className("ant-table-row-expand-icon")).exists() &&
                                !el.find(By.className("ant-table-row-spaced")).exists())
                        .collect(toList());
            }
        };
    }

    public NATAddRouteAO addRoute() {
        click(ADD_ROUTE);
        return new NATAddRouteAO(this);
    }

    public NATGatewayAO checkRouteRecord(final String ipAddress, final String serverName, final String port) {
        getRouteRecord(ipAddress, port)
                .shouldBe(exist)
                .findAll(".external-column").get(1)
                .shouldHave(text(serverName));
        return this;
    }

    public NATGatewayAO checkRouteRecordByServerName(final String serverName, final String port) {
        $(routeByName(serverName, port))
                .shouldBe(exist)
                .findAll(".external-column").get(2)
                .shouldHave(text(serverName));
        return this;
    }

    public NATGatewayAO checkNoRouteRecord(final String ipAddressOrServerName, final String port) {
        final SelenideElement route = getRouteRecord(ipAddressOrServerName, port);
        route.shouldBe(disappear);
        return this;
    }

    public NATGatewayAO checkCreationScheduled(final String ipAddressOrServerName, final String port) {
        sleep(3, SECONDS);
        expandGroup(ipAddressOrServerName, port);
        final SelenideElement route = getRouteRecord(ipAddressOrServerName, port);
        route
                .findAll(".external-column").get(0)
                .find(tagName("i"))
                .shouldHave(cssClass("anticon-hourglass"));
        return this;
    }

    public NATGatewayAO expandGroup(final String serverName, final String port) {
        sleep(3, SECONDS);
        final SelenideElement routeRecord = $(groupRouteByName(serverName, port));
        if (routeRecord.exists() && routeRecord.find(By.className("ant-table-row-expand-icon"))
                .has(cssClass("ant-table-row-collapsed"))) {
            routeRecord.find(By.className("ant-table-row-expand-icon")).click();
        }
        return this;
    }

    public NATGatewayAO checkActiveRouteRecord(final String ipAddress, final String serverName, final String comment,
                                               final String port) {
        final SelenideElement routeRecord = StringUtils.isBlank(ipAddress)
                ? $(routeByName(serverName, port))
                : $(route(ipAddress, port));
        final ElementsCollection internalConfigElements = routeRecord
                .findAll(".internal-column")
                .shouldHaveSize(3);
        internalConfigElements.get(0).shouldHave(text(
                format("%s-%s", C.NAT_PROXY_SERVICE_PREFIX, serverName.replaceAll("\\.", "-"))));
        internalConfigElements.get(1).shouldHave(matchText(IPV4_PATTERN));
        internalConfigElements.get(2).shouldHave(matchText(PORT_PATTERN));
        routeRecord.find(".at-gateway-configuration__comment-column").shouldHave(text(comment));
        return this;
    }

    public NATGatewayAO checkFailedRouteRecord(final String ipAddress, final String serverName, final String port) {
        final SelenideElement routeRecord = StringUtils.isBlank(ipAddress)
                ? $(routeByName(serverName, port))
                : $(route(ipAddress, port));
        routeRecord
                .findAll(".external-column").get(0)
                .find(tagName("i"))
                .shouldHave(cssClass("anticon-exclamation-circle-o"), cssClass("cp-error"));
        final ElementsCollection internalConfigElements = routeRecord
                .findAll(".internal-column")
                .shouldHaveSize(3);
        internalConfigElements.get(1).shouldHave(text(StringUtils.EMPTY));
        return this;
    }

    public String getInternalIP(final String externalIPAddressOrServerName, final String port) {
        final SelenideElement route = getRouteRecord(externalIPAddressOrServerName, port);
        return route.findAll(".internal-column").get(1).text();
    }

    public String getInternalPort(final String externalIPAddressOrServerName, final String externalPort) {
        final SelenideElement route = getRouteRecord(externalIPAddressOrServerName, externalPort);
        return route.findAll(".internal-column").get(2).text();
    }

    public String getComment(final String externalIPAddressOrServerName, final String externalPort) {
        final SelenideElement route = getRouteRecord(externalIPAddressOrServerName, externalPort);
        return route.find(".nat-column").text();
    }

    private SelenideElement getRouteRecord(final String externalIPAddressOrServerName, final String port) {
        return externalIPAddressOrServerName.matches(IPV4_PATTERN)
                ? $(route(externalIPAddressOrServerName, port))
                : $(routeByName(externalIPAddressOrServerName, port));
    }

    public boolean routeRecordExist(final String externalIPAddressOrServerName, final String port) {
        return getRouteRecord(externalIPAddressOrServerName, port).has(visible);
    }

    public NATGatewayAO deleteRoute(final String externalIPAddressOrServerName, final String port) {
        final SelenideElement route = getRouteRecord(externalIPAddressOrServerName, port);
        route.find(".at-gateway-configuration__actions-column")
                .find(byClassName("ant-btn-danger"))
                .shouldBe(visible)
                .click();
        return this;
    }

    public NATGatewayAO deleteRouteIfExists(final String externalIPAddressOrServerName, final String port) {
        performIf(getRouteRecord(externalIPAddressOrServerName, port).has(visible), route -> {
            deleteRoute(externalIPAddressOrServerName, port);
            sleep(1, SECONDS)
                    .click(SAVE)
                    .sleep(1, SECONDS)
                    .waitRouteRecordTerminationScheduled(externalIPAddressOrServerName, port);
        });
        return this;
    }

    public NATGatewayAO waitRouteRecordCreationScheduled(final String ipAddressOrServerName, final String port) {
        return waitForRouteStatus(ipAddressOrServerName, port, "anticon-hourglass");
    }

    public NATGatewayAO waitRouteRecordTerminationScheduled(final String ipAddressOrServerName, final String port) {
        return waitForRouteStatus(ipAddressOrServerName, port, "anticon-clock-circle-o");
    }

    private NATGatewayAO waitForRouteStatus(final String ipAddressOrServerName,
                                            final String port,
                                            final String status) {
        sleep(1, MINUTES);
        int attempt = 0;
        int maxAttempts = 60;
        expandGroup(ipAddressOrServerName, port);
        final SelenideElement route = getRouteRecord(ipAddressOrServerName, port);
        while (route.findAll(".external-column").get(0).find(tagName("i")).has(cssClass(status))
                && attempt < maxAttempts) {
            expandGroup(ipAddressOrServerName, port);
            click(REFRESH);
            sleep(1, SECONDS);
            attempt++;
        }
        return this;
    }

    public NATGatewayAO waitForRouteData() {
        $(byClassName("ub-settings__content")).waitUntil(visible, C.DEFAULT_TIMEOUT);
        get(ADD_ROUTE).shouldBe(enabled);
        return this;
    }

    public List<String> getGroupExternalPortsList(final String serverName, final String port) {
        final SelenideElement routeRecord = $(groupRouteByName(serverName, port));
        return routeRecord.shouldBe(exist).findAll(".external-column")
                .get(3).findAll(".at-gateway-configuration__port").texts();
    }

    public NATGatewayAO checkGroupPortsList(final String serverName, final String port, List<String> ports) {
        List<String> actualPorts = getGroupExternalPortsList(serverName, port);
        assertEquals(actualPorts, ports, format("Actual list of ports {%s} doesn't correspond expended {%s}",
                actualPorts.toString(), ports.toString()));
        return this;
    }

    @Override
    public SelenideElement context() {
        return $(byClassName("at-gateway-configuration__container"));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static class NATAddRouteAO extends PopupAO<NATAddRouteAO, NATGatewayAO> {

        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(CLOSE, context().find(byClassName("ant-modal-close"))),
                entry(CANCEL, context().find(button("CANCEL"))),
                entry(ADD, context().find(button("ADD"))),
                entry(ADD_PORT, context().find(button("Add port"))),
                entry(SERVER_NAME, context().find(byAttribute("placeholder", "Server name"))),
                entry(PORT, context().find(byText("Port:"))
                        .closest(".dd-route-modal__form-item-container")
                        .find(".dd-route-modal__form-item")),
                entry(COMMENT, context().find(byAttribute("placeholder", "Comment"))),
                entry(SPECIFY_IP, context().find(elementWithText(byClassName("ant-checkbox-wrapper"),
                        "Specify IP address"))),
                entry(RESOLVE, context().find(button("Resolve"))),
                entry(IP, context().find(byAttribute("placeholder", "127.0.0.1"))),
                entry(PROTOCOL, context().find(byText("Protocol:")).find(By.xpath("following::div")))
        );

         public NATAddRouteAO(final NATGatewayAO parentAO) {
             super(parentAO);
         }

         public NATAddRouteAO setServerName(final String serverName) {
             setValue(SERVER_NAME, serverName);
             return this;
         }

         public NATAddRouteAO checkFieldWarning(final Primitive field, final String warning) {
             get(field)
                     .closest(".dd-route-modal__form-item-container")
                     .find(xpath("following-sibling::div"))
                     .shouldHave(text(warning));
             return this;
         }

         public NATAddRouteAO checkIPAddress() {
             assertTrue(get(IP).getValue().matches(IPV4_PATTERN));
             return this;
         }

         public String getIPAddress() {
             return get(IP).getValue();
         }

         public NATGatewayAO addRoute() {
             click(ADD);
             return parent();
         }

         public NATAddRouteAO addMorePorts(final String port, int portNumber) {
             context().findAll(By.className("cp-nat-route-port-control")).get(portNumber - 1)
                     .find(".dd-route-modal__form-item")
                     .shouldBe(visible)
                     .setValue(port);
             return this;
         }

         @Override
         public Map<Primitive, SelenideElement> elements() {
             return elements;
         }

         @Override
         public SelenideElement context() {
             return $(PipelineSelectors.visible(byClassName("ant-modal-content")));
         }
     }
}
