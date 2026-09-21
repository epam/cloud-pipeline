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
package com.epam.pipeline.autotests.ao;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byTitle;
import static com.codeborne.selenide.Selenide.$;
import com.epam.pipeline.autotests.ao.UserManagementAO.GroupsTabAO;
import com.epam.pipeline.autotests.ao.UserManagementAO.GroupsTabAO.EditGroupPopup;
import com.epam.pipeline.autotests.mixins.Authorization;
import static com.epam.pipeline.autotests.utils.C.DEFAULT_TIMEOUT;
import static java.lang.String.format;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;

import java.util.NoSuchElementException;

public class NotificationsPageAO implements AccessObject<NotificationsPageAO> {
    public static final String NEXT_PAGE = "Next Page";

    private ElementsCollection notifications() {
        return $(byClassName("notification-browser__notifications-container"))
                .$(byClassName("ant-spin-container"))
                .should(exist)
                .findAll(byClassName("notification-browser__notification-title"));
    }

    public SelenideElement searchNotification(String notification) {
        sleep(1, SECONDS);
        $(byTitle(NEXT_PAGE)).shouldBe(exist, ofMillis(DEFAULT_TIMEOUT));
        while (notifications().filter(text(notification)).size() == 0
                && $(byTitle(NEXT_PAGE)).has(not(cssClass("ant-pagination-disabled")))) {
            click(byTitle(NEXT_PAGE));
        }
        return notifications().stream().filter(r -> r.has(text(notification)))
                .findFirst()
                .orElseThrow(() -> {
                    return new NoSuchElementException(format("Supposed notification '%s' is not found.", notification));
                });
    }

    public NotificationsPageAO checkNotificationExist(String notification) {
        return ensure(searchNotification(notification), exist);
    }

    public NotificationPopup openNotification(String notification) {
        searchNotification(notification).click();
        return new NotificationPopup(this);
    }

    public class NotificationPopup extends PopupAO<NotificationPopup, NotificationsPageAO> {

        public NotificationPopup(NotificationsPageAO parentAO) {
            super(parentAO);
        }

        @Override
        public SelenideElement context() {
            return $(byClassName("ant-modal-content"));
        }
    }
}
