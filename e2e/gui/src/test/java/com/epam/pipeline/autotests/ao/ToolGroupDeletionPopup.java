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
import com.epam.pipeline.autotests.utils.Utils;

import java.util.Map;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static org.openqa.selenium.By.id;

public class ToolGroupDeletionPopup extends PopupAO<ToolGroupDeletionPopup, Registry> {
    private final Map<Primitive,SelenideElement> elements = initialiseElements(
            entry(DELETE, context().find(id("confirm-remove-group"))),
            entry(CANCEL, context().find(id("cancel-remove-group")))
    );

    public ToolGroupDeletionPopup(final Registry registry) {
        super(registry);
    }

    @Override
    public Registry cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public Registry ok() {
        return click(DELETE).parent();
    }

    @Override
    public SelenideElement context() {
        return Utils.getPopupByTitle("Are you sure you want to delete");
    }

    public ToolGroupDeletionPopup ensureGroupNameIs(final String expectedGroupName) {
        context().find(byText(String.format("Are you sure you want to delete '%s'?", expectedGroupName)))
                .shouldBe(visible);
        return this;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
