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
package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.ex.ElementNotFound;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.matchText;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.NAT_GATEWAY_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.SYSTEM_LOGS_TAB;
import static com.epam.pipeline.autotests.utils.C.ADMIN_TOKEN_IS_SERVICE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.combobox;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.inputOf;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertTrue;

public class SystemManagementAO extends SettingsPageAO {

    public SystemManagementAO(PipelinesLibraryAO pipelinesLibraryAO) {
        super(pipelinesLibraryAO);
    }

    public final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(SYSTEM_LOGS_TAB, $(byClassName("section-logs")).find(byText("LOGS"))),
            entry(NAT_GATEWAY_TAB, $(byClassName("section-nat")).find(byText("NAT GATEWAY")))
    );

    public SystemLogsAO switchToSystemLogs() {
        click(SYSTEM_LOGS_TAB);
        if ("false".equalsIgnoreCase(ADMIN_TOKEN_IS_SERVICE)) {
            return new SystemLogsAO();
        }
        return new SystemLogsAO().setIncludeServiceAccountEventsOption();
    }

    public NATGatewayAO switchToNATGateway() {
        click(NAT_GATEWAY_TAB);
        return new NATGatewayAO().waitForRouteData();
    }

    public class SystemLogsAO implements AccessObject<SystemLogsAO> {

        private ElementsCollection containerLogs() {
            return $(byClassName("ystem-logs__container"))
                    .$(byClassName("ant-table-tbody"))
                    .should(exist)
                    .findAll(byClassName("ant-table-row"));
        }

        public SelenideElement getInfoRow(final String message, final String user, final String type) {
            int attempt = 0;
            int maxAttempts = 10;
            while (containerLogs().stream().filter(r ->
                    r.has(matchText(message)) && r.has(text(type))).count() == 0
                    && attempt < maxAttempts) {
                sleep(3, SECONDS);
                refresh();
                filterBy(user);
            }
        return containerLogs().stream()
                .filter(r -> r.has(matchText(message)) && r.has(text(type)))
                .findFirst()
                .orElseThrow(() -> {
                    String screenshotName = format("SystemLogsFor%s_%s", user, Utils.randomSuffix());
                    screenshot(screenshotName);
                    return new NoSuchElementException(format("Supposed log info '%s' is not found.",
                            format("%s message for %s with %s type. Screenshot: %s", message, user, type,
                                    screenshotName)));
                });
        }

        public SystemLogsAO filterByUser(final String user) {
            selectValue(combobox("User"), user);
            return this;
        }

        public SystemLogsAO filterByMessage(final String message) {
            setValue(inputOf(filterBy("Message")), message);
            pressEnter();
            return this;
        }

        public SystemLogsAO filterByService(final String service) {
            selectValue(combobox("Service"), service);
            click(byText("Service"));
            return this;
        }

        public SystemLogsAO clearUserFilters() {
            clearFiltersBy("User");
            return this;
        }

        public SystemLogsAO pressEnter() {
            actions().sendKeys(Keys.ENTER).perform();
            return this;
        }

        public void validateTimeOrder(final SelenideElement info1, final SelenideElement info2) {
            sleep(5, SECONDS);
            LocalDateTime td1 = Utils.validateDateTimeString(info1.findAll("td").get(0).getText());
            LocalDateTime td2 = Utils.validateDateTimeString(info2.findAll("td").get(0).getText());
            screenshot(format("SystemLogsValidateTimeOrder-%s", Utils.randomSuffix()));
            assertTrue(td1.isAfter(td2) || td1.isEqual(td2));
        }

        public SystemLogsAO validateRow(final String message, final String user, final String type) {
            final SelenideElement infoRow = getInfoRow(message, user, type);
            infoRow.should(exist);
            infoRow.findAll("td").get(3).shouldHave(text(user));
            return this;
        }

        public String getUserId(final SelenideElement element) {
            final String message = getMessage(element);
            final Pattern pattern = Pattern.compile("\\d+");
            final Matcher matcher = pattern.matcher(getMessage(element));
            if (!matcher.find()) {
                final String screenName = format("SystemLogsGetUserId_%s", Utils.randomSuffix());
                screenshot(screenName);
                throw new ElementNotFound(format("Could not get user id from message: %s. Screenshot: %s.png", message,
                        screenName), exist);
            }
            return matcher.group();
        }

        private By filterBy(final String name) {
            return byXpath(format("(//*[contains(@class, '%s') and .//*[contains(text(), '%s')]])[last()]",
                    "ilters__filter", name
            ));
        }

        private String getMessage(final SelenideElement element) {
            return element.findAll("td").get(2).getText();
        }

        public void clearFiltersBy(final String name) {
            actions().moveToElement($(combobox(name))).build().perform();
            if ($(filterBy(name)).find(byClassName("ant-select-selection__clear")).isDisplayed()) {
                $(filterBy(name)).find(byClassName("ant-select-selection__clear")).shouldBe(visible).click();
            }
        }

        public SystemLogsAO setIncludeServiceAccountEventsOption() {
            if ($(byId("show-hide-advanced")).shouldBe(enabled).has(text("Show advanced"))) {
                click(byId("show-hide-advanced"));
            }
            if (!$(byXpath(".//span[.='Include Service Account Events']/preceding-sibling::span"))
                    .has(cssClass("ant-checkbox-checked"))) {
                click(byXpath(".//span[.='Include Service Account Events']/preceding-sibling::span"));
            }
            return this;
        }
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
