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

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.*;

import java.util.stream.Stream;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.*;
import static com.epam.pipeline.autotests.utils.Permission.permissionsTable;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.buttonByIconClass;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
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

    public PermissionTabAO addNewUser(String userName) {
        UserAdditionPopupAO userAdditionPopupAO = clickAddNewUser()
                .typeInField(userName);
        $(visible(byText("Select user"))).click();
        return userAdditionPopupAO
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

    private void clickOnInfoTabIfItIsVisible() {
        sleep(1, SECONDS);
        SelenideElement infoTab = $$(byText("Info")).findBy(visible);
        if(infoTab.is(visible)) {
            infoTab.click();
        }
    }

    private static SelenideElement getElementByNameInUpperCase(String groupName) {
        return getTabTable()
                .find(withText(groupName));
    }

    private static SelenideElement getTabTable() {
        return $(visible(withText("Groups and users")))
                .closest(".ant-table");
    }

    public PermissionTabAO addNewGroup(String usersGroup) {
        return clickAddNewGroup()
                .typeInField(usersGroup)
                .ok();
    }

    public PermissionTabAO deleteIfPresent(String userOrGroup) {
        sleep(1, SECONDS);
        return performIf(getElementByNameInUpperCase(userOrGroup).is(visible),
                tab -> tab.delete(userOrGroup));
    }

    public PermissionTabAO delete(String usersGroup) {
        $$(byClassName("ant-table-tbody"))
                .find(text(usersGroup))
                .find(tagName("button"))
                .click();
        return this;
    }

    @Override
    public void closeAll() {
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
                    .forEach(row -> row.findAll(byClassName("ant-checkbox-input")).shouldHaveSize(2));
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

        @Override
        public UserAdditionPopupAO typeInField(String value) {
            context().find(tagName("input")).shouldBe(visible).setValue(value);
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
    }

}
