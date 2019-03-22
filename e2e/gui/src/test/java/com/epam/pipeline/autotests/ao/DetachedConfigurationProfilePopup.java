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
import java.util.Map;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.ao.Primitive.TEMPLATE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.comboboxOf;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.fieldWithLabel;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.modalWithTitle;

public class DetachedConfigurationProfilePopup extends DetachedConfigurationPopup {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(super.elements(),
            entry(CREATE, context().find(byId("create-pipeline-configuration-form-create-button"))),
            entry(CANCEL, context().find(byId("create-pipeline-configuration-form-cancel-button"))),
            entry(TEMPLATE, context().find(comboboxOf(fieldWithLabel("Template"))))
    );

    public DetachedConfigurationProfilePopup(Configuration parent) {
        super(parent);
    }

    @Override
    public Configuration ok() {
        return click(CREATE).parent();
    }

    @Override
    public SelenideElement context() {
        return $(modalWithTitle("Create configuration"));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

}
