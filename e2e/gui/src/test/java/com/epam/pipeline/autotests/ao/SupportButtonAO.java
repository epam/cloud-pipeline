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
import com.epam.pipeline.autotests.utils.SupportButton;

import java.util.NoSuchElementException;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static java.lang.String.format;

public class SupportButtonAO implements AccessObject<SupportButtonAO>  {

    public NavigationMenuAO checkSupportButtonIcon(final SupportButton.Icon icon, final Condition iconCondition) {
        $(byId(format("navigation-button-support-%s", icon.getName()))).shouldBe(exist);
        $$(byId(format("navigation-button-support-%s", icon.getName()))).stream()
                .filter(i -> i.$(":nth-child(1)").has(iconCondition))
                .findAny()
                .orElseThrow(() -> new NoSuchElementException(format(
                "Support button with %s condition was not found.", iconCondition
        )));
        return new NavigationMenuAO();
    }

    public SupportButtonAO checkSupportButtonContent(final SupportButton.Icon icon, final Condition condition) {
        final SelenideElement selenideElement = $$(byId(format("navigation-button-support-%s", icon.getName()))).stream()
                .filter(i -> i.$(":nth-child(1)").has(condition))
                .findFirst().orElseThrow(() -> new NoSuchElementException(format(
                        "Support button with %s condition was not found.", condition
                )));
        selenideElement.should(visible).click();
        $$(byClassName("ant-popover-content"))
                .filter(appear)
                .first()
                .shouldHave(text(icon.getContent()));
        return this;
    }
}
