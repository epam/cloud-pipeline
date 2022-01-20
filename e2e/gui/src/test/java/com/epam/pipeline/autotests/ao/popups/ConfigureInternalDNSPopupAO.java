/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests.ao.popups;

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.PipelineRunFormAO;
import com.epam.pipeline.autotests.ao.PopupAO;
import com.epam.pipeline.autotests.ao.Primitive;
import com.epam.pipeline.autotests.utils.Utils;

import java.util.Map;

import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.ao.Primitive.PORT;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.SERVICE_NAME;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;

public class ConfigureInternalDNSPopupAO extends PopupAO<ConfigureInternalDNSPopupAO, PipelineRunFormAO> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(SERVICE_NAME, context().find(byText("Service name:")).parent()
                    .find(".hosted-app-configuration__input")),
            entry(PORT, context().find(byClassName("hosted-app-configuration__port")).parent()
                    .find(".ant-input-number-input")),
            entry(SAVE, context().find(button("Save")))
    );

    public ConfigureInternalDNSPopupAO(PipelineRunFormAO parentAO) {
        super(parentAO);
    }

    public ConfigureInternalDNSPopupAO setServiceName(final String serviceName) {
        setValue(SERVICE_NAME, serviceName);
        return this;
    }

    public ConfigureInternalDNSPopupAO setPort(final String port) {
        setValue(PORT, port);
        return this;
    }

    public PipelineRunFormAO save() {
        return click(SAVE).parent();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    @Override
    public SelenideElement context() {
        return Utils.getPopupByTitle("Configure internal DNS");
    }
}
