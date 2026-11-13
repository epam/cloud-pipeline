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

package com.epam.pipeline.autotests.mixins;

import com.epam.pipeline.autotests.ao.BillingReportAO;
import com.epam.pipeline.autotests.ao.ClusterMenuAO;
import com.epam.pipeline.autotests.ao.NavigationHomeAO;
import com.epam.pipeline.autotests.ao.NavigationMenuAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.ao.ToolsPage;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Utils;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.ex.UIAssertionError;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.Point;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.utils.Conditions.selectedMenuItem;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public interface Navigation {

    Duration NAVIGATION_LOADING_TIMEOUT = Duration.ofSeconds(10);
    Duration NAVIGATION_SETTLED_FOR = Duration.ofSeconds(1);
    Duration STOP_IMPERSONATION_TIMEOUT = Duration.ofSeconds(30);

    default NavigationMenuAO navigationMenu() {
        final By pipelinesPageSelector = byId("navigation-button-pipelines");
        $(pipelinesPageSelector).shouldBe(visible).click();
        $(pipelinesPageSelector).shouldBe(selectedMenuItem);
        return new NavigationMenuAO();
    }

    default NavigationHomeAO home() {
        final By homePageSelector = byId("navigation-button-home");
        $(homePageSelector).shouldBe(visible).click();
        $(homePageSelector).shouldBe(selectedMenuItem);
        return new NavigationHomeAO();
    }

    default PipelinesLibraryAO library() {
        return navigationMenu().library();
    }

    default ToolsPage tools() {
        final By toolsPageSelector = byId("navigation-button-tools");
        $(toolsPageSelector).shouldBe(visible).click();
        $(toolsPageSelector).shouldBe(selectedMenuItem);
        return new ToolsPage();
    }

    default ClusterMenuAO clusterMenu() {
        final By clusterPageSelector = byId("navigation-button-cluster");
        $(clusterPageSelector).shouldBe(visible).click();
        $(clusterPageSelector).shouldBe(selectedMenuItem);
        return new ClusterMenuAO();
    }

    default RunsMenuAO runsMenu() {
        final By runsPageSelector = byId("navigation-button-runs");
        $(runsPageSelector).shouldBe(visible).click();
        $(runsPageSelector).shouldBe(selectedMenuItem);
        return new RunsMenuAO();
    }

    /**
     * The "Billing" page. Its navigation item appears only after the platform preferences have loaded, so this clicks
     * it once it has stopped moving (see settledNavigationItem).
     */
    default BillingReportAO billing() {
        final By billingPageSelector = byId("navigation-button-billing");
        settledNavigationItem(billingPageSelector).click();
        $(billingPageSelector).shouldBe(selectedMenuItem);
        return new BillingReportAO();
    }

    /**
     * Stops the impersonation with one click. The GUI's STOP first invalidates the edge tokens, then calls
     * /user/impersonation/stop, then reloads the page, and that can take seconds. A second STOP that reaches the API
     * after the first one has ended the impersonation gets 401 and drops the login of the session: a second click
     * 1.2 s after the first was enough, and the session went to SSO 2 s later. So this
     * clicks again only if the button is still there after STOP_IMPERSONATION_TIMEOUT and the API says the session
     * is still impersonated. If it isn't, the page is just stale, and it is reloaded.
     */
    default NavigationHomeAO stopImpersonation() {
        final By stopImpersonateSelector = byId("navigation-button-stop-impersonation");
        settledNavigationItem(stopImpersonateSelector).click();
        if (!disappears(stopImpersonateSelector, STOP_IMPERSONATION_TIMEOUT)) {
            if (isImpersonated()) {
                System.out.println("The impersonation is still active after the click on STOP, clicking it again");
                settledNavigationItem(stopImpersonateSelector).click();
            } else {
                Utils.refresh();
            }
        }
        $(stopImpersonateSelector).shouldNotBe(visible, STOP_IMPERSONATION_TIMEOUT);
        return new NavigationHomeAO();
    }

    /**
     * Whether the session of the browser is impersonated now, as the API answers (GET /user/impersonation, the call
     * the GUI makes for the same purpose). The request is sent from the page, with the session cookie.
     */
    default boolean isImpersonated() {
        final String status = Selenide.executeJavaScript(
                "var request = new XMLHttpRequest();"
                        + "request.open('GET', arguments[0], false);"
                        + "request.withCredentials = true;"
                        + "request.send();"
                        + "return request.status + ' ' + request.responseText;",
                C.ROOT_ADDRESS + (C.ROOT_ADDRESS.endsWith("/") ? "" : "/") + "restapi/user/impersonation");
        if (status == null || !status.startsWith("200 ")) {
            throw new IllegalStateException("The impersonation status is not available: " + status);
        }
        return !status.contains("\"impersonated\":null") && status.contains("\"impersonated\":");
    }

    default boolean disappears(final By element, final Duration timeout) {
        try {
            $(element).shouldNotBe(visible, timeout);
            return true;
        } catch (UIAssertionError e) {
            return false;
        }
    }

    /**
     * The navigation item, once it has stopped moving. The navigation shows its "search" and "billing" items only
     * after the platform preferences have loaded (Navigation.js), and the items under them move down a slot. A click
     * on "stop impersonation" right after the dashboard opened once landed on the "search"
     * item that had just taken its place. That opened the search dialog, and the session stayed impersonated
     * (RunToolsInSandboxCheckDataStorageTest cleanup, then the setup of RunsTest as USER2). So this waits for the
     * search item to appear (it doesn't when search is off), then until the item stays in place for a second.
     */
    default SelenideElement settledNavigationItem(final By item) {
        try {
            $(byId("navigation-button-search")).should(exist, NAVIGATION_LOADING_TIMEOUT);
        } catch (UIAssertionError e) {
            // search is off: the navigation has no item that appears late
        }
        final SelenideElement element = $(item).shouldBe(visible);
        final long deadline = System.currentTimeMillis() + NAVIGATION_LOADING_TIMEOUT.toMillis();
        Point position = element.getLocation();
        long unchangedSince = System.currentTimeMillis();
        while (System.currentTimeMillis() - unchangedSince < NAVIGATION_SETTLED_FOR.toMillis()
                && System.currentTimeMillis() < deadline) {
            sleep(200, MILLISECONDS);
            final Point current = element.getLocation();
            if (!current.equals(position)) {
                position = current;
                unchangedSince = System.currentTimeMillis();
            }
        }
        return element;
    }

    default NavigationHomeAO impersonateAs(String user) {
        return navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(user.toUpperCase())
                .edit()
                .impersonate();
    }

    default boolean checkImpersonation() {
        int attempt = 0;
        int maxAttempts = 5;
        while (!$(byId("navigation-button-stop-impersonation")).isDisplayed() && attempt < maxAttempts) {
            sleep(1, SECONDS);
            attempt += 1;
        }
        return $(byId("navigation-button-stop-impersonation")).isDisplayed();
    }
}

