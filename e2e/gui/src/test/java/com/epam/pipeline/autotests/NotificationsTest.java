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
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.NotificationAO;
import com.epam.pipeline.autotests.ao.Primitive;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static com.epam.pipeline.autotests.ao.Primitive.ADD;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.REFRESH;

import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.Condition.*;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static java.lang.String.format;

public class NotificationsTest extends AbstractBfxPipelineTest implements Authorization {

    private final String currentDate = LocalDate.now().toString();
    private final String infoNotification = format("info_notification-%s", currentDate);
    private final String infoNotificationBodyText = "info_notification_body_text";
    private final String infoEditedTitle = format("info_edited-%s", currentDate);
    private final String infoEditedBodyText = "info_edited_body_text";
    private final String warningNotification = format("warning_notification-%s", currentDate);
    private final String warningNotificationBodyText = "warning_notification_body_text";
    private final String warningActiveNotification = format("warning_active-%s", currentDate);
    private final String warningActiveNotificationBodyText = "warning_active_body_text";
    private final String criticalNotification = format("critical_notification-%s", currentDate);
    private final String criticalNotificationBodyText = "critical_notification_body_text";
    private final String deletionMessageFormat = "Are you sure you want to delete notification '%s'?";
    private final List<String> testNotifications = Arrays.asList(infoNotification, infoEditedTitle,
            warningNotification, warningActiveNotification, criticalNotification);

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .deleteTestEntries(testNotifications);
    }

    @Test(dependsOnMethods = "validateRoleModelForNotifications")
    @TestCase(value = {"EPMCMBIBPC-1205"})
    public void validateSystemEventsMenu() {
        loginAs(admin)
                .settings()
                .switchToSystemEvents()
                .ensureTableHasNoDateText()
                .ensureVisible(REFRESH, ADD);
    }

    @Test(dependsOnMethods = {"validateSystemEventsMenu"})
    @TestCase(value = {"EPMCMBIBPC-1206"})
    public void validateCreateInactiveInfoNotification() {
        createAndValidateNotificationEntry(INFO, infoNotification, infoNotificationBodyText);
    }

    @Test(dependsOnMethods = {"validateSystemEventsMenu"})
    @TestCase(value = {"EPMCMBIBPC-1224"})
    public void validateCreateActiveWarningNotification() {
        Primitive severity = WARNING;

        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .add()
                .setTitle(warningActiveNotification)
                .setBody(warningActiveNotificationBodyText)
                .clickCombobox()
                .selectSeverity(severity)
                .setActive()
                .create()
                .searchForTableEntry(warningActiveNotification)
                .ensureSeverityIconIs(severity.name());
        if(!impersonateMode()) {
            refresh();
            validateActiveNotification(warningActiveNotification, warningActiveNotificationBodyText, severity);
            closeNotification(warningActiveNotification);
        }
    }

    @Test(dependsOnMethods = {"validateCreateInactiveInfoNotification"})
    @TestCase(value = {"EPMCMBIBPC-1210"})
    public void validateExpandNotification() {
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .searchForTableEntry(infoNotification)
                .expand()
                .ensureBodyHasText(infoNotificationBodyText)
                .narrow()
                .ensureNoBodyText(infoNotificationBodyText)
                .close();
    }

    @Test(dependsOnMethods = {"validateCreateInactiveInfoNotification"})
    @TestCase(value = {"EPMCMBIBPC-1209"})
    public void validateActivateNotification() {
        changeStateOf(infoNotification);

        refresh();
        new NotificationAO(infoNotification)
                .ensureSeverityIs("Info")
                .ensureTitleIs(infoNotification)
                .ensureBodyIs(infoNotificationBodyText)
                .ensureVisible(CLOSE, DATE);
        closeNotification(infoNotification);
        changeStateOf(infoNotification);
    }

    @Test(dependsOnMethods = {"validateSystemEventsMenu"})
    @TestCase(value = {"EPMCMBIBPC-1212"})
    public void validateCreateInactiveNotificationsWithDifferentSeverity() {
        createAndValidateNotificationEntry(WARNING, warningNotification, warningNotificationBodyText);
        createAndValidateNotificationEntry(CRITICAL, criticalNotification, criticalNotificationBodyText);
    }

    @Test(dependsOnMethods = {"validateCreateInactiveNotificationsWithDifferentSeverity"})
    @TestCase(value = {"EPMCMBIBPC-1213"})
    public void validateDisplaySeveralActiveNotifications() {
        changeStateOf(infoNotification);
        changeStateOf(warningNotification);
        changeStateOf(criticalNotification);

        refresh();
        validateActiveNotification(infoNotification, infoNotificationBodyText, INFO);
        validateActiveNotification(warningNotification, warningNotificationBodyText, WARNING);
        validateActiveNotification(criticalNotification, criticalNotificationBodyText, CRITICAL);

        closeNotification(infoNotification);
        closeNotification(warningNotification);
        closeNotification(criticalNotification);
    }

    @Test(dependsOnMethods = {"validateDisplaySeveralActiveNotifications"})
    @TestCase(value = {"EPMCMBIBPC-1219"})
    public void validateEditActiveNotification() {
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .searchForTableEntry(infoNotification)
                .edit()
                .titleTo(infoEditedTitle)
                .bodyTo(infoEditedBodyText)
                .save()
                .searchForTableEntry(infoEditedTitle)
                .ensureVisible(EXPAND, SEVERITY_ICON, TITLE, DATE, STATE, ACTIVE_LABEL, EDIT, DELETE)
                .click(EXPAND)
                .ensureBodyHasText(infoEditedBodyText);
        refresh();
        validateActiveNotification(infoEditedTitle, infoEditedBodyText, INFO);
        closeNotification(infoEditedTitle);
    }

    @Test(dependsOnMethods = {"validateDisplaySeveralActiveNotifications"})
    @TestCase(value = {"EPMCMBIBPC-1220"})
    public void validateDeleteActiveNotification() {
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .searchForTableEntry(warningNotification)
                .delete()
                .ensureTitleIs(format(deletionMessageFormat, warningNotification))
                .ok();

        refresh();
        ensureNotificationIsAbsent(warningNotification);
        closeNotification(infoNotification);
        closeNotification(criticalNotification);
    }

    @Test(dependsOnMethods = {"validateDeleteActiveNotification"}, enabled = false)
    @TestCase(value = {"EPMCMBIBPC-1221"})
    public void validateCloseActiveNotification() {
        closeNotification(criticalNotification);
        refresh();
        validateActiveNotification(criticalNotification, criticalNotificationBodyText, CRITICAL);
    }

    @Test(dependsOnMethods = {"validateDeleteActiveNotification"})
    @TestCase(value = {"EPMCMBIBPC-1217"})
    public void validateSeveralInactiveNotifications() {
        if (!impersonateMode()) {
            changeStateOf(infoEditedTitle);
            changeStateOf(warningActiveNotification);
            changeStateOf(criticalNotification);
        }

        refresh();
        ensureNotificationIsAbsent(infoEditedTitle);
        ensureNotificationIsAbsent(warningActiveNotification);
        ensureNotificationIsAbsent(criticalNotification);
    }

    @Test(dependsOnMethods = {"validateSeveralInactiveNotifications"})
    @TestCase(value = {"EPMCMBIBPC-1222"})
    public void validateEditInactiveNotification() {
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .searchForTableEntry(infoEditedTitle)
                .edit()
                .titleTo(infoNotification)
                .bodyTo(infoNotificationBodyText)
                .save()
                .ensureTableHasText(infoNotification)
                .searchForTableEntry(infoNotification)
                .expand()
                .ensureBodyHasText(infoNotificationBodyText)
                .close();
    }

    @Test(dependsOnMethods = {"validateSeveralInactiveNotifications"})
    @TestCase(value = {"EPMCMBIBPC-1223"})
    public void validateDeleteInactiveNotification() {
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .searchForTableEntry(criticalNotification)
                .delete()
                .ensureTitleIs(format(deletionMessageFormat, criticalNotification))
                .ok()
                .ensureTableHasNoText(criticalNotification);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-1229"})
    public void validateRoleModelForNotifications() {
        logout();
        loginAs(user)
                .settings()
                .ensure(SYSTEM_EVENTS_TAB, not(visible));
        logout();
    }

    private void createAndValidateNotificationEntry(Primitive severity, String titleText, String bodyText) {
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .add()
                .ensureVisible(TITLE, TITLE_FIELD,
                        BODY, BODY_FIELD,
                        SEVERITY, SEVERITY_COMBOBOX,
                        STATE, ACTIVE_LABEL,
                        CREATE, CANCEL)
                .ensureFieldMarkedAsRequired(TITLE)
                .ensureSeverityIs(capitalCase(INFO))
                .setTitle(titleText)
                .setBody(bodyText)
                .clickCombobox()
                .ensureVisible(INFO, WARNING, CRITICAL)
                .selectSeverity(severity)
                .create()
                .searchForTableEntry(titleText)
                .ensureVisible(EXPAND, SEVERITY_ICON, TITLE, DATE, STATE, ACTIVE_LABEL, EDIT, DELETE)
                .ensureSeverityIconIs(severity.name().toLowerCase())
                .close();
    }

    private void validateActiveNotification(String titleText, String bodyText, Primitive severity) {
        new NotificationAO(titleText)
                .ensureSeverityIs(severity.name())
                .ensureTitleIs(titleText)
                .ensureBodyIs(bodyText);
    }

    private void ensureNotificationIsAbsent(String title) {
        $(byXpath(format("//*[contains(@class, 'system-notification__container') and contains(., '%s')]", title)))
                .shouldNotBe(visible);
    }

    private void changeStateOf(String title) {
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .searchForTableEntry(title)
                .changeState()
                .close();
    }

    private String capitalCase(Primitive severity) {
        return severity.name().charAt(0) + severity.name().substring(1).toLowerCase();
    }

    private void closeNotification(final String notificationName) {
        new NotificationAO(notificationName).close();
    }
}
