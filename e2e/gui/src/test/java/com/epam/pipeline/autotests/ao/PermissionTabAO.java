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
package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.*;

import java.util.stream.Stream;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.*;
import static com.epam.pipeline.autotests.utils.Permission.permissionsTable;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.buttonByIconClass;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.tagName;

public class PermissionTabAO implements ClosableAO, AccessObject<PermissionTabAO> {
    private final ClosableAO parentAO;

    public PermissionTabAO(ClosableAO parentAO) {
        this.parentAO = parentAO;
    }

    public PermissionTabElementAO selectByName(String login) {
        return new PermissionTabElementAO(this, login);
    }

    public UserAdditionPopupAO clickAddNewUser() {
        getTabTable()
                .find(buttonByIconClass("anticon-user-add")).shouldBe(visible).click();
        return new UserAdditionPopupAO(this);
    }

    public GroupAdditionPopupAO clickAddNewGroup() {
        getTabTable()
                .find(buttonByIconClass("anticon-usergroup-add")).shouldBe(visible).click();
        return new GroupAdditionPopupAO(this);
    }

    public PermissionTabAO validateUserHasPermissions(String userName) {
        getElementByNameInUpperCase(userName).shouldBe(visible);
        return this;
    }

    public PermissionTabAO validateGroupHasPermissions(String groupName) {
        getElementByNameInUpperCase(groupName).shouldBe(visible);
        return this;
    }

    /**
     * Adds the user to the form, unless it is listed already (adding it again would reset its permissions to the
     * default ones). The change is saved by {@link #closeAll()}, which clicks APPLY.
     */
    public PermissionTabAO addNewUser(String userName) {
        if (getRowByExactName(userName).exists()) {
            return this;
        }
        return clickAddNewUser()
                .typeInField(userName)
                .selectUser(userName)
                .ok();
    }

    public PermissionTabAO validateDeleteButtonIsDisplayedOppositeTo(String name) {
        $$(byClassName("ant-table-tbody")).find(text(name))
                .find(tagName("button"))
                .find(tagName("i"))
                .shouldHave(cssClass("anticon-delete"))
                .shouldBe(visible);
        return this;
    }

    /**
     * Checks the delete button in the row of this user or group itself, not in the first row of the table.
     */
    public PermissionTabAO deleteButtonShouldBeShownFor(final String userOrGroup) {
        getRowByExactName(userOrGroup).shouldBe(visible)
                .find(tagName("button")).shouldBe(visible)
                .find(byClassName("anticon-delete")).shouldBe(visible);
        return this;
    }

    /**
     * For a user who can't change the owner, the owner is plain text; admins and the owner get an input instead.
     */
    public PermissionTabAO ownerShouldBe(final String owner) {
        $(byId("object-owner")).shouldHave(exactText(owner));
        return this;
    }

    private void clickOnInfoTabIfItIsVisible() {
        sleep(1, SECONDS);
        SelenideElement infoTab = $$(byText("Info")).findBy(visible);
        if(infoTab.is(visible)) {
            infoTab.click();
        }
    }

    private static SelenideElement getElementByNameInUpperCase(String groupName) {
        for (int i = 0; i < 3; i++) {
            if (getTabTable().find(withText(groupName)).exists()) {
                return getTabTable().find(withText(groupName));
            }
            Utils.sleep(3, SECONDS);
        }
        return getTabTable().find(withText(groupName.toLowerCase()));
    }

    private static SelenideElement getTabTable() {
        return $(visible(withText("Groups and users")))
                .closest(".ant-table");
    }

    public PermissionTabAO addNewGroup(String usersGroup) {
        return clickAddNewGroup()
                .typeInField(usersGroup)
                .selectGroup(usersGroup)
                .ok();
    }

    public PermissionTabAO deleteIfPresent(String userOrGroup) {
        sleep(1, SECONDS);
        return performIf(getElementByNameInUpperCase(userOrGroup).is(visible),
                tab -> tab.delete(userOrGroup));
    }

    public boolean rowExists(final String userOrGroup) {
        for (int i = 0; i < 3; i++) {
            if (getRowByExactName(userOrGroup).is(visible)) {
                return true;
            }
            sleep(3, SECONDS);
        }
        return false;
    }

    public UserPermissionsTableAO showRowPermissions(final String userOrGroup) {
        getRowByExactName(userOrGroup).shouldBe(visible).findAll("td").get(1).click();
        return new UserPermissionsTableAO(this);
    }

    public PermissionTabAO deleteRowIfPresent(final String userOrGroup) {
        if (rowExists(userOrGroup)) {
            getRowByExactName(userOrGroup).find(tagName("button")).shouldBe(enabled).click();
            getRowByExactName(userOrGroup).shouldNot(exist);
        }
        return this;
    }

    // a row shows a user by its lowercase name and a group by its name; exactText ignores the case
    private static SelenideElement getRowByExactName(final String userOrGroup) {
        return getTabTable().findAll(byClassName("ant-table-row")).findBy(exactText(userOrGroup));
    }

    public PermissionTabAO delete(String usersGroup) {
        $$(byClassName("ant-table-tbody"))
                .find(text(usersGroup))
                .find(tagName("button"))
                .click();
        return this;
    }

    public boolean checkPermissionExistence(String userOrGroup) {
        sleep(1, SECONDS);
        return getElementByNameInUpperCase(userOrGroup).is(visible);
    }

    /**
     * Saves the changes, then closes. The permissions form (client/.../roleModel/PermissionsForm.js) keeps added,
     * removed and changed entries in the dialog until APPLY is clicked. While the save runs, APPLY is disabled and
     * "Updating permissions..." is shown; once that message is gone, APPLY stays disabled only if the save worked,
     * so a failed save fails here. A read-only form has no APPLY. The form is always in a dialog, so a button of the
     * page underneath is not taken for it.
     */
    @Override
    public void closeAll() {
        final SelenideElement apply = $$(byXpath("//div[contains(@class, 'ant-modal-content')]//button[.='APPLY']"))
                .findBy(visible);
        if (apply.exists() && apply.isEnabled()) {
            apply.click();
            $(byXpath("//div[contains(@class, 'ant-message-notice')][contains(., 'Updating permissions')]"))
                    .shouldNot(exist, ofMillis(C.DEFAULT_TIMEOUT * 2));
            apply.shouldBe(disabled);
        }
        clickOnInfoTabIfItIsVisible();
        parentAO.closeAll();
    }

    public static class PermissionTabElementAO implements ClosableAO {

        private final ClosableAO parentAO;
        private final String login;

        public PermissionTabElementAO(ClosableAO parentAO, String login) {
            this.parentAO = parentAO;
            this.login = login;
        }

        public UserPermissionsTableAO showPermissions() {
            getElementByNameInUpperCase(login).shouldBe(visible).click();
            return new UserPermissionsTableAO(this);
        }

        @Override
        public void closeAll() {
            parentAO.closeAll();
        }
    }

    public static class UserPermissionsTableAO implements ClosableAO {
        private final ClosableAO parentAO;

        public UserPermissionsTableAO(ClosableAO parentAO) {
            this.parentAO = parentAO;
        }

        public UserPermissionsTableAO validateAllPrivilegesAreListed() {
            Stream.of("Read", "Write", "Execute")
                    .forEach(this::validatePrivilegeIsListed);
            return this;
        }

        private void validatePrivilegeIsListed(String privilege) {
            $(permissionsTable).find(byText(privilege)).shouldBe(visible);
        }

        public UserPermissionsTableAO validateAllCheckboxesAreListed() {
            Privilege.privilegesRows()
                    .forEach(row -> row.findAll(byClassName("ant-checkbox-input")).shouldHave(size(2)));
            return this;
        }

        public UserPermissionsTableAO validatePrivilegeValue(Privilege privilege, PrivilegeValue value) {
            privilege.shoudBeSetTo(value);
            return this;
        }

        public UserPermissionsTableAO set(Privilege privilege, PrivilegeValue value) {
            privilege.setTo(value);
            return this;
        }

        @Override
        public void closeAll() {
            parentAO.closeAll();
        }
    }

    public static class UserAdditionPopupAO extends PopupWithStringFieldAO<UserAdditionPopupAO, PermissionTabAO> {

        public UserAdditionPopupAO(PermissionTabAO parentAO) {
            super(parentAO);
        }

        /**
         * Opens the dialog's Select, then types. Its search input stays hidden (display: none) until the dropdown
         * opens, and a click does nothing while the dialog still zooms in, or while the Select is disabled, which it
         * is until the users are loaded.
         */
        @Override
        public UserAdditionPopupAO typeInField(String value) {
            final SelenideElement dialog = context().shouldBe(visible).closest(".ant-modal");
            dialog.shouldNotHave(cssClass("zoom-appear"));
            dialog.shouldNotHave(cssClass("zoom-enter"));
            context().find(byClassName("ant-select-enabled")).shouldBe(visible)
                    .find(byClassName("ant-select-selection")).click();
            context().find(tagName("input")).shouldBe(visible).setValue(value);
            return this;
        }

        /**
         * Chooses the user's option: the dialog keeps a user only when an option is chosen, typing alone selects
         * nothing. The options appear once three characters are typed, and show the user's display name, which is
         * the account name for a user without name attributes (exactText ignores the case).
         */
        public UserAdditionPopupAO selectUser(String userName) {
            $$(byClassName("ant-select-dropdown-menu-item")).filterBy(visible).findBy(exactText(userName))
                    .shouldBe(visible).click();
            return this;
        }

        @Override
        public SelenideElement context() {
            return Utils.getPopupByTitle("Select user");
        }
    }

    public static class GroupAdditionPopupAO extends PopupWithStringFieldAO<GroupAdditionPopupAO, PermissionTabAO> {

        public GroupAdditionPopupAO(PermissionTabAO parentAO) {
            super(parentAO);
        }

        @Override
        public GroupAdditionPopupAO typeInField(String value) {
            Utils.getPopupByTitle("Select group")
                    .find(tagName("input")).shouldBe(visible).setValue(value);
            return this;
        }

        private GroupAdditionPopupAO selectGroup(String name) {
            // the visible menu: a user dropdown opened before (selectUser) stays in the page, hidden
            $$(byClassName("ant-select-dropdown-menu-root")).findBy(visible).$(byText(name)).shouldBe(visible).click();
            return this;
        }
    }

}
