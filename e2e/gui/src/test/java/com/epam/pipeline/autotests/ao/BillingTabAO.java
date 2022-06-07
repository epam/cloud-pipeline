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
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import org.openqa.selenium.By;

import java.util.Arrays;
import java.util.Map;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.menuitem;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertTrue;


public class BillingTabAO implements AccessObject<BillingTabAO> {
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(QUOTAS, $(byXpath("//div[@title='Quotas']"))),
            entry(COMPUTE_INSTANCES, $(byId("quotas$Menu")).$(byXpath("//li[.='Compute instances']"))),
            entry(STORAGES, $(byId("quotas$Menu")).$(byXpath("//li[.='Storages']")))
    );

    public QuotasSection getQuotasSection(BillingQuotaType section) {
        sleep(1, SECONDS);
        SelenideElement entry = context().$$(By.className("uotas__section-container"))
                .findBy(text(section.type)).shouldBe(visible);
        return new QuotasSection(this, entry);
    }

    public BillingTabAO checkQuotasSections(BillingQuotaType ... sections) {
        Arrays.stream(sections).forEach(section ->
                $$(By.className("uotas__section-container"))
                        .findBy(text(section.type)).exists());
        return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    @Override
    public SelenideElement context() {
        return $(PipelineSelectors.visible(byId("root-content")));
    }

    public class QuotasSection implements AccessObject<QuotasSection> {
        private final BillingTabAO parentAO;
        private SelenideElement entry;
        private final Map<Primitive, SelenideElement> elements;

        public QuotasSection(BillingTabAO parentAO, SelenideElement entry) {
            this.parentAO = parentAO;
            this.entry = entry;
            this.elements = initialiseElements(
                    entry(ADD_QUOTA, entry.$(button("Add quota")))
            );
        }

        public QuotaPopUp addQuota() {
            click(ADD_QUOTA);
            return new QuotaPopUp(this);
        }

        public QuotaEntry getQuotaEntry(String entity, String period) {
            return new QuotaEntry(this, quotaEntry(entity, period));
        }

        public QuotaPopUp openQuotaEntry(String entity, String period) {
            quotaEntry(entity, period).click();
            return new QuotaPopUp(this);
        }

        public boolean isQuotaExist(String entity, BillingQuotaPeriod period) {
            return entry.$$(By.className("uota-description__container"))
                    .filter(text(entity))
                    .filter(text(period.period))
                    .first().exists();
        }

        public QuotasSection removeQuotaWithPeriodIfExist(String entity, BillingQuotaPeriod period) {
            sleep(1, SECONDS);
            if(isQuotaExist(entity, period)) {
                getQuotaEntry(entity, period.period).removeQuota();
            }
            return this;
        }

        private SelenideElement quotaEntry(String entity, String quota) {
            sleep(1, SECONDS);
            return entry.$$(By.className("uota-description__container"))
                    .filter(text(entity))
                    .filter(text(quota))
                    .first().shouldBe(visible);
        }

        private void removeConfirmation() {
            new ConfirmationPopupAO<>(this)
                    .ensureTitleIs("Are you sure you want to remove quota?")
                    .sleep(1, SECONDS)
                    .click(button(OK.name()));
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        public class QuotaPopUp extends PopupAO<QuotaPopUp, QuotasSection> implements AccessObject<QuotaPopUp> {
            public final Map<Primitive, SelenideElement> elements = initialiseElements(
                    entry(QUOTA, context().find(By.className("uotas__quota-input")).find(By.xpath(".//input"))),
                    entry(PERIOD, context().find(byText("$"))
                            .find(By.xpath("following-sibling::div//div[@role='combobox']"))),
                    entry(THRESHOLD, context().find(byAttribute("placeholder","Threshold"))),
                    entry(ACTIONS, context().find(withText("%"))
                            .find(By.xpath("following-sibling::div//div[@role='combobox']"))),
                    entry(RECIPIENTS, context().find(byText("Recipients:"))
                            .find(By.xpath("following-sibling::div//div[@role='combobox']"))),
                    entry(BILLING_CENTER, context().find(byText("Billing center"))
                            .find(By.xpath("following-sibling::div//div[@role='combobox']"))),
                    entry(USER_NAME, context().find(byText("User"))
                            .find(By.xpath("following-sibling::div//div[@role='combobox']"))),
                    entry(ADD_ACTION, context().$(byClassName("uotas__add")).$(byXpath(".//button"))),
                    entry(SAVE, context().find(button("SAVE"))),
                    entry(CANCEL, context().find(button("CANCEL"))),
                    entry(REMOVE, context().find(button("REMOVE"))),
                    entry(CLOSE, context().find(button("CLOSE"))),
                    entry(TITLE, context().find(byClassName("ant-modal-title")))
            );

            public QuotaPopUp(QuotasSection parentAO) {
                super(parentAO);
            }

            public QuotaPopUp ensureActionsList(String... actions) {
                click(ACTIONS);
                sleep(1, SECONDS);
                ElementsCollection list = $$(byXpath(
                        ".//li[@role = 'menuitem' and contains(@class, 'ant-select-dropdown-menu-item')]"));
                Arrays.stream(actions).forEach(action ->
                        assertTrue(list.texts().contains(action), format("Action '%s' isn't contained in list", action)));
                return this;
            }

            public QuotaPopUp addRecipient(final String recipient) {
                click(RECIPIENTS);
                actions().sendKeys(recipient).perform();
                enter();
                click(byText("Recipients:"));
                return this;
            }

            public QuotaPopUp addQuotaObject(Primitive object, final String name) {
                click(object);
                actions().sendKeys(name).perform();
                enter();
                click(byText("Quota:"));
                return this;
            }

            public QuotaPopUp setAction(final String billingThreshold, final String ... actions) {
                setValue(THRESHOLD, billingThreshold);
                Arrays.stream(actions).forEach(act -> {
                        selectValue(ACTIONS, act);
                        click(byText("Actions:"));
                });
                return this;
            }

            public QuotaPopUp setAdditionalAction(final int actionNumber,
                                                  final String billingThreshold, final String ... actions) {
                SelenideElement action = context().$$(byClassName("uotas__threshold-container"))
                                .get(actionNumber);
                SelenideElement combobox = action
                        .find(By.xpath(".//div[@role='combobox']"));
                setValue(action.find(byAttribute("placeholder","Threshold")), billingThreshold);
                Arrays.stream(actions).forEach(act -> {
                    selectValue(combobox, menuitem(act));
                    click(byText("Actions:"));
                });
                return this;


            }

            public QuotaPopUp ensureComboboxFieldDisabled(Primitive ... elements) {
                Arrays.stream(elements).forEach(el ->
                        assertTrue(get(el).parent().has(cssClass("ant-select-disabled"))));
                return this;
            }

            public QuotasSection close() {
                return click(CLOSE).parent();
            }

            public QuotasSection removeQuota() {
                click(REMOVE);
                removeConfirmation();
                return parent();
            }

            @Override
            public Map<Primitive, SelenideElement> elements() {
                return elements;
            }

            @Override
            public SelenideElement context() {
                return $(PipelineSelectors.visible(byClassName("ant-modal-content")));
            }

            @Override
            public QuotasSection ok() {
                return click(SAVE).parent();
            }

            @Override
            public QuotasSection cancel() {
                context().find(button("CANCEL")).shouldBe(visible).click();
                return this.parent();
            }

            public QuotaPopUp errorMessageShouldAppear(BillingQuotaType type, BillingQuotaPeriod period) {
                screenshot("error_message");
                $(byClassName("uotas__message")).should(appear)
                        .should(text(format("%squota %salready exists", type.type,
                                period.period.replace(" ", ""))));
                return this;
            }
        }

        public class QuotaEntry implements AccessObject<QuotaEntry> {
            private final QuotasSection parentAO;
            private SelenideElement entry;
            private final Map<Primitive, SelenideElement> elements;

            public QuotaEntry(QuotasSection parentAO, SelenideElement entry) {
                this.parentAO = parentAO;
                this.entry = entry;
                this.elements = initialiseElements(
                        entry(DELETE_ICON, entry.$(byClassName("anticon-close")).parent()),
                        entry(ACTIONS, entry.$(byClassName("uota-description__actions-container"))
                                ),
                        entry(STATUS, entry.find("circle"))
                );
            }

            public QuotaEntry checkEntryActions(String ... actions) {
                Arrays.stream(actions).forEach(act -> ensure(ACTIONS, text(act)));
                return this;
            }

            public QuotasSection removeQuota() {
                click(DELETE_ICON);
                removeConfirmation();
                return parentAO;
            }

            public QuotaEntry checkQuotaStatus(BillingQuotaStatus status) {
                return ensure(STATUS, cssClass(status.status));
            }

            public QuotaEntry checkQuotaWarning() {
                assertTrue(get(ACTIONS).$$(By.xpath(".//span")).first().has(cssClass("cp-warning")));
                return this;
            }

            @Override
            public Map<Primitive, SelenideElement> elements() {
                return elements;
            }
        }
    }

    public enum BillingQuotaType {
        OVERALL("Overall"),
        BILLING_CENTERS("Billing centers"),
        GROUPS("Groups"),
        USERS("Users");

        public final String type;

        BillingQuotaType(String type) {
            this.type = type;
        }
    }

    public enum BillingQuotaPeriod {
        PER_MONTH("per month"),
        PER_YEAR("per year"),
        PER_QUARTER("per quarter");

        public final String period;

        BillingQuotaPeriod(String period) {
            this.period = period;
        }
    }

    public enum BillingQuotaStatus {
        GREEN("cp-quota-status-green"),
        YELLOW("cp-quota-status-yellow"),
        RED("cp-quota-status-red");

        public final String status;

        BillingQuotaStatus(String status) {
            this.status = status;
        }
    }
}
