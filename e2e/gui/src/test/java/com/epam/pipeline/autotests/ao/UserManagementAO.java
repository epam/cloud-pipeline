/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exist;
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
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_KEY;
import static com.epam.pipeline.autotests.ao.Primitive.BLOCK;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CONFIGURE;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE_GROUP;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE_USER;
import static com.epam.pipeline.autotests.ao.Primitive.DEFAULT_SETTINGS;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.DO_NOT_MOUNT_STORAGES;
import static com.epam.pipeline.autotests.ao.Primitive.EDIT;
import static com.epam.pipeline.autotests.ao.Primitive.EDIT_GROUP;
import static com.epam.pipeline.autotests.ao.Primitive.EXPORT_USERS;
import static com.epam.pipeline.autotests.ao.Primitive.GROUPS_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.IMPERSONATE;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.PRICE_TYPE;
import static com.epam.pipeline.autotests.ao.Primitive.ROLE_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.SEARCH;
import static com.epam.pipeline.autotests.ao.Primitive.SEARCH_INPUT;
import static com.epam.pipeline.autotests.ao.Primitive.SHOW_USERS;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;
import static com.epam.pipeline.autotests.ao.Primitive.TABLE;
import static com.epam.pipeline.autotests.ao.Primitive.UNBLOCK;
import static com.epam.pipeline.autotests.ao.Primitive.USERS_TAB;
import static com.epam.pipeline.autotests.utils.C.DEFAULT_TIMEOUT;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.buttonByIconClass;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.By.className;
import static org.testng.Assert.assertTrue;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import com.epam.pipeline.autotests.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
                get(STATUS).shouldBe(visible).shouldHave(cssClass(format("cp-status-%s", status)));
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

                public EditUserPopup switchToTab(TabHeader tabHeader) {
                    context().find(byText(tabHeader.header)).click();
                    return this;
                }

                @Override
                public UsersTabAO ok() {
                    if (get(OK).isEnabled()) {
                        click(OK);
                    } else {
                        click(CANCEL);
                    }
                    $(className("edit-user-roles-dialog__modal-container"))
                            .waitUntil(not(visible), DEFAULT_TIMEOUT);
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
                    $(By.className("role-ROLE_USER")).waitUntil(exist, DEFAULT_TIMEOUT);
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

                public EditUserPopup deleteRoleOrGroupIfExist(final String value) {
                    $(byClassName("edit-user-roles-dialog__table")).waitUntil(exist, DEFAULT_TIMEOUT);
                    if(!$$(byClassName("role-name-column")).findBy(text(value)).exists()) {
                        return this;
                    }
                    return deleteRoleOrGroup(value);
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
                    addAllowedLaunchOptions(option, mask);
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
                addAllowedLaunchOptions(option, mask);
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

    public void addAllowedLaunchOptions(final String option, final String mask) {
        $(byText("Allowed price types")).shouldBe(visible, enabled);
        final By optionField = byXpath(format("//div/b[text()='%s']/following::div/input", option));
        if (StringUtils.isBlank(mask)) {
            clearByKey(optionField);
            return;
        }
        setValue(optionField, mask);
    }

    public enum TabHeader {
        PROFILE("PROFILE"),
        STATISTICS("STATISTICS"),
        PERMISSIONS("PERMISSIONS");

        public final String header;

        TabHeader(final String header) {
            this.header = header;
        }
    }
}
