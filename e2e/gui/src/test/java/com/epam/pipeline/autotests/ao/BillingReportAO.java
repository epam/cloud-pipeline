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

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exactValue;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.webdriver;
import static com.codeborne.selenide.WebDriverConditions.urlContaining;
import static java.lang.String.format;

/**
 * The "Billing" page, its reports (client/src/components/billing/reports).
 */
public class BillingReportAO {

    // A report queries the billing data of its whole period
    private static final Duration REPORT_LOADING_TIMEOUT = Duration.ofMinutes(2);
    private static final DateTimeFormatter RANGE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    // The range button and the date pickers show the dates as moment's "D MMM YYYY" (LocaleProvider en_US)
    private static final DateTimeFormatter RANGE_BUTTON_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final By CUSTOM_PERIOD = byId("set-period-custom");
    // The range button: it has this class once a range is set, which "Custom" does (the range of the period before)
    private static final By RANGE_BUTTON = byClassName("cp-billing-calendar-set-value");
    // The window of the range button: two date pickers and "Apply"
    private static final By RANGE_WINDOW = byXpath(
            "//div[contains(concat(' ', @class, ' '), ' ant-popover ')][.//input[@placeholder = 'From']]");
    private static final By FROM_DATE = byCssSelector(".ant-popover input[placeholder='From']");
    private static final By TO_DATE = byCssSelector(".ant-popover input[placeholder='To']");
    private static final By APPLY = byCssSelector(".ant-popover button.ant-btn-primary");
    // The date input of an open calendar. A calendar is removed once it has closed (destroyPopupOnHide), after an
    // animation: until then the calendar before is still there too.
    private static final By CALENDAR_INPUT = byCssSelector(".ant-calendar-picker-container .ant-calendar-input");
    // The table of a storage report, the only table of the page with a "Storage" column
    private static final By STORAGES_TABLE = byXpath(
            "//div[contains(@class, 'ant-table-wrapper')][.//th[normalize-space(.) = 'Storage']]");
    // The pages of the storages table, the only pagination of the page
    private static final By NEXT_PAGE = byCssSelector("ul.ant-pagination li.ant-pagination-next");
    private static final By ACTIVE_PAGE = byCssSelector("ul.ant-pagination li.ant-pagination-item-active");
    private static final int MAX_PAGES = 1000;

    /**
     * The "Storages" report: object and file storages.
     */
    public BillingReportAO storages() {
        $$(byCssSelector(".cp-billing-menu .ant-menu-submenu-title")).findBy(exactText("Storages"))
                .shouldBe(visible).click();
        webdriver().shouldHave(urlContaining("/billing/reports/storage"));
        return this;
    }

    /**
     * Clicks the "Custom" period button, opens the range button, types the dates from..to (whole days, UTC, as the
     * page reads them) into the "From" and "To" date pickers, and clicks "Apply" (filters/components/range-picker.js).
     * The range button and the page address must then show the range.
     * "From" goes first: the "To" picker refuses a date before "From", and "From" starts at the first day of the month.
     */
    public BillingReportAO customRange(final LocalDate from, final LocalDate to) {
        $(CUSTOM_PERIOD).shouldBe(visible).click();
        $(CUSTOM_PERIOD).shouldHave(cssClass("ant-btn-primary"));
        $(RANGE_BUTTON).shouldBe(visible).click();
        // The window grows from a fifth of its size: its pickers move until the animation has ended
        $(RANGE_WINDOW).shouldBe(visible).shouldNotHave(cssClass("zoom-big-enter"));
        typeDate(FROM_DATE, from);
        typeDate(TO_DATE, to);
        $(FROM_DATE).shouldHave(exactValue(from.format(RANGE_BUTTON_DATE)));
        $$(APPLY).findBy(exactText("Apply")).shouldBe(enabled).click();
        $$(RANGE_BUTTON)
                .findBy(exactText(format("%s to %s", from.format(RANGE_BUTTON_DATE), to.format(RANGE_BUTTON_DATE))))
                .shouldBe(visible);
        webdriver().shouldHave(urlContaining("period=custom"));
        webdriver().shouldHave(urlContaining(from.format(RANGE_DATE)));
        webdriver().shouldHave(urlContaining(to.format(RANGE_DATE)));
        $(CUSTOM_PERIOD).shouldHave(cssClass("ant-btn-primary"));
        return this;
    }

    /**
     * Opens the calendar of a date picker of the range window, types the date into the calendar's own input, and
     * closes the calendar. The picker takes a date as soon as the input holds a valid one ("D MMM YYYY", strict): the
     * input of the picker, which can't be typed into, then shows it. A date the page doesn't allow leaves the picker
     * as it was. Enter and Escape do nothing in that input.
     * The calendar is closed by a click outside it, on "Custom": an open calendar can cover the other picker and
     * "Apply", while "Custom" is above the window. The window ignores a click outside while a calendar is open, and a
     * click on the period that is set already does nothing.
     */
    private void typeDate(final By picker, final LocalDate date) {
        $(picker).shouldBe(visible).click();
        $$(CALENDAR_INPUT).shouldHave(size(1));
        $(CALENDAR_INPUT).shouldBe(visible).sendKeys(Keys.chord(Keys.CONTROL, "a"), date.format(RANGE_BUTTON_DATE));
        $(picker).shouldHave(exactValue(date.format(RANGE_BUTTON_DATE)));
        $(CUSTOM_PERIOD).click();
        $$(CALENDAR_INPUT).shouldHave(size(0));
        $(picker).shouldBe(visible);
    }

    /**
     * The "Storage" column of the storages table, all its pages: the path of an object storage, the name of an NFS
     * storage (StorageBillingDetailsLoader).
     * The table shows 10 storages per page, and the pages appear only when there are more. A page is loaded when it
     * is clicked: the rows of the page before stay, under a spinner, until it has loaded, and the page becomes the
     * active one as the loading starts. So each page is read once it is the active one and the spinner is gone.
     */
    public List<String> storageColumn() {
        final List<String> storages = new ArrayList<>(storageColumnOfThePage());
        for (int page = 2; $(NEXT_PAGE).exists() && !$(NEXT_PAGE).has(cssClass("ant-pagination-disabled")); page++) {
            if (page > MAX_PAGES) {
                throw new IllegalStateException(format("The storages table has more than %d pages", MAX_PAGES));
            }
            $(NEXT_PAGE).click();
            $(ACTIVE_PAGE).shouldHave(attribute("title", String.valueOf(page)), REPORT_LOADING_TIMEOUT);
            storages.addAll(storageColumnOfThePage());
        }
        return storages;
    }

    /**
     * The "Storage" column of the page of the storages table that is shown, once it has loaded. The rows are in a
     * scrolled box, so the cells are read from the page source rather than as visible text.
     */
    private List<String> storageColumnOfThePage() {
        final SelenideElement table = $(STORAGES_TABLE).shouldBe(visible, REPORT_LOADING_TIMEOUT);
        table.$(byClassName("ant-spin-spinning")).shouldNot(exist, REPORT_LOADING_TIMEOUT);
        return Selenide.executeJavaScript(
                "return Array.from(arguments[0].querySelectorAll('tbody tr.ant-table-row'))"
                        + ".map(row => row.cells[0].textContent.trim());",
                table.toWebElement());
    }
}
