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
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.ao.Primitive.DESCRIPTION;
import static com.epam.pipeline.autotests.ao.Primitive.INFO_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;

public class ToolGroupAdditionPopup extends PopupAO<ToolGroupAdditionPopup, Registry> {
    private final Map<Primitive,SelenideElement> elements = initialiseElements(
            entry(INFO_TAB, context().find(byClassName("ant-tabs-nav")).find(byText("Info"))),
            entry(CREATE, context().find(button("CREATE"))),
            entry(CANCEL, context().find(button("CANCEL"))),
            entry(NAME, context().find(byId("name"))),
            entry(DESCRIPTION, context().find(byId("description")))
    );

    public ToolGroupAdditionPopup(final Registry registry) {
        super(registry);
    }

    public ToolGroupAdditionPopup addName(final String name) {
        return setValue(NAME, name);
    }

    public ToolGroupAdditionPopup addDescription(final String description) {
        return setValue(DESCRIPTION, description);
    }

    @Override
    public Registry cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public Registry ok() {
        return click(CREATE).parent();
    }

    @Override
    public SelenideElement context() {
        return Utils.getPopupByTitle("Create group");
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public ToolGroupAdditionPopup checkForText(String text) {
        context().find(byText(text)).shouldBe(visible);
        return this;
    }
}
