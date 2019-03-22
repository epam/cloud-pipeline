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
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import com.epam.pipeline.autotests.utils.Utils;
import java.util.Map;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.DESCRIPTION;
import static com.epam.pipeline.autotests.ao.Primitive.INFO;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.PERMISSIONS;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;

public class ToolGroupEditionPopup extends PopupAO<ToolGroupEditionPopup, ToolGroup> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(SAVE, context().find(PipelineSelectors.button("SAVE"))),
            entry(CANCEL, context().find(PipelineSelectors.button("CANCEL"))),
            entry(NAME, context().find(byId("name"))),
            entry(DESCRIPTION, context().find(byId("description"))),
            entry(INFO, context().find(byClassName("ant-tabs-nav")).find(byText("Info"))),
            entry(PERMISSIONS, context().find(byClassName("ant-tabs-nav")).find(byText("Permissions")))
    );

    public ToolGroupEditionPopup(final ToolGroup toolGroup) {
        super(toolGroup);
    }

    public PermissionTabAO permissions() {
        click(PERMISSIONS);
        return new PermissionTabAO(this);
    }

    public ToolGroupEditionPopup addDescription(final String description) {
        return setValue(DESCRIPTION, description);
    }

    @Override
    public ToolGroup cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public ToolGroup ok() {
        return click(SAVE).parent();
    }

    @Override
    public SelenideElement context() {
        return Utils.getPopupByTitle("Edit group");
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
