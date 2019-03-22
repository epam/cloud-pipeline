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
import static com.codeborne.selenide.Selectors.byId;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.ENABLE;
import static com.epam.pipeline.autotests.ao.Primitive.IMAGE;

public class ToolEnablePopup extends PopupAO<ToolEnablePopup, ToolGroup> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(IMAGE, context().find(byId("image"))),
            entry(ENABLE, context().find(byId("enable-tool-form-enable-button"))),
            entry(CANCEL, context().find(byId("enable-tool-form-cancel-button")))
    );

    public ToolEnablePopup(final ToolGroup parentAO) {
        super(parentAO);
    }

    public ToolEnablePopup name(final String toolName) {
        return setValue(IMAGE, toolName);
    }

    @Override
    public ToolGroup ok() {
        return click(ENABLE).parent();
    }

    @Override
    public ToolGroup cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public SelenideElement context() {
        return Utils.getPopupByTitle("Enable tool");
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
