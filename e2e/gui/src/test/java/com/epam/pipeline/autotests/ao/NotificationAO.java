/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import java.util.Map;

import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.codeborne.selenide.Condition.*;

public class NotificationAO implements AccessObject<NotificationAO> {

    private SelenideElement context;
    private final Map<Primitive, SelenideElement> elements;

    public NotificationAO(String title) {
        this.context = $(byXpath(String.format("//*[contains(@class, 'system-notification__container') and contains(., '%s')]", title))).shouldBe(visible);
        this.elements = initialiseElements(
                entry(SEVERITY_ICON, context().find(byClassName("anticon"))),
                entry(TITLE, context().find(byClassName("system-notification__title"))),
                entry(BODY, context().find(byClassName("system-notification__body"))),
                entry(CLOSE, context().find(byId("notification-close-button"))),
                entry(DATE, context().find(byClassName("system-notification__date")))
        );
    }

    @Override
    public SelenideElement context() {
        return context;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public NotificationAO ensureTitleIs(String title) {
        ensure(TITLE, text(title));
        return this;
    }

    public NotificationAO ensureBodyIs(String bodyText) {
        ensure(BODY, text(bodyText));
        return this;
    }

    public NotificationAO ensureSeverityIs(String severity) {
        ensure(SEVERITY_ICON, cssClass(String.format("system-notification__%s", severity.toLowerCase())));
        return this;
    }

    public void close() {
        click(CLOSE);
    }
}
