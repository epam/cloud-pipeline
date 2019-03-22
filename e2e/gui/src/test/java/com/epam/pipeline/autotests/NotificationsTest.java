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
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.NotificationAO;
import com.epam.pipeline.autotests.ao.Primitive;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.epam.pipeline.autotests.ao.Primitive.ADD;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.REFRESH;

import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.Condition.*;
import static com.epam.pipeline.autotests.ao.Primitive.*;

public class NotificationsTest extends AbstractBfxPipelineTest implements Authorization {

    private final String infoNotification = "info_notification";
    private final String infoNotificationBodyText = "info_notification_body_text";
    private final String infoEditedTitle = "info_edited";
    private final String infoEditedBodyText = "info_edited_body_text";
    private final String warningNotification = "warning_notification";
    private final String warningNotificationBodyText = "warning_notification_body_text";
    private final String warningActiveNotification = "warning_active";
    private final String warningActiveNotificationBodyText = "warning_active_body_text";
    private final String criticalNotification = "critical_notification";
    private final String criticalNotificationBodyText = "critical_notification_body_text";
    private final String deletionMessageFormat = "Are you sure you want to delete notification '%s'?";

    @BeforeClass
    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .deleteAllEntries()
                .ok();
    }


    @Test(dependsOnMethods = "validateRoleModelForNotifications")
    @TestCase(value = {"EPMCMBIBPC-1205"})
    public void validateSystemEventsMenu() {
        loginAs(admin)
                .settings()
                .switchToSystemEvents()
                .ensureTableHasText("No data")
                .ensureVisible(REFRESH, ADD, OK)
                .ok();
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
                .ok();

        refresh();
        validateActiveNotification(warningActiveNotification, warningActiveNotificationBodyText, severity);
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
                .close()
                .ok();
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
                .ok();
        refresh();
        validateActiveNotification(infoEditedTitle, infoEditedBodyText, INFO);
    }

    @Test(dependsOnMethods = {"validateDisplaySeveralActiveNotifications"})
    @TestCase(value = {"EPMCMBIBPC-1220"})
    public void validateDeleteActiveNotification() {
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .searchForTableEntry(warningNotification)
                .delete()
                .ensureTitleIs(String.format(deletionMessageFormat, warningNotification))
                .ok()
                .ok();

        refresh();
        ensureNotificationIsAbsent(warningNotification);
    }

    @Test(dependsOnMethods = {"validateDeleteActiveNotification"})
    @TestCase(value = {"EPMCMBIBPC-1221"})
    public void validateCloseActiveNotification() {
        new NotificationAO(criticalNotification)
                .close();
        refresh();
        validateActiveNotification(criticalNotification, criticalNotificationBodyText, CRITICAL);
    }

    @Test(dependsOnMethods = {"validateCloseActiveNotification"})
    @TestCase(value = {"EPMCMBIBPC-1217"})
    public void validateSeveralInactiveNotifications() {
        changeStateOf(infoEditedTitle);
        changeStateOf(warningActiveNotification);
        changeStateOf(criticalNotification);

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
                .close()
                .ok();
    }

    @Test(dependsOnMethods = {"validateSeveralInactiveNotifications"})
    @TestCase(value = {"EPMCMBIBPC-1223"})
    public void validateDeleteInactiveNotification() {
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .searchForTableEntry(criticalNotification)
                .delete()
                .ensureTitleIs(String.format(deletionMessageFormat, criticalNotification))
                .ok()
                .ensureTableHasNoText(criticalNotification)
                .ok();
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-1229"})
    public void validateRoleModelForNotifications() {
        logout();
        loginAs(user)
                .settings()
                .ensure(SYSTEM_EVENTS_TAB, not(visible))
                .ok();
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
                .close()
                .ok();
    }

    private void validateActiveNotification(String titleText, String bodyText, Primitive severity) {
        new NotificationAO(titleText)
                .ensureSeverityIs(severity.name())
                .ensureTitleIs(titleText)
                .ensureBodyIs(bodyText);
    }

    private void ensureNotificationIsAbsent(String title) {
        $(byXpath(String.format("//*[contains(@class, 'system-notification__container') and contains(., '%s')]", title))).shouldNotBe(visible);
    }

    private void changeStateOf(String title) {
        navigationMenu()
                .settings()
                .switchToSystemEvents()
                .searchForTableEntry(title)
                .changeState()
                .close()
                .ok();
    }

    private String capitalCase(Primitive severity) {
        return severity.name().substring(0,1) + severity.name().substring(1).toLowerCase();
    }
}
