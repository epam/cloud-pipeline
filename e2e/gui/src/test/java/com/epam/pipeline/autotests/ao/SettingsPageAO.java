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

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.settings.CliAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import org.openqa.selenium.By;

import java.util.List;
import java.util.Map;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class SettingsPageAO extends PopupAO<SettingsPageAO, PipelinesLibraryAO> implements AccessObject<SettingsPageAO>,
        Authorization {

    protected PipelinesLibraryAO parentAO;

    @Override
    public SelenideElement context() {
        return $(byId("root-content"));
    }

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(CLI_TAB, $(byXpath(".//*[contains(@class, 'ant-menu-item') and contains(., 'CLI')]"))),
            entry(SYSTEM_EVENTS_TAB, $(byXpath(".//*[contains(@class, 'ant-menu-item') and contains(., 'System events')]"))),
            entry(USER_MANAGEMENT_TAB, context().find(byXpath(".//*[contains(@class, 'ant-menu-item') and contains(., 'User management')]"))),
            entry(PREFERENCES_TAB, context().find(byXpath(".//*[contains(@class, 'ant-menu-item') and contains(., 'Preferences')]"))),
            entry(SYSTEM_LOGS_TAB, context().find(byXpath(".//*[contains(@class, 'ant-menu-item') and contains(., 'System Management')]"))),
            entry(EMAIL_NOTIFICATIONS_TAB, context().find(byXpath(".//*[contains(@class, 'ant-menu-item') and contains(., 'Email notifications')]"))),
            entry(CLOUD_REGIONS_TAB, context().find(byXpath(".//*[contains(@class, 'ant-menu-item') and contains(., 'Cloud regions')]"))),
            entry(MY_PROFILE, context().find(byXpath(".//*[contains(@class, 'ant-menu-item') and contains(., 'My Profile')]"))),
            entry(OK, context().find(byId("settings-form-ok-button")))
    );

    public SettingsPageAO(PipelinesLibraryAO parent) {
        super(parent);
        this.parentAO = parent;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public CliAO switchToCLI() {
        click(CLI_TAB);
        return new CliAO(parentAO);
    }

    public SystemEventsAO switchToSystemEvents() {
        click(SYSTEM_EVENTS_TAB);
        return new SystemEventsAO(parentAO);
    }

    public UserManagementAO switchToUserManagement() {
        click(USER_MANAGEMENT_TAB);
        return new UserManagementAO(parentAO);
    }

    public PreferencesAO switchToPreferences() {
        click(PREFERENCES_TAB);
        return new PreferencesAO(parentAO);
    }

    public SystemManagementAO switchToSystemManagement() {
        click(SYSTEM_LOGS_TAB);
        return new SystemManagementAO(parentAO);
    }

    public MyProfileAO switchToMyProfile() {
        click(MY_PROFILE);
        return new MyProfileAO();
    }

    @Override
    public PipelinesLibraryAO cancel() {
        click(CANCEL);
        return parentAO;
    }

    public PipelinesLibraryAO ok() {
        click(OK);
        return parentAO;
    }

    public class SystemEventsAO extends SettingsPageAO {
        public static final String NEXT_PAGE = "Next Page";

        public final Map<Primitive, SelenideElement> elements = initialiseElements(
                super.elements(),
                entry(REFRESH, context().find(byId("refresh-notifications-button"))),
                entry(ADD, context().find(byId("add-notification-button"))),
                entry(TABLE, context().find(byClassName("ant-table-content")))
        );

        public SystemEventsAO(PipelinesLibraryAO pipelinesLibraryAO) {
            super(pipelinesLibraryAO);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        public SystemEventsAO ensureTableHasText(String text) {
            ensure(TABLE, matchText(text));
            return this;
        }

        public SystemEventsAO ensureTableHasNoDateText() {
            if (getAllEntries() != null) {
                return this;
            }
            ensure(TABLE, matchText("No data"));
            return this;
        }

        public SystemEventsAO ensureTableHasNoText(String text) {
            ensure(TABLE, not(matchText(text)));
            return this;
        }

        public SelenideElement getEntry(String title) {
            sleep(1, SECONDS);
            return elements().get(TABLE)
                    .find(byXpath(
                            format(".//tr[contains(@class, 'ant-table-row-level-0') and contains(., '%s')]", title)));
        }

        public SystemEventsEntry searchForTableEntry(String title) {
            sleep(1, SECONDS);
            while (!getEntry(title).isDisplayed()
                    && $(byTitle(NEXT_PAGE)).has(not(cssClass("ant-pagination-disabled")))) {
                click(byTitle(NEXT_PAGE));
            }
            SelenideElement entry = getEntry(title).shouldBe(visible);
            return new SystemEventsEntry(this, title, entry);
        }

        public CreateNotificationPopup add() {
            click(ADD);
            return new CreateNotificationPopup(this);
        }

        public SystemEventsAO deleteAllEntries() {
            sleep(2, SECONDS);
            List<SelenideElement> entries = getAllEntries();
            if (!entries.isEmpty()) {
                entries.forEach(this::removeEntry);
            }
            return this;
        }

        private List<SelenideElement> getAllEntries() {
            return context().find(byClassName("ant-table-content"))
                    .findAll(byXpath(".//tr[contains(@class, 'ant-table-row-level-0')]"));
        }

        public void deleteTestEntries(final List<String> testEntries) {
            sleep(2, SECONDS);
            testEntries.forEach(notification ->
                    navigationMenu()
                            .settings()
                            .switchToSystemEvents()
                            .removeEntryIfExist(notification));
        }

        private void removeEntry(SelenideElement entry) {
            entry.find(byId("delete-notification-button")).shouldBe(visible, enabled).click();
            new ConfirmationPopupAO<>(this)
                    .ensureTitleIs("Are you sure you want to delete notification")
                    .ok();
        }

        private void removeEntryIfExist(String title) {
            sleep(1, SECONDS);
            while (!getEntry(title).isDisplayed()
                    && $(byTitle(NEXT_PAGE)).has(not(cssClass("ant-pagination-disabled")))) {
                click(byTitle(NEXT_PAGE));
            }
            performIf(byXpath(format(".//tr[contains(@class, 'ant-table-row-level-0') and contains(., '%s')]", title)),
                    exist, entry -> removeEntry(getEntry(title))
            );
        }

        public class CreateNotificationPopup extends PopupAO<CreateNotificationPopup, SystemEventsAO> implements AccessObject<CreateNotificationPopup>{
            public final Map<Primitive, SelenideElement> elements = initialiseElements(
                    entry(TITLE, context().find(By.className("edit-notification-form-title-container")).find(byXpath(".//label[contains(@title, 'Title')]"))),
                    entry(TITLE_FIELD, context().find(By.className("edit-notification-form-title-container")).find(By.className("ant-input-lg"))),
                    entry(BODY, context().find(By.className("edit-notification-form-body-container")).find(byXpath(".//label[contains(@title, 'Body')]"))),
                    entry(BODY_FIELD, context().find(By.className("edit-notification-form-body-container")).find(byId("body"))),
                    entry(SEVERITY, context().find(By.className("edit-notification-form-severity-container")).find(byXpath(".//label[contains(@title, 'Severity')]"))),
                    entry(SEVERITY_COMBOBOX, context().find(By.className("edit-notification-form-severity-container")).find(By.className("ant-select-selection-selected-value"))),
                    entry(STATE, context().find(By.className("edit-notification-form-state-container")).find(byXpath(".//label[contains(@title, 'State')]"))),
                    entry(STATE_CHECKBOX, context().find(By.className("edit-notification-form-state-container")).find(byClassName("ant-checkbox"))),
                    entry(ACTIVE_LABEL, context().find(By.className("edit-notification-form-state-container")).find(byXpath(".//*[text() = 'Active']"))),
                    entry(CANCEL, context().find(byId("edit-notification-form-cancel-button"))),
                    entry(CREATE, context().find(byId("edit-notification-form-create-button")))
            );

            public CreateNotificationPopup(SystemEventsAO parentAO) {
                super(parentAO);
            }

            @Override
            public Map<Primitive, SelenideElement> elements() {
                return elements;
            }

            public CreateNotificationPopup ensureFieldMarkedAsRequired(Primitive field) {
                ensure(field, cssClass("ant-form-item-required"));
                return this;
            }

            public CreateNotificationPopup ensureSeverityIs(String severity) {
                ensure(SEVERITY_COMBOBOX, attribute("title", severity));
                return this;
            }

            public CreateNotificationPopup setTitle(String title) {
                setValue(TITLE_FIELD, title);
                return this;
            }

            public CreateNotificationPopup setBody(String bodyText) {
                setValue(BODY_FIELD, bodyText);
                return this;
            }

            public CreateNotificationPopup setActive() {
                click(STATE_CHECKBOX);
                return this;
            }

            public NotificationSeverityCombobox clickCombobox() {
                click(SEVERITY_COMBOBOX);
                return new NotificationSeverityCombobox(this);
            }

            public SystemEventsAO create() {
                click(CREATE);
                return parent();
            }

            public class NotificationSeverityCombobox extends ComboboxAO<NotificationSeverityCombobox, CreateNotificationPopup> {

                private final CreateNotificationPopup parentAO;

                public final Map<Primitive, SelenideElement> elements = initialiseElements(
                        entry(INFO, context().find(byClassName("cp-setting-info"))),
                        entry(WARNING, context().find(byClassName("cp-setting-warning"))),
                        entry(CRITICAL, context().find(byClassName("cp-setting-critical")))
                );

                public NotificationSeverityCombobox(CreateNotificationPopup parentAO) {
                    super(parentAO);
                    this.parentAO = parentAO;
                }

                @Override
                public Map<Primitive, SelenideElement> elements() {
                    return elements;
                }

                @Override
                SelenideElement closingElement() {
                    return parentAO.elements().get(SEVERITY_COMBOBOX);
                }

                public CreateNotificationPopup selectSeverity(Primitive severity) {
                    click(severity);
                    return parentAO;
                }
            }
        }

        public class EditNotificationPopup extends CreateNotificationPopup {

            public final Map<Primitive, SelenideElement> elements = initialiseElements(
                    entry(TITLE, context().find(By.className("edit-notification-form-title-container")).find(byXpath(".//label[contains(@title, 'Title')]"))),
                    entry(TITLE_FIELD, context().find(By.className("edit-notification-form-title-container")).find(By.className("ant-input-lg"))),
                    entry(BODY, context().find(By.className("edit-notification-form-body-container")).find(byXpath(".//label[contains(@title, 'Body')]"))),
                    entry(BODY_FIELD, context().find(By.className("edit-notification-form-body-container")).find(byId("body"))),
                    entry(SEVERITY, context().find(By.className("edit-notification-form-severity-container")).find(byXpath(".//label[contains(@title, 'Severity')]"))),
                    entry(SEVERITY_COMBOBOX, context().find(By.className("edit-notification-form-severity-container")).find(By.className("ant-select-selection-selected-value"))),
                    entry(STATE, context().find(By.className("edit-notification-form-state-container")).find(byXpath(".//label[contains(@title, 'State')]"))),
                    entry(STATE_CHECKBOX, context().find(By.className("edit-notification-form-state-container")).find(byClassName("ant-checkbox"))),
                    entry(ACTIVE_LABEL, context().find(By.className("edit-notification-form-state-container")).find(byXpath(".//*[text() = 'Active']"))),
                    entry(CANCEL, context().find(byId("edit-notification-form-cancel-button"))),
                    entry(SAVE, context().find(byId("edit-notification-form-save-button")))
            );

            public EditNotificationPopup(SystemEventsAO parentAO) {
                super(parentAO);
            }

            @Override
            public Map<Primitive, SelenideElement> elements() {
                return elements;
            }

            public SystemEventsAO save() {
                click(SAVE);
                return parent();
            }

            public EditNotificationPopup titleTo(String newTitle) {
                clear(TITLE_FIELD);
                setTitle(newTitle);
                return this;
            }

            public EditNotificationPopup bodyTo(String newBody) {
                clear(BODY_FIELD);
                setBody(newBody);
                return this;
            }
        }

        public class SystemEventsEntry implements AccessObject<SystemEventsEntry> {
            private final SystemEventsAO parentAO;
            private SelenideElement entry;
            private final Map<Primitive, SelenideElement> elements;
            private String title;

            public SystemEventsEntry(SystemEventsAO parentAO, String title, SelenideElement entry) {
                this.parentAO = parentAO;
                this.title = title;
                this.entry = entry;
                this.elements = initialiseElements(
                        entry(EXPAND, entry.find(byClassName("ant-table-row-collapsed"))),
                        entry(NARROW, entry.find(byClassName("ant-table-row-expanded"))),
                        entry(SEVERITY_ICON, entry.find(byClassName("notification-title-column")).find(byClassName("anticon"))),
                        entry(TITLE, entry.find(byClassName("notification-title-column")).find(byClassName("ant-row"))),
                        entry(DATE, entry.find(byClassName("notification-created-date-column"))),
                        entry(STATE, entry.find(byClassName("ant-checkbox-wrapper")).find(byClassName("ant-checkbox"))),
                        entry(ACTIVE_LABEL, entry.find(byClassName("notification-status-column")).find(byXpath(".//*[text() = 'Active']"))),
                        entry(EDIT, entry.find(byId("edit-notification-button"))),
                        entry(DELETE, entry.find(byId("delete-notification-button")))
                );
            }

            @Override
            public Map<Primitive, SelenideElement> elements() {
                return elements;
            }

            public SystemEventsAO close() {
                return parentAO;
            }

            public SystemEventsEntry ensureSeverityIconIs(String severity) {
                ensure(SEVERITY_ICON, cssClass(format("cp-setting-%s", severity.toLowerCase())));
                return this;
            }

            public SystemEventsEntry expand() {
                click(EXPAND);
                return this;
            }

            public SystemEventsEntry narrow() {
                click(NARROW);
                return this;
            }

            public SystemEventsEntry ensureBodyHasText(String bodyText) {
                $(byXpath(format(".//td[contains(., '%s')]/following::tr", title))).shouldHave(text(bodyText));
                return this;
            }

            public SystemEventsEntry ensureNoBodyText(String bodyText) {
                entry.shouldNotHave(text(bodyText));
                return this;
            }

            public SystemEventsEntry changeState() {
                click(STATE);
                return this;
            }

            public EditNotificationPopup edit() {
                click(EDIT);
                return new EditNotificationPopup(this.parentAO);
            }

            public ConfirmationPopupAO<SystemEventsAO> delete() {
                click(DELETE);
                return new ConfirmationPopupAO<>(this.parentAO);
            }
        }
    }

    public class MyProfileAO implements AccessObject<MyProfileAO> {
        private final Map<Primitive,SelenideElement> elements = initialiseElements(
                entry(USER_NAME, $(byClassName("rofile__header"))),
                entry(LIMIT_MOUNTS, $(byClassName("limit-mounts-input__limit-mounts-input"))),
                entry(DO_NOT_MOUNT_STORAGES, $(byXpath(".//span[.='Do not mount storages']/preceding-sibling::span")))
        );

        public MyProfileAO validateUserName(String user) {
            return ensure(USER_NAME, text(user));
        }

        public SelectLimitMountsPopupAO<MyProfileAO> limitMountsPerUser() {
            click(LIMIT_MOUNTS);
            return new SelectLimitMountsPopupAO<>(this).sleep(2, SECONDS);
        }

        public MyProfileAO doNotMountStoragesSelect(boolean isSelected) {
            if ((!get(DO_NOT_MOUNT_STORAGES).has(cssClass("ant-checkbox-checked")) && isSelected) ||
                    (get(DO_NOT_MOUNT_STORAGES).has(cssClass("ant-checkbox-checked")) && !isSelected)) {
                click(DO_NOT_MOUNT_STORAGES);
                sleep(1, SECONDS);
            }
            return this;
        }

        public MyProfileAO assertDoNotMountStoragesIsNotChecked() {
            get(DO_NOT_MOUNT_STORAGES).shouldNotHave(cssClass("ant-checkbox-checked"));
            return this;
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }
}
