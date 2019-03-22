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
import java.util.Map;

import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;

abstract public class DetachedConfigurationPopup extends PopupAO<DetachedConfigurationPopup, Configuration> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(NAME, $(PipelineSelectors.visible(byId("name")))),
            entry(DESCRIPTION, $(PipelineSelectors.visible(byId("description")))),
            entry(CANCEL, $(byId("edit-configuration-form-cancel-button"))),
            entry(TEMPLATE, $(PipelineSelectors.visible(byClassName("ant-select-selection__rendered"))))
    );

    public DetachedConfigurationPopup(final Configuration parent) {
        super(parent);
    }

    public DetachedConfigurationPopup setName(final String configurationName) {
        return setValue(NAME, configurationName);
    }

    public DetachedConfigurationPopup setDescription(final String description) {
        return setValue(DESCRIPTION, description);
    }

    public DetachedConfigurationPopup setTemplate(final String template) {
        return selectValue(TEMPLATE, template);
    }

    @Override
    public Configuration cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
