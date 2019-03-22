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

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Condition.*;

public class Checkbox {
    private final SelenideElement checkboxElement;

    public Checkbox (SelenideElement checkboxElement) {
        this.checkboxElement = checkboxElement;
    }

    public void switchOn() {
        if(checkboxElement.should(exist).is(not(checked))) {
            getLabel().shouldBe(visible).click();
        }
    }

    public void switchOff() {
        if (checkboxElement.should(exist).is(checked)) {
            getLabel().shouldBe(visible).click();
        }
    }

    private SelenideElement getLabel() {
        return checkboxElement.closest(".ant-checkbox-wrapper");
    }

    public void shouldBeChecked() {
        checkboxElement.should(exist).shouldBe(checked);
    }

    public void shouldNotBeChecked() {
        checkboxElement.should(exist).shouldNotBe(checked);
    }
}
