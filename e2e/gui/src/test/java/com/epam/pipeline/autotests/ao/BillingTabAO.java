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

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import org.openqa.selenium.By;

import java.util.Map;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byTitle;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.ACTIONS;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_ACTION;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_QUOTA;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.COMPUTE_INSTANCES;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.PERIOD;
import static com.epam.pipeline.autotests.ao.Primitive.QUOTA;
import static com.epam.pipeline.autotests.ao.Primitive.QUOTAS;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.THRESHOLD;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;


public class BillingTabAO implements AccessObject<BillingTabAO> {
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(QUOTAS, $(byXpath("//div[@title='Quotas']"))),
            entry(COMPUTE_INSTANCES, $(byId("quotas$Menu")).$(byXpath("//li[.='Compute instances']")))
    );

    public QuotasSection getQuotasSection(String section) {
        sleep(1, SECONDS);
        SelenideElement entry = context().$$(By.className("uotas__section-container"))
                .findBy(text(section)).shouldBe(visible);
        return new QuotasSection(this, entry);
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


        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        @Override
        public SelenideElement context() {
            return $(byId("root-content"));
        }


        public class QuotaPopUp extends PopupAO<QuotaPopUp, QuotasSection> implements AccessObject<QuotaPopUp> {
            public final Map<Primitive, SelenideElement> elements = initialiseElements(
                    entry(QUOTA, context().find(By.className("uotas__quota-input")).find(By.xpath(".//input"))),
                    entry(PERIOD, context().find(byText("$"))
                            .find(By.xpath("following-sibling::div//div[@role='combobox']"))),
                    entry(THRESHOLD, context().find(byAttribute("placeholder","Threshold"))),
                    entry(ACTIONS, context().find(byText("%"))
                            .find(By.xpath("following-sibling::div//div[@role='combobox']"))),
                    entry(ADD_ACTION, context().$(button(" Add action"))),
                    entry(SAVE, context().find(button("SAVE"))),
                    entry(CANCEL, context().find(button("CANCEL")))
            );

            public QuotaPopUp(QuotasSection parentAO) {
                super(parentAO);
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
        }
    }
}
