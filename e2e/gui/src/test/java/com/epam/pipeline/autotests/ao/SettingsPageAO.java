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

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

public class SettingsPageAO extends PopupAO<SettingsPageAO, PipelinesLibraryAO> implements AccessObject<SettingsPageAO> {

    protected PipelinesLibraryAO parentAO;

    @Override
    public SelenideElement context() {
        return $(byClassName("ant-modal-content"));
    }

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(CLI_TAB, $(byXpath("//*[contains(@class, 'ant-tabs-tab') and contains(., 'CLI')]"))),
            entry(SYSTEM_EVENTS_TAB, $(byXpath("//*[contains(@class, 'ant-tabs-tab') and contains(., 'System events')]"))),
            entry(USER_MANAGEMENT_TAB, context().find(byXpath("//*[contains(@class, 'ant-tabs-tab') and contains(., 'User management')]"))),
            entry(PREFERENCES_TAB, context().find(byXpath("//*[contains(@class, 'ant-tabs-tab') and contains(., 'Preferences')]"))),
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
        return new CliAO();
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

    @Override
    public PipelinesLibraryAO cancel() {
        click(CANCEL);
        return parentAO;
    }

    private class CliAO{
    }

    public class SystemEventsAO extends SettingsPageAO {
        public final Map<Primitive, SelenideElement> elements = initialiseElements(
                super.elements(),
                entry(REFRESH, context().find(byId("refresh-notifications-button"))),
                entry(ADD, context().find(byId("add-notification-button"))),
                entry(TABLE, context().find(byClassName("ant-tabs-tabpane-active"))
                                .find(byClassName("ant-table-content")))
        );

        public SystemEventsAO(PipelinesLibraryAO pipelinesLibraryAO) {
            super(pipelinesLibraryAO);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        public SystemEventsAO ensureTableHasText(String text) {
            ensure(TABLE, matchesText(text));
            return this;
        }

        public SystemEventsAO ensureTableHasNoText(String text) {
            ensure(TABLE, not(matchesText(text)));
            return this;
        }

        public SystemEventsEntry searchForTableEntry(String title) {
            sleep(1, SECONDS);
            SelenideElement entry = elements().get(TABLE)
                    .find(byXpath(String.format(
                            ".//tr[contains(@class, 'ant-table-row-level-0') and contains(., '%s')]", title)))
                    .shouldBe(visible);
            return new SystemEventsEntry(this, title, entry);
        }

        public CreateNotificationPopup add() {
            click(ADD);
            return new CreateNotificationPopup(this);
        }

        public SystemEventsAO deleteAllEntries() {
            List<SelenideElement> entries = getAllEntries();
            if (!entries.isEmpty()) {
                entries.forEach(this::removeEntry);
            }
            return this;
        }

        private List<SelenideElement> getAllEntries() {
            return new ArrayList<>(context().find(byClassName("ant-tabs-tabpane-active"))
                    .findAll(byXpath(".//tr[contains(@class, 'ant-table-row-level-0')]")));
        }

        private void removeEntry(SelenideElement entry) {
            entry.find(byId("delete-notification-button")).shouldBe(visible).click();
            new ConfirmationPopupAO<>(this)
                    .ensureTitleIs("Are you sure you want to delete notification")
                    .ok();
        }

        public class CreateNotificationPopup extends PopupAO<CreateNotificationPopup, SystemEventsAO> implements AccessObject<CreateNotificationPopup>{
            public final Map<Primitive, SelenideElement> elements = initialiseElements(
                    entry(TITLE, context().find(By.className("edit-notification-form-title-container")).find(byXpath("//label[contains(@title, 'Title')]"))),
                    entry(TITLE_FIELD, context().find(By.className("edit-notification-form-title-container")).find(By.className("ant-input-lg"))),
                    entry(BODY, context().find(By.className("edit-notification-form-body-container")).find(byXpath("//label[contains(@title, 'Body')]"))),
                    entry(BODY_FIELD, context().find(By.className("edit-notification-form-body-container")).find(byId("body"))),
                    entry(SEVERITY, context().find(By.className("edit-notification-form-severity-container")).find(byXpath("//label[contains(@title, 'Severity')]"))),
                    entry(SEVERITY_COMBOBOX, context().find(By.className("edit-notification-form-severity-container")).find(By.className("ant-select-selection-selected-value"))),
                    entry(STATE, context().find(By.className("edit-notification-form-state-container")).find(byXpath("//label[contains(@title, 'State')]"))),
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
                ensure(SEVERITY_COMBOBOX, attribute("title", String.format("[object Object], %s", severity)));
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
                        entry(INFO, context().find(By.className("edit-system-notification-form__info"))),
                        entry(WARNING, context().find(By.className("edit-system-notification-form__warning"))),
                        entry(CRITICAL, context().find(By.className("edit-system-notification-form__critical")))
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
                    entry(TITLE, context().find(By.className("edit-notification-form-title-container")).find(byXpath("//label[contains(@title, 'Title')]"))),
                    entry(TITLE_FIELD, context().find(By.className("edit-notification-form-title-container")).find(By.className("ant-input-lg"))),
                    entry(BODY, context().find(By.className("edit-notification-form-body-container")).find(byXpath("//label[contains(@title, 'Body')]"))),
                    entry(BODY_FIELD, context().find(By.className("edit-notification-form-body-container")).find(byId("body"))),
                    entry(SEVERITY, context().find(By.className("edit-notification-form-severity-container")).find(byXpath("//label[contains(@title, 'Severity')]"))),
                    entry(SEVERITY_COMBOBOX, context().find(By.className("edit-notification-form-severity-container")).find(By.className("ant-select-selection-selected-value"))),
                    entry(STATE, context().find(By.className("edit-notification-form-state-container")).find(byXpath("//label[contains(@title, 'State')]"))),
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
                ensure(SEVERITY_ICON, cssClass(String.format("settings-form__%s", severity.toLowerCase())));
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
                $(byXpath(String.format("//td[contains(., '%s')]/following::tr", title))).shouldHave(text(bodyText));
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

    public class UserManagementAO extends SettingsPageAO {
        public final Map<Primitive, SelenideElement> elements = initialiseElements(
                super.elements(),
                entry(USERS_TAB, $$(byClassName("ant-tabs-tab")).findBy(text("Users"))),
                entry(GROUPS_TAB, $$(byClassName("ant-tabs-tab")).findBy(text("Groups"))),
                entry(ROLE_TAB, $$(byClassName("ant-tabs-tab")).findBy(text("Roles")))
        );

        public UserManagementAO(PipelinesLibraryAO pipelinesLibraryAO) {
            super(pipelinesLibraryAO);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

        public UsersTabAO switchToUsers() {
            click(USERS_TAB);
            return new UsersTabAO(parentAO);
        }

        public GroupsTabAO switchToGroups() {
            click(GROUPS_TAB);
            return new GroupsTabAO(parentAO);
        }

        public class UsersTabAO extends SystemEventsAO {
            public final Map<Primitive, SelenideElement> elements = initialiseElements(
                    super.elements(),
                    entry(TABLE, context().find(byClassName("ant-tabs-tabpane-active"))
                            .find(byClassName("ant-table-content"))),
                    entry(SEARCH, context().find(byClassName("ant-input-search")))
            );

            public UsersTabAO(PipelinesLibraryAO parentAO) {
                super(parentAO);
            }

            @Override
            public Map<Primitive, SelenideElement> elements() {
                return elements;
            }

            public UserEntry searchForUserEntry(String login) {
                sleep(1, SECONDS);
                SelenideElement entry = elements().get(TABLE)
                        .find(byXpath(String.format(
                                ".//tr[contains(@class, 'ant-table-row-level-0') and contains(., '%s')]", login)))
                        .shouldBe(visible);
                return new UserEntry(this, login, entry);
            }

            public UsersTabAO clickSearch() {
                click(SEARCH);
                return this;
            }

            public UsersTabAO pressEnter() {
                actions().sendKeys(Keys.ENTER).perform();
                return this;
            }

            public class UserEntry implements AccessObject<SystemEventsEntry> {
                private final UsersTabAO parentAO;
                private SelenideElement entry;
                private final Map<Primitive, SelenideElement> elements;
                private String login;

                public UserEntry(UsersTabAO parentAO, String login, SelenideElement entry) {
                    this.parentAO = parentAO;
                    this.login = login;
                    this.entry = entry;
                    this.elements = initialiseElements(
                            entry(EDIT, entry.find(byId("edit-user-button")))
                    );
                }

                @Override
                public Map<Primitive, SelenideElement> elements() {
                    return elements;
                }

                public SystemEventsAO close() {
                    return parentAO;
                }

                public EditUserPopup edit() {
                    click(EDIT);
                    return new EditUserPopup(parentAO);
                }

                public class EditUserPopup extends PopupAO<EditUserPopup, UsersTabAO> implements AccessObject<EditUserPopup> {
                    private final SelenideElement element = context().find(byText("Add role or group:"))
                            .closest(".ant-row-flex").find(By.className("ant-select-allow-clear"));
                    public final Map<Primitive, SelenideElement> elements = initialiseElements(
                            entry(SEARCH, element),
                            entry(SEARCH_INPUT, element.find(By.className("ant-select-search__field"))),
                            entry(OK, context().find(By.id("close-edit-user-form")))
                    );

                    public EditUserPopup(UsersTabAO parentAO) {
                        super(parentAO);
                    }

                    @Override
                    public Map<Primitive, SelenideElement> elements() {
                        return elements;
                    }

                    @Override
                    public UsersTabAO ok() {
                        click(OK);
                        return parentAO;
                    }

                    public EditUserPopup validateRoleAppearedInSearch(String role) {
                        sleep(1, SECONDS);
                        $$(byClassName("ant-select-dropdown-menu-item")).findBy(text(role)).shouldBe(visible);
                        return this;
                    }

                    public EditUserPopup searchRoleBySubstring(String substring) {
                        click(SEARCH);
                        setValue(SEARCH_INPUT, substring);
                        return this;
                    }
                }
            }
        }

        public class GroupsTabAO extends SystemEventsAO {

            public final Map<Primitive, SelenideElement> elements = initialiseElements(
                    super.elements(),
                    entry(TABLE, context().find(byClassName("ant-tabs-tabpane-active"))
                            .find(byClassName("ant-table-content"))),
                    entry(SEARCH, context().find(byId("search-groups-input"))),
                    entry(CREATE_GROUP, context().$$(byAttribute("type", "button"))
                            .findBy(text("Create group"))),
                    entry(OK, $(byClassName("ant-confirm-body-wrapper")).find(byClassName("ant-btn-primary")))
            );

            public GroupsTabAO(final PipelinesLibraryAO parentAO) {
                super(parentAO);
            }

            @Override
            public Map<Primitive, SelenideElement> elements() {
                return elements;
            }

            public CreateGroupPopup pressCreateGroup() {
                click(CREATE_GROUP);
                return new CreateGroupPopup(this);
            }

            public GroupsTabAO deleteGroup(final String groupName) {
                sleep(1, SECONDS);
                context().$(byText(groupName))
                        .closest(".ant-table-row-level-0")
                        .find(byClassName("ant-btn-danger"))
                        .click();
                return confirmGroupDeletion(groupName);
            }

            public GroupsTabAO searchGroupBySubstring(final String part) {
                click(SEARCH);
                setValue(SEARCH, part);
                return this;
            }

            private GroupsTabAO confirmGroupDeletion(final String groupName) {
                new ConfirmationPopupAO(this.parentAO)
                    .ensureTitleIs(String.format("Are you sure you want to delete group '%s'?", groupName))
                    .sleep(1, SECONDS)
                    .click(OK);
                return this;
            }

            public class CreateGroupPopup extends PopupAO<CreateGroupPopup, GroupsTabAO> implements AccessObject<CreateGroupPopup> {
                private final GroupsTabAO parentAO;

                public CreateGroupPopup(final GroupsTabAO parentAO) {
                    super(parentAO);
                    this.parentAO = parentAO;
                }

                public final Map<Primitive, SelenideElement> elements = initialiseElements(
                        entry(EDIT_GROUP, context()
                                .find(byAttribute("placeholder", "Enter group name"))),
                        entry(DEFAULT_SETTINGS, context().find(byClassName("ant-checkbox-wrapper"))
                                .find(byText("Default"))),
                        entry(CREATE, context().$$(byClassName("ant-btn-primary"))
                                .exclude(cssClass("ant-dropdown-trigger")).find(Condition.exactText("Create"))),
                        entry(CANCEL, context().$$(byClassName("ant-btn-primary"))
                                .exclude(cssClass("ant-dropdown-trigger")).find(Condition.exactText("Cancel")))
                );

                @Override
                public Map<Primitive, SelenideElement> elements() {
                    return elements;
                }

                public CreateGroupPopup enterGroupName(final String groupName) {
                    click(EDIT_GROUP);
                    setValue(EDIT_GROUP, groupName);
                    return this;
                }

                public GroupsTabAO create() {
                    click(CREATE);
                    return parentAO;
                }

                public GroupsTabAO cancel() {
                    click(CANCEL);
                    return parentAO;
                }
            }
        }
    }

    public class PreferencesAO extends SettingsPageAO {
        public final Map<Primitive, SelenideElement> elements = initialiseElements(
                super.elements(),
                entry(CLUSTER_TAB, $$(byClassName("preferences__preference-group-row")).findBy(text("Cluster")))
        );

        PreferencesAO(final PipelinesLibraryAO pipelinesLibraryAO) {
            super(pipelinesLibraryAO);
        }

        public ClusterTabAO switchToCluster() {
            click(CLUSTER_TAB);
            return new ClusterTabAO(parentAO);
        }

        public PreferencesAO save() {
            $(byId("edit-preference-form-ok-button")).shouldBe(visible).click();
            return this;
        }

        public class ClusterTabAO extends PreferencesAO {

            ClusterTabAO(final PipelinesLibraryAO parentAO) {
                super(parentAO);
            }

            private By clusterHddExtraMulti() {
                return new By() {
                    @Override
                    public List<WebElement> findElements(final SearchContext context) {
                        return $$(byClassName("preference-group__preference-row"))
                                .stream()
                                .filter(element -> text("cluster.instance.hdd_extra_multi").apply(element))
                                .map(e -> e.find(".ant-input-sm"))
                                .collect(toList());
                    }
                };
            }

            public PreferencesAO setClusterHddExtraMulti(final String value) {
                final By clusterHddExtraMultiValue = clusterHddExtraMulti();
                click(clusterHddExtraMultiValue);
                clear(clusterHddExtraMultiValue);
                setValue(clusterHddExtraMultiValue, value);
                return this;
            }

            public String getClusterHddExtraMulti() {
                return $(clusterHddExtraMulti()).getValue();
            }

            @Override
            public Map<Primitive, SelenideElement> elements() {
                return elements;
            }
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }
}
