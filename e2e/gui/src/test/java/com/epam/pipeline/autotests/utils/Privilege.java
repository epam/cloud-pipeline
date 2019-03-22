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
package com.epam.pipeline.autotests.utils;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.utils.Permission.permissionsTable;
import static org.openqa.selenium.By.tagName;

public enum Privilege {
    READ, WRITE, EXECUTE;

    public void shoudBeSetTo(PrivilegeValue value) {
        SelenideElement privilegeRow = getPrivilegeRow();

        value.shouldBeCorrect(
                getAllowCheckbox(privilegeRow),
                getDenyCheckbox(privilegeRow)
        );
    }

    private SelenideElement getPrivilegeRow() {
        return privilegesRows().findBy(text(this.name()));
    }

    public void setTo(PrivilegeValue value) {
        SelenideElement privilegeRow = getPrivilegeRow();
        value.setTo(getAllowCheckbox(privilegeRow), getDenyCheckbox(privilegeRow));
    }

    private Checkbox getAllowCheckbox(SelenideElement permissionRow) {
        return new Checkbox(getCheckBoxElementByColumn(permissionRow, 2));
    }

    private Checkbox getDenyCheckbox(SelenideElement permissionRow) {
        return new Checkbox(getCheckBoxElementByColumn(permissionRow, 3));
    }

    private SelenideElement getCheckBoxElementByColumn(SelenideElement permissionRow, int columnNumber) {
        return permissionRow
                    .find(byCssSelector(String.format("td:nth-child(%d)", columnNumber)))
                    .find(byClassName("ant-checkbox-wrapper")).shouldBe(visible)
                    .find(tagName("input")).should(exist);
    }

    public static ElementsCollection privilegesRows() {
        return $(permissionsTable)
                .find(tagName("tbody"))
                .shouldBe(visible)
                .findAll(tagName("tr"));
    }
}
