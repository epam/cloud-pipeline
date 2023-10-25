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
package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.settings.CliAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import com.epam.pipeline.autotests.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.UnaryOperator;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.buttonByIconClass;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.menuitem;
import static com.epam.pipeline.autotests.utils.Utils.*;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.By.tagName;
import static org.testng.Assert.assertTrue;

public class SettingsPageAO extends PopupAO<SettingsPageAO, PipelinesLibraryAO> implements AccessObject<SettingsPageAO>,
        Authorization {

    protected PipelinesLibraryAO parentAO;

    @Override
    public SelenideElement context() {
        return $(byId("root-content"));
    }

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(CLI_TAB, $(byXpath("//*[contains(@class, 'ant-menu-item') and contains(., 'CLI')]"))),
            entry(SYSTEM_EVENTS_TAB, $(byXpath("//*[contains(@class, 'ant-menu-item') and contains(., 'System events')]"))),
            entry(USER_MANAGEMENT_TAB, context().find(byXpath("//*[contains(@class, 'ant-menu-item') and contains(., 'User management')]"))),
            entry(PREFERENCES_TAB, context().find(byXpath("//*[contains(@class, 'ant-menu-item') and contains(., 'Preferences')]"))),
            entry(SYSTEM_LOGS_TAB, context().find(byXpath("//*[contains(@class, 'ant-menu-item') and contains(., 'System Management')]"))),
            entry(EMAIL_NOTIFICATIONS_TAB, context().find(byXpath("//*[contains(@class, 'ant-menu-item') and contains(., 'Email notifications')]"))),
            entry(CLOUD_REGIONS_TAB, context().find(byXpath("//*[contains(@class, 'ant-menu-item') and contains(., 'Cloud regions')]"))),
            entry(MY_PROFILE, context().find(byXpath("//*[contains(@class, 'ant-menu-item') and contains(., 'My Profile')]"))),
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
            ensure(TABLE, matchesText(text));
            return this;
        }

        public SystemEventsAO ensureTableHasNoDateText() {
            if (getAllEntries() != null) {
                return this;
            }
            ensure(TABLE, matchesText("No data"));
            return this;
        }

        public SystemEventsAO ensureTableHasNoText(String text) {
            ensure(TABLE, not(matchesText(text)));
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
                if(!impersonateMode()) {
                    click(STATE_CHECKBOX);
                }
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
                $(byXpath(format("//td[contains(., '%s')]/following::tr", title))).shouldHave(text(bodyText));
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
                entry(USERS_TAB, $(byClassName("section-users")).find(byText("Users"))),
                entry(GROUPS_TAB, $(byClassName("section-groups")).find(byText("Groups"))),
                entry(ROLE_TAB, $(byClassName("section-roles")).find(byText("Roles")))
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

        public RolesTabAO switchToRoles() {
            click(ROLE_TAB);
            return new RolesTabAO(parentAO);
        }

        public class UsersTabAO extends SystemEventsAO {
            public final Map<Primitive, SelenideElement> elements = initialiseElements(
                    super.elements(),
                    entry(TABLE, context().find(byClassName("user-management-form__container"))
                            .find(byClassName("ant-table-tbody"))),
                    entry(SEARCH, context().find(byId("search-users-input"))),
                    entry(CREATE_USER, context().find(button("Create user"))),
                    entry(EXPORT_USERS, context().find(button("Export users"))),
                    entry(SHOW_USERS, context().find(byClassName("ant-select-selection-selected-value")))
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
                while (!getUser(login.toUpperCase()).isDisplayed()
                        && $(byTitle(NEXT_PAGE)).has(not(cssClass("ant-pagination-disabled")))) {
                    click(byTitle(NEXT_PAGE));
                }
                SelenideElement entry = getUser(login.toUpperCase()).shouldBe(visible);
                return new UserEntry(this, login, entry);
            }

            public UsersTabAO createUser(final String name) {
                click(CREATE_USER);
                setValue(byId("name"), name);
                click(byId("create-user-form-ok-button"), byClassName("ant-modal-content"));
                return this;
            }

            private SelenideElement getUser(final String login) {
                return elements().get(TABLE)
                        .find(byXpath(format(
                                ".//td[contains(@class, 'user-management-form__user-name-column') and " +
                                        "starts-with(.//text(), '%s')]", login)))
                        .closest(".ant-table-row-level-0");
            }

            public UsersTabAO clickSearch() {
                click(SEARCH);
                clear(SEARCH);
                return this;
            }

            public UsersTabAO pressEnter() {
                actions().sendKeys(Keys.ENTER).perform();
                return this;
            }

            public UsersTabAO pressMagnifierIcon() {
                $(byClassName("user-management-form__container"))
                        .$(byClassName("ant-input-search-icon")).shouldBe(enabled).click();
                return this;
            }

            public UsersTabAO searchUser(String name) {
                sleep(1, SECONDS);
                clear(SEARCH);
                return clickSearch()
                        .setSearchName(name)
                        .pressEnter();
            }

            public UsersTabAO exportUsers() {
                click(EXPORT_USERS);
                return this;
            }

            public UserEntry searchUserEntry(String login) {
                searchUser(login);
                SelenideElement entry = getUser(login).shouldBe(visible);
                return new UserEntry(this, login, entry);
            }

            public UsersTabAO checkUserExist(String name) {
                searchUser(name).sleep(1, SECONDS);
                getUser(name.toUpperCase()).shouldBe(visible);
                return this;
            }

            public UsersTabAO checkUserNotExist(String name) {
                searchUser(name).sleep(1, SECONDS);
                getUser(name.toUpperCase()).shouldNotBe(exist);
                return this;
            }

            public UsersTabAO checkUserRoles(String name, String...roles) {
                 List<String> roleLabels = getUser(name.toUpperCase())
                         .find(byClassName("user-management-form__roles-column"))
                         .findAll(byXpath(".//span"))
                         .stream()
                         .map(SelenideElement::text)
                         .collect(toList());
                Arrays.stream(roles).forEach(role -> assertTrue(roleLabels.contains(role),
                        format("Role label %s isn't found in '%s'", role, roleLabels)));
                return this;
            }

            public UsersTabAO deleteUser(String name) {
                return new UserEntry(this, name.toUpperCase(), getUser(name.toUpperCase()).shouldBe(visible))
                            .edit()
                            .deleteUser(name);
            }

            public UsersTabAO checkUserTabIsEmpty() {
                sleep(1, SECONDS);
                $(byText("No data")).shouldBe(visible, exist);
                return this;
            }

            public UsersTabAO setSearchName(String name) {
                actions().sendKeys(name).perform();
                return this;
            }

            private boolean userTabIsEmpty() {
                sleep(1, SECONDS);
                return $(byText("No data")).isDisplayed();
            }

            public CreateUserPopup clickCreateButton() {
                click(CREATE_USER);
                return new CreateUserPopup(this);
            }

            public UsersTabAO createIfNotExist(String name) {
                if (clickSearch().setSearchName(name).pressEnter().userTabIsEmpty()) {
                    clickCreateButton()
                            .setValue(NAME, name)
                            .ok();
                }
                return this;
            }

            public UsersTabAO deleteUserIfExist(String name) {
                if (!clickSearch().setSearchName(name).pressEnter().userTabIsEmpty()) {
                    SelenideElement entry = getUser(name.toUpperCase()).shouldBe(visible);
                    new UserEntry(this, name.toUpperCase(), entry)
                            .edit()
                            .delete();
                    confirmUserDeletion(name);
                }
                return this;
            }

            private UsersTabAO confirmUserDeletion(final String name) {
                new ConfirmationPopupAO(this.parentAO)
                        .ensureTitleIs(format("Are you sure you want to delete user %s?", name.toUpperCase()))
                        .sleep(1, SECONDS)
                        .click(OK);
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
                            entry(EDIT, entry.find(byId("edit-user-button"))),
                            entry(STATUS, entry.find("circle") )
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

                public UserEntry validateUserStatus(final String status) {
                    get(STATUS).shouldBe(visible).shouldHave(cssClass(format("cp-user-status-%s", status)));
                    return this;
                }

                public UsersTabAO validateStatusTooltipText(String tooltipText) {
                    hover(STATUS);
                    $(PipelineSelectors.visible(byClassName("ant-tooltip")))
                            .find(byClassName("ant-tooltip-content"))
                            .shouldHave(Condition.text(tooltipText));
                    return parentAO;
                }

                public UserEntry validateBlockedStatus(final String username, final boolean blockedStatus) {
                    final SelenideElement userWithStatus = entry.find(byClassName("user-management-form__line-break"));
                    if (blockedStatus) {
                        userWithStatus.shouldHave(text(format("%s- blocked", username)));
                        return this;
                    }
                    userWithStatus.shouldHave(text(format("%s", username)));
                    return this;
                }

                public boolean isBlockedUser(final String username) {
                    return entry.find(byClassName("user-management-form__line-break"))
                            .has(text(format("%s- blocked", username)));
                }

                public class EditUserPopup extends PopupAO<EditUserPopup, UsersTabAO> implements AccessObject<EditUserPopup> {
                    private final SelenideElement element = context().find(byText("Add role or group:"))
                            .closest(".ant-row-flex").find(By.className("ant-select-allow-clear"));
                    public final Map<Primitive, SelenideElement> elements = initialiseElements(
                            entry(SEARCH, element),
                            entry(SEARCH_INPUT, element.find(By.className("ant-select-search__field"))),
                            entry(ADD_KEY, context().find(By.id("add-role-button"))),
                            entry(OK, context().find(By.id("close-edit-user-form"))),
                            entry(CANCEL, context().$(button("CANCEL"))),
                            entry(BLOCK, context().$(button("BLOCK"))),
                            entry(UNBLOCK, context().$(button("UNBLOCK"))),
                            entry(DELETE, context().$(byId("delete-user-button"))),
                            entry(PRICE_TYPE, context().find(byXpath(
                                    format("//div/b[text()='%s']/following::div/input", "Allowed price types")))),
                            entry(CONFIGURE, context().$(byXpath(".//span[.='Can run as this user:']/following-sibling::a"))),
                            entry(IMPERSONATE, context().$(button("IMPERSONATE"))),
                            entry(DO_NOT_MOUNT_STORAGES, $(byXpath(".//span[.='Do not mount storages']/preceding-sibling::span")))
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
                        if (get(OK).isEnabled()) {
                            click(OK);
                        } else {
                            click(CANCEL);
                        }
                        return parentAO;
                    }

                    public UsersTabAO delete() {
                        click(DELETE);
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

                    public EditUserPopup addRoleOrGroup(final String value) {
                        click(SEARCH);
                        $$(byClassName("ant-select-dropdown-menu-item")).findBy(exactText(value)).click();
                        click(ADD_KEY);
                        return this;
                    }

                    public EditUserPopup addRoleOrGroupIfNonExist(final String value) {
                        if ($(By.className(format("role-%s", value))).exists()) {
                            return this;
                        }
                        return addRoleOrGroup(value);
                    }

                    public EditUserPopup deleteRoleOrGroup(final String value) {
                        $$(byClassName("role-name-column"))
                                .findBy(text(value))
                                .closest("tr")
                                .find(By.id("delete-role-button"))
                                .click();
                        return this;
                    }

                    public EditUserPopup blockUser(final String user) {
                        click(BLOCK);
                        new ConfirmationPopupAO(this)
                                .ensureTitleIs(format("Are you sure you want to block user %s?", user))
                                .ok();
                        return this;
                    }

                    public EditUserPopup unblockUser(final String user) {
                        click(UNBLOCK);
                        new ConfirmationPopupAO(this)
                                .ensureTitleIs(format("Are you sure you want to unblock user %s?", user))
                                .ok();
                        return this;
                    }

                    public UsersTabAO deleteUser(final String user) {
                        click(DELETE);
                        new ConfirmationPopupAO(this)
                                .ensureTitleIs(format("Are you sure you want to delete user %s?", user))
                                .ok();
                        return parentAO;
                    }

                    public EditUserPopup addAllowedLaunchOptions(final String option, final String mask) {
                        SettingsPageAO.this.addAllowedLaunchOptions(option, mask);
                        return this;
                    }

                    public EditUserPopup setAllowedPriceType(final String priceType) {
                        click(PRICE_TYPE);
                        context().find(byClassName("ant-select-dropdown")).find(byText(priceType))
                                .shouldBe(visible)
                                .click();
                        return this;
                    }

                    public EditUserPopup clearAllowedPriceTypeField() {
                        ensureVisible(PRICE_TYPE);
                        SelenideElement type = context().$(byClassName("ant-select-selection__choice__remove"));
                        while (type.isDisplayed()) {
                            type.click();
                            sleep(1, SECONDS);
                        }
                        return this;
                    }

                    public EditUserPopup configureRunAs(final String name, final boolean sshConnection) {
                        click(CONFIGURE);
                        new LogAO.ShareWith().addUserToShare(name, sshConnection);
                        return this;
                    }

                    public EditUserPopup resetConfigureRunAs(final String name) {
                        click(CONFIGURE);
                        SelenideElement shareWithContext = Utils.getPopupByTitle("Share with users and groups");
                        shareWithContext
                                .$(byClassName("ant-table-tbody"))
                                .find(byXpath(
                                        format(".//tr[contains(@class, 'ant-table-row-level-0') and contains(., '%s')]",
                                                name)))
                                .find(buttonByIconClass("anticon-delete"))
                                .shouldBe(visible)
                                .click();
                        new LogAO.ShareWith().click(OK);
                        return this;
                    }

                    public boolean checkConfigureRunAs(final String name) {
                        return context().$(byXpath(".//span[.='Can run as this user:']/following-sibling::a"))
                                .shouldBe(visible).$$(byXpath(".//span")).texts().contains(name);
                    }

                    public NavigationHomeAO impersonate() {
                        click(IMPERSONATE);
                        return new NavigationHomeAO();
                    }

                    public EditUserPopup doNotMountStoragesSelect (boolean isSelected) {
                        if(!get(DO_NOT_MOUNT_STORAGES).exists()) {
                            return this;
                        }
                        if ((!get(DO_NOT_MOUNT_STORAGES).has(cssClass("ant-checkbox-checked")) && isSelected) ||
                                (get(DO_NOT_MOUNT_STORAGES).has(cssClass("ant-checkbox-checked")) && !isSelected)) {
                            click(DO_NOT_MOUNT_STORAGES);
                        }
                        return this;
                    }
                }
            }

            public class CreateUserPopup extends PopupAO<CreateUserPopup, UsersTabAO> {

                public CreateUserPopup(UsersTabAO parentAO) {
                    super(parentAO);
                }

                public final Map<Primitive, SelenideElement> elements = initialiseElements(
                        entry(NAME, context().$(byId("name"))),
                        entry(OK, context().$(byId("create-user-form-ok-button")))
                );

                @Override
                public UsersTabAO ok() {
                    return click(OK).parent();
                }

                @Override
                public Map<Primitive, SelenideElement> elements() {
                    return elements;
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
                            .findBy(text("Create group")))
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

            public GroupsTabAO deleteGroupIfPresent(String group) {
                sleep(2, SECONDS);
                searchGroupBySubstring(group.split(StringUtils.SPACE)[0]);
                performIf(context().$$(byText(group)).filterBy(visible).first().exists(), t -> deleteGroup(group));
                return this;
            }

            public GroupsTabAO deleteGroup(final String groupName) {
                sleep(1, SECONDS);
                context().$$(byText(groupName))
                        .filterBy(visible)
                        .first()
                        .closest(".ant-table-row-level-0")
                        .find(byClassName("ant-btn-danger"))
                        .click();
                return confirmGroupDeletion(groupName);
            }

            public GroupsTabAO searchGroupBySubstring(final String part) {
                setValue(SEARCH, part);
                return this;
            }

            private GroupsTabAO confirmGroupDeletion(final String groupName) {
                new ConfirmationPopupAO(this.parentAO)
                        .ensureTitleIs(format("Are you sure you want to delete group '%s'?", groupName))
                        .sleep(1, SECONDS)
                        .click(OK);
                return this;
            }

            public EditGroupPopup editGroup(final String group) {
                sleep(1, SECONDS);
                searchGroupBySubstring(group);
                context().$$(byText(group))
                        .filterBy(visible)
                        .first()
                        .closest(".ant-table-row-level-0")
                        .find(byClassName("ant-btn-sm"))
                        .click();
                return new EditGroupPopup(this);
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

            public class EditGroupPopup extends PopupAO<EditGroupPopup, GroupsTabAO>
                    implements AccessObject<EditGroupPopup> {
                private final GroupsTabAO parentAO;
                public final Map<Primitive, SelenideElement> elements = initialiseElements(
                        entry(OK, context().find(By.id("close-edit-user-form"))),
                        entry(CANCEL, context().$(button("CANCEL"))),
                        entry(PRICE_TYPE, context().find(byXpath(
                                format("//div/b[text()='%s']/following::div/input", "Allowed price types"))))
                );

                public EditGroupPopup(final GroupsTabAO parentAO) {
                    super(parentAO);
                    this.parentAO = parentAO;
                }

                @Override
                public Map<Primitive, SelenideElement> elements() {
                    return elements;
                }

                @Override
                public GroupsTabAO ok() {
                    if (get(OK).isEnabled()) {
                        click(OK);
                    } else {
                        click(CANCEL);
                    }
                    return parentAO;
                }

                public EditGroupPopup addAllowedLaunchOptions(String option, String mask) {
                    SettingsPageAO.this.addAllowedLaunchOptions(option, mask);
                    return this;
                }

                public EditGroupPopup setAllowedPriceType(final String priceType) {
                    click(PRICE_TYPE);
                    context().find(byClassName("ant-select-dropdown")).find(byText(priceType))
                            .shouldBe(visible)
                            .click();
                    click(byText("Allowed price types"));
                    return this;
                }

                public EditGroupPopup clearAllowedPriceTypeField() {
                    ensureVisible(PRICE_TYPE);
                    SelenideElement type = context().$(byClassName("ant-select-selection__choice__remove"));
                    while (type.isDisplayed()) {
                        type.click();
                        sleep(1, SECONDS);
                    }
                    click(byText("Allowed price types"));
                    return this;
                }
            }
        }

        public class RolesTabAO extends SystemEventsAO {
            public final Map<Primitive, SelenideElement> elements = initialiseElements(
                    super.elements(),
                    entry(TABLE, context().find(byClassName("ant-tabs-tabpane-active"))
                            .find(byClassName("ant-table-content"))),
                    entry(SEARCH, context().find(byId("search-roles-input")))
            );

            public RolesTabAO(PipelinesLibraryAO parentAO) {
                super(parentAO);
            }

            @Override
            public Map<Primitive, SelenideElement> elements() {
                return elements;
            }

            public RolesTabAO editRoleIfPresent(String role) {
                sleep(2, SECONDS);
                performIf(context().$$(byText(role)).filterBy(visible).first().exists(), t -> editRole(role));
                return this;
            }

            public EditRolePopup editRole(final String role) {
                sleep(1, SECONDS);
                searchRoleBySubstring(role);
                context().$$(byText(role))
                        .filterBy(visible)
                        .first()
                        .closest(".ant-table-row-level-0")
                        .find(byClassName("ant-btn-sm"))
                        .click();
                return new EditRolePopup(this);
            }

            public RolesTabAO clickSearch() {
                click(SEARCH);
                return this;
            }

            public RolesTabAO searchRoleBySubstring(final String part) {
                setValue(SEARCH, part);
                return this;
            }

            public class EditRolePopup extends PopupAO<EditRolePopup, RolesTabAO>
                    implements AccessObject<EditRolePopup> {
                private final RolesTabAO parentAO;
                public final Map<Primitive, SelenideElement> elements = initialiseElements(
                        entry(OK, context().find(By.id("close-edit-user-form"))),
                        entry(PRICE_TYPE, context().find(byXpath(
                                format("//div/b[text()='%s']/following::div/input", "Allowed price types"))))
                );

                public EditRolePopup(final RolesTabAO parentAO) {
                    super(parentAO);
                    this.parentAO = parentAO;
                }

                @Override
                public Map<Primitive, SelenideElement> elements() {
                    return elements;
                }

                @Override
                public RolesTabAO ok() {
                    click(OK);
                    return parentAO;
                }
            }
        }
    }

    public class PreferencesAO extends SettingsPageAO {
        public final Map<Primitive, SelenideElement> elements = initialiseElements(
                super.elements(),
                entry(CLUSTER_TAB, $(byClassName("section-cluster"))),
                entry(SYSTEM_TAB, $(byClassName("section-system"))),
                entry(DOCKER_SECURITY_TAB, $(byClassName("section-docker-security"))),
                entry(AUTOSCALING_TAB, $(byClassName("section-grid-engine-autoscaling"))),
                entry(USER_INTERFACE_TAB, $(byClassName("section-user-interface"))),
                entry(LUSTRE_FS_TAB, $(byClassName("section-lustre-fs"))),
                entry(LAUNCH_TAB, $(byClassName("section-launch"))),
                entry(SEARCH,  context().find(byClassName("ant-input-search")).find(tagName("input"))),
                entry(SAVE, $(byId("edit-preference-form-ok-button")))
        );

        PreferencesAO(final PipelinesLibraryAO pipelinesLibraryAO) {
            super(pipelinesLibraryAO);
        }

        public ClusterTabAO switchToCluster() {
            click(CLUSTER_TAB);
            return new ClusterTabAO(parentAO);
        }

        public SystemTabAO switchToSystem() {
            click(SYSTEM_TAB);
            return new SystemTabAO(parentAO);
        }

        public AutoscalingTabAO switchToAutoscaling() {
            click(AUTOSCALING_TAB);
            return new AutoscalingTabAO(parentAO);
        }

        public DockerSecurityAO switchToDockerSecurity() {
            click(DOCKER_SECURITY_TAB);
            return new DockerSecurityAO(parentAO);
        }

        public UserInterfaceAO switchToUserInterface() {
            click(USER_INTERFACE_TAB);
            return new UserInterfaceAO(parentAO);
        }

        public PreferencesAO searchPreference(String preference) {
            clear(SEARCH);
            setValue(SEARCH, preference);
            enter();
            return this;
        }

        public LustreFSAO switchToLustreFS() {
            click(LUSTRE_FS_TAB);
            return new LustreFSAO(parentAO);

        }

        public LaunchAO switchToLaunch() {
            click(LAUNCH_TAB);
            return new LaunchAO(parentAO);
        }

        private void setEyeOption(boolean eyeIsChecked) {
            final SelenideElement eye = context().find(byClassName("preference-group__preference-row"))
                    .find(byClassName("anticon"));
            if((eye.has(cssClass("anticon-eye-o")) && eyeIsChecked) ||
                    (eye.has(cssClass("anticon-eye")) && !eyeIsChecked)) {
                eye.click();
            }
        }

        public PreferencesAO setPreference(String preference, String value, boolean eyeIsChecked) {
            searchPreference(preference);
            final By pref = getByField(preference);
            click(pref)
                    .clear(pref)
                    .setValue(pref, value);
            setEyeOption(eyeIsChecked);
            return this;
        }

        public String[] getPreference(String preference) {
            searchPreference(preference);
            $(byClassName("CodeMirror-code")).shouldBe(visible);
            sleep(1, SECONDS);
            final List<String> list = $$(byClassName("CodeMirror-line")).texts();
            final String[] prefValue = new String[2];
            prefValue[0] = (list.size() <= 1 ) ? String.join("", list) : String.join("\n", list);
            prefValue[1] = String.valueOf(!context().find(byClassName("preference-group__preference-row"))
                    .$(byClassName("anticon")).has(cssClass("anticon-eye-o")));
            return prefValue;
        }

        public PreferencesAO clearAndSetJsonToPreference(String preference, String value, boolean eyeIsChecked) {
            SelenideElement pref = context().$(byClassName("preference-group__code-editor"));
            searchPreference(preference);
            selectAllAndClearTextField(pref);
            clickAndSendKeysWithSlashes(pref, value);
            deleteExtraBrackets(pref, 300);
            setEyeOption(eyeIsChecked);
            return this;
        }

        public PreferencesAO updateCodeText(final String preference, final String value, final boolean eyeIsChecked) {
            searchPreference(preference);
            final SelenideElement editor = $(byClassName("CodeMirror-line"));
            selectAllAndClearTextField(editor);
            final StringSelection stringSelection = new StringSelection(value);
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(stringSelection, null);
            actions().moveToElement(editor).click()
                    .sendKeys(Keys.chord(Keys.CONTROL, "v"))
                    .perform();
            setEyeOption(eyeIsChecked);
            return this;
        }

        public String[] getLinePreference(String preference) {
            searchPreference(preference);
            String[] prefValue = new String[2];
            final SelenideElement selenideElement = context().$$(byClassName("preference-group__preference-row"))
                    .filter(exactText(preference)).first();
            prefValue[0] = selenideElement.$(byClassName("ant-input-sm")).attr("value");
            prefValue[1] = String.valueOf(!selenideElement.$(byClassName("anticon")).has(cssClass("anticon-eye-o")));
            return prefValue;
        }

        public PreferencesAO setCheckboxPreference(String preference, boolean checkboxIsEnable, boolean eyeIsChecked) {
            searchPreference(preference);
            final SelenideElement checkBox = context().shouldBe(visible)
                    .find(byXpath(".//span[.='Enabled']/preceding-sibling::span"));
            if ((checkBox.has(cssClass("ant-checkbox-checked")) && !checkboxIsEnable) ||
                    (!checkBox.has(cssClass("ant-checkbox-checked")) && checkboxIsEnable)) {
                checkBox.click();
            }
            setEyeOption(eyeIsChecked);
            return this;
        }

        public boolean[] getCheckboxPreferenceState(String preference) {
            boolean[] checkboxState = new boolean[2];
            searchPreference(preference);
            checkboxState[0] = context().shouldBe(visible)
                    .find(byXpath(".//span[.='Enabled']/preceding-sibling::span")).has(cssClass("ant-checkbox-checked"));
            checkboxState[1] = context().find(byClassName("preference-group__preference-row"))
                    .find(byClassName("anticon")).has(cssClass("anticon-eye"));
            return checkboxState;
        }

        private By getByField(final String variable) {
            return new By() {
                @Override
                public List<WebElement> findElements(final SearchContext context) {
                    return $$(byClassName("preference-group__preference-row"))
                            .stream()
                            .filter(element -> exactText(variable).apply(element))
                            .map(e -> e.find(".ant-input-sm"))
                            .collect(toList());
                }
            };
        }

        private By getPreferenceState(String preference) {
            return new By() {
                @Override
                public List<WebElement> findElements(final SearchContext context) {
                    return $$(byClassName("preference-group__preference-row"))
                            .stream()
                            .filter(element -> exactText(preference).apply(element))
                            .map(e -> e.find(byCssSelector("i")))
                            .collect(toList());
                }
            };
        }

        private By getByCheckbox(final String variable) {
            return new By() {
                @Override
                public List<WebElement> findElements(final SearchContext context) {
                    return $$(byClassName("preference-group__preference-row"))
                            .stream()
                            .filter(element -> text(variable).apply(element))
                            .map(e -> e.find(".ant-checkbox-wrapper"))
                            .collect(toList());
                }
            };
        }

        public PreferencesAO setSystemSshDefaultRootUserEnabled() {
            setValue(SEARCH, "system.ssh.default.root.user.enabled").enter();
            SelenideElement checkBox = context().shouldBe(visible)
                    .find(byXpath(".//span[.='Enabled']/preceding-sibling::span"));
            if (!checkBox.has(cssClass("ant-checkbox-checked"))) {
                checkBox.click();
            }
            if (context().find(byClassName("anticon-eye-o")).isDisplayed()) {
                context().find(byClassName("anticon-eye-o")).click();
            }
            return this;
        }

        public String[] getAmisFromClusterNetworksConfigPreference(String region) {
            final String[] ami = new String[2];
            searchPreference("cluster.networks.config");
            $(byClassName("CodeMirror-code")).shouldBe(visible);
            sleep(1, SECONDS);
            final String[] strings = $$(byClassName("CodeMirror-line")).texts().toArray(new String[0]);
            try {
                JsonNode instance = new ObjectMapper().readTree(String.join("", strings)).get("regions");
                for (JsonNode node1 : instance) {
                    if (node1.get("name").asText().equals(region)) {
                        for (JsonNode node : node1.get("amis")) {
                            if (node.path("instance_mask").asText().equals("*")) {
                                ami[0] = node.path("ami").asText();
                            } else {
                                ami[1] = node.path("ami").asText();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(format("Could not deserialize JSON content %s, cause: %s",
                        String.join("", strings), e.getMessage()), e);
            }
            return ami;
        }

        public PreferencesAO save() {
        /* click(SAVE) method has been replaced by pressEnter() method due to the problem of pressing
           the Save button at case of big size json or a lot of preferences on the page for current Chromium version
        */
            actions().moveToElement(get(SAVE)).perform();
            get(SAVE).pressEnter();
            get(SAVE).waitUntil(disabled, C.DEFAULT_TIMEOUT);
            return this;
        }

        public PreferencesAO saveIfNeeded() {
            actions().moveToElement($(By.id("edit-preference-form-ok-button")))
                     .perform();
            sleep(10, SECONDS);
            if(get(SAVE).isEnabled()) {
                save();
            }
            return this;
        }

        public PreferencesAO updatePreference(String preference, String value, boolean eyeIsChecked) {
            return setPreference(preference, value, eyeIsChecked)
                    .saveIfNeeded();
        }

        public class ClusterTabAO extends PreferencesAO {

            private final By dockerExtraMulti = getByField("cluster.docker.extra_multi");
            private final By instanceHddExtraMulti = getByField("cluster.instance.extra_multi");
            private final By clusterAllowedInstanceTypes = getByField("cluster.allowed.instance.types");
            private final By clusterAllowedInstanceTypesDocker = getByField(
                    "cluster.allowed.instance.types.docker");

            public static final String CLUSTER_AWS_EBS_TYPE = "cluster.aws.ebs.type";

            ClusterTabAO(final PipelinesLibraryAO parentAO) {
                super(parentAO);
            }

            public PreferencesAO setDockerExtraMulti(final String value) {
                setByVariable(value, dockerExtraMulti);
                return this;
            }

            public PreferencesAO setInstanceHddExtraMulti(final String value) {
                setByVariable(value, instanceHddExtraMulti);
                return this;
            }

            public String getDockerExtraMulti() {
                return getClusterValue(dockerExtraMulti);
            }

            public String getInstanceHddExtraMulti() {
                return getClusterValue(instanceHddExtraMulti);
            }

            public PreferencesAO setClusterAllowedStringPreference(String mask, String value) {
                return setClusterValue(mask, value);
            }

            public ClusterTabAO checkClusterAllowedInstanceTypes(final String value) {
                ensure(clusterAllowedInstanceTypes, value(value));
                return this;
            }

            public ClusterTabAO checkClusterAllowedInstanceTypesDocker(final String value) {
                ensure(clusterAllowedInstanceTypesDocker, value(value));
                return this;
            }

            public ClusterTabAO checkClusterAwsEbsType(final String value) {
                ensure(getByField(CLUSTER_AWS_EBS_TYPE), value(value));
                ensure(getPreferenceState(CLUSTER_AWS_EBS_TYPE), cssClass("anticon-eye"));
                return this;
            }

            private ClusterTabAO setClusterValue(final String clusterPref, final String value) {
                By clusterVariable = getByField(clusterPref);
                setByVariable(value, clusterVariable);
                while(!$(clusterVariable).attr("value").equals(value)) {
                    sleep(500, MILLISECONDS);
                }
                return this;
            }

            private void setByVariable(final String value, final By clusterVariable) {
                click(clusterVariable);
                clear(clusterVariable);
                setValue(clusterVariable, value);
            }

            private String getClusterValue(final By clusterVariable) {
                return $(clusterVariable).getValue();
            }

            @Override
            public Map<Primitive, SelenideElement> elements() {
                return elements;
            }
        }

        public class SystemTabAO extends PreferencesAO {

            private final By maxIdleTimeout = getByField("system.max.idle.timeout.minutes");
            private final By idleActionTimeout = getByField("system.idle.action.timeout.minutes");
            private final By idleCpuThreshold = getByField("system.idle.cpu.threshold");
            private final By idleAction = getByField("system.idle.action");
            private final By ldapUserBlockMonitor = getByCheckbox("system.ldap.user.block.monitor.enable");
            private final By userMonitor = getByCheckbox("system.user.monitor.enable");
            private final By systemMaintenanceMode = getByCheckbox("system.maintenance.mode");
            private final By systemMaintenanceModeBanner = getByField("system.maintenance.mode.banner");

            SystemTabAO(final PipelinesLibraryAO parentAO) {
                super(parentAO);
            }

            public SystemTabAO setMaxIdleTimeout(final String value) {
                return setSystemValue(maxIdleTimeout, value);
            }

            public String getMaxIdleTimeout() {
                return getSystemValue(maxIdleTimeout);
            }

            public SystemTabAO setIdleActionTimeout(final String value) {
                return setSystemValue(idleActionTimeout, value);
            }

            public String getIdleActionTimeout() {
                return getSystemValue(idleActionTimeout);
            }

            public SystemTabAO setIdleCpuThreshold(final String value) {
                return setSystemValue(idleCpuThreshold, value);
            }

            public String getIdleCpuThreshold() {
                return getSystemValue(idleCpuThreshold);
            }

            public SystemTabAO setIdleAction(final String value) {
                return setSystemValue(idleAction, value);
            }

            public String getIdleAction() {
                return getSystemValue(idleAction);
            }

            public SystemTabAO setSystemMaintenanceModeBanner(final String value) {
                return setSystemValue(systemMaintenanceModeBanner, value);
            }

            public String getSystemMaintenanceModeBanner() {
                return getSystemValue(systemMaintenanceModeBanner);
            }

            public SystemTabAO setEmptySystemMaintenanceModeBanner() {
                click(systemMaintenanceModeBanner);
                PreferencesAO.this.clearByKey(systemMaintenanceModeBanner);
                return this;
            }

            private SystemTabAO setSystemValue(final By systemVariable, final String value) {
                click(systemVariable);
                clear(systemVariable);
                setValue(systemVariable, value);
                return this;
            }

            private String getSystemValue(final By systemVariable) {
                return $(systemVariable).getValue();
            }

            public boolean getLdapUserBlockMonitor() {
                return $(ldapUserBlockMonitor).$(byXpath(".//span")).has(cssClass("ant-checkbox-checked"));
            }

            public SystemTabAO enableLdapUserBlockMonitor() {
                if (!getLdapUserBlockMonitor()) {
                    click(ldapUserBlockMonitor);
                }
                return this;
            }

            public SystemTabAO disableLdapUserBlockMonitor() {
                if (getLdapUserBlockMonitor()) {
                    click(ldapUserBlockMonitor);
                }
                return this;
            }

            public boolean getUserMonitor() {
                return $(userMonitor).$(byXpath(".//span")).has(cssClass("ant-checkbox-checked"));
            }

            public SystemTabAO enableUserMonitor() {
                if (!getUserMonitor()) {
                    click(userMonitor);
                }
                return this;
            }

            public SystemTabAO disableUserMonitor() {
                if (getUserMonitor()) {
                    click(userMonitor);
                }
                return this;
            }

            public boolean getSystemMaintenanceMode() {
                return $(systemMaintenanceMode).$(byXpath(".//span")).has(cssClass("ant-checkbox-checked"));
            }

            public SystemTabAO enableSystemMaintenanceMode() {
                if (!getSystemMaintenanceMode()) {
                    click(systemMaintenanceMode);
                }
                return this;
            }

            public SystemTabAO disableSystemMaintenanceMode() {
                if (getSystemMaintenanceMode()) {
                    click(systemMaintenanceMode);
                }
                return this;
            }

            @Override
            public Map<Primitive, SelenideElement> elements() {
                return elements;
            }
        }

        public class DockerSecurityAO extends PreferencesAO {

            private final By policyDenyNotScanned = getByCheckbox("security.tools.policy.deny.not.scanned");
            private final By graceHours = getByField("security.tools.grace.hours");

            DockerSecurityAO(final PipelinesLibraryAO parentAO) {
                super(parentAO);
            }

            public DockerSecurityAO enablePolicyDenyNotScanned() {
                if (!$(policyDenyNotScanned).$(byXpath(".//span")).has(cssClass("ant-checkbox-checked"))) {
                    clickPolicyDenyNotScanned();
                }
                return this;
            }

            public DockerSecurityAO disablePolicyDenyNotScanned() {
                if ($(policyDenyNotScanned).$(byXpath(".//span")).has(cssClass("ant-checkbox-checked"))) {
                    clickPolicyDenyNotScanned();
                }
                return this;
            }

            public DockerSecurityAO clickPolicyDenyNotScanned() {
                click(policyDenyNotScanned);
                return this;
            }

            public String getGraceHours() {
                return getDockerSecurityValue(graceHours);
            }

            public boolean getPolicyDenyNotScanned() {
                return getDockerSecurityCheckbox(policyDenyNotScanned).equals("Enable");
            }

            public DockerSecurityAO setGraceHours(final String value) {
                click(graceHours);
                clear(graceHours);
                setValue(graceHours, value);
                return this;
            }

            private String getDockerSecurityValue(final By dockerSecurityVar) {
                return $(dockerSecurityVar).getValue();
            }

            private String getDockerSecurityCheckbox(final By dockerSecurityVar) {
                return $(dockerSecurityVar).getText();
            }
        }

        public class AutoscalingTabAO extends PreferencesAO {

            private final By scaleDownTimeout = getByField("ge.autoscaling.scale.down.timeout");
            private final By scaleUpTimeout = getByField("ge.autoscaling.scale.up.timeout");

            AutoscalingTabAO(final PipelinesLibraryAO parentAO) {
                super(parentAO);
            }

            public AutoscalingTabAO setScaleDownTimeout(final String value) {
                click(scaleDownTimeout);
                clear(scaleDownTimeout);
                setValue(scaleDownTimeout, value);
                return this;
            }

            public AutoscalingTabAO setScaleUpTimeout(final String value) {
                click(scaleUpTimeout);
                clear(scaleUpTimeout);
                setValue(scaleUpTimeout, value);
                return this;
            }
        }

        public class UserInterfaceAO extends PreferencesAO {

            public static final String SUPPORT_TEMPLATE = "ui.support.template";

            private final By supportTemplateValue = getByField(SUPPORT_TEMPLATE);
            private final By supportTemplateState = getPreferenceState(SUPPORT_TEMPLATE);

            UserInterfaceAO(final PipelinesLibraryAO parentAO) {
                super(parentAO);
            }

            public UserInterfaceAO checkSupportTemplate(final String value) {
                ensure(supportTemplateValue, value(value));
                ensure(supportTemplateState, cssClass("anticon-eye"));
                return this;
            }

            public String getSupportTemplate() {
                return $(supportTemplateValue).getValue();
            }
        }

        public class LustreFSAO extends PreferencesAO {

            private final By lustreFSMountOptions = getByField("lustre.fs.mount.options");

            LustreFSAO(final PipelinesLibraryAO parentAO) {
                super(parentAO);
            }

            public LustreFSAO checkLustreFSMountOptionsValue(final String value) {
                ensure(lustreFSMountOptions, value(value));
                return this;
            }
        }

        public class LaunchAO extends PreferencesAO {

            public static final String LAUNCH_PARAMETERS = "launch.system.parameters";
            public static final String LAUNCH_CONTAINER_CPU_RESOURCES = "launch.container.cpu.resource";

            LaunchAO(PipelinesLibraryAO pipelinesLibraryAO) {
                super(pipelinesLibraryAO);
            }

            public LaunchAO checkLaunchSystemParameters(final String value) {
                final String launchSystemParameters = $$(byClassName("preference-group__preference-row")).stream()
                        .map(SelenideElement::getText)
                        .filter(e -> e.startsWith(LAUNCH_PARAMETERS))
                        .map(e -> e.replaceAll("\\n[0-9]*\\n", "\n"))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException(format(
                                "%s preference was not found.", LAUNCH_PARAMETERS
                        )));
                assertTrue(launchSystemParameters.contains(value),
                        format("Value %s isn't found in '%s' preference", value, launchSystemParameters));
                return this;
            }

            public LaunchAO checkLaunchContainerCpuResource(final String value) {
                ensure(getByField(LAUNCH_CONTAINER_CPU_RESOURCES), value(value));
                return this;
            }

            public String getLaunchSystemParameters() {
                return $$(byClassName("preference-group__preference-row")).stream()
                        .map(SelenideElement::getText)
                        .filter(e -> e.startsWith(LAUNCH_PARAMETERS))
                        .map(e -> e.replaceAll("\\n[0-9]*\\n", "\n")
                                .replaceFirst(LAUNCH_PARAMETERS + "\n", ""))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException(format(
                                "%s preference was not found.", LAUNCH_PARAMETERS
                        )));
            }

            public LaunchAO setLaunchSystemParameters(final UnaryOperator<String> action) {
                final String launchSystemParameters = getLaunchSystemParameters();
                final String edited = action.apply(launchSystemParameters);
                if (launchSystemParameters.equals(edited)) {
                    return this;
                }
                final SelenideElement launchSystemParameter = $$(byClassName("preference-group__preference-row")).stream()
                        .filter(s -> s.getText().startsWith(LAUNCH_PARAMETERS))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException(format(
                                "%s preference was not found.", LAUNCH_PARAMETERS
                        )));
                return this;
            }
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }

    public class MyProfileAO implements AccessObject<MyProfileAO> {
        private final Map<Primitive,SelenideElement> elements = initialiseElements(
                entry(USER_NAME, $(byClassName("rofile__header"))),
                entry(LIMIT_MOUNTS, $(byClassName("limit-mounts-input__limit-mounts-input"))),
                entry(DO_NOT_MOUNT_STORAGES, $(byXpath(".//span[.='Do not mount storages']/preceding-sibling::span"))),
                entry(MUTE_EMAIL_NOTIFICATIONS, $(byXpath(".//span[.='Mute email notifications']/preceding-sibling::span")))
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

        public MyProfileAO muteEmailNotificationsSelect(boolean isSelected) {
            sleep(1, SECONDS);
            if ((!get(MUTE_EMAIL_NOTIFICATIONS).has(cssClass("ant-checkbox-checked")) && isSelected) ||
                    (get(MUTE_EMAIL_NOTIFICATIONS).has(cssClass("ant-checkbox-checked")) && !isSelected)) {
                click(MUTE_EMAIL_NOTIFICATIONS);
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

    private void addAllowedLaunchOptions(final String option, final String mask) {
        final By optionField = byXpath(format("//div/b[text()='%s']/following::div/input", option));
        if (StringUtils.isBlank(mask)) {
            clearByKey(optionField);
            return;
        }
        setValue(optionField, mask);
    }
}
