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
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;

public class RegistryEditionPopup extends RegistryPopup {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(super.elements(),
            entry(SAVE, context().find(button("SAVE")))
    );

    public ConfirmationPopupAO<RegistryEditionPopup> deleteRegistry() {
        click(DELETE);
        return new ConfirmationPopupAO<>(this);
    }

    @Override
    public ToolsPage ok() {
        return click(SAVE).parent();
    }

    @Override
    public SelenideElement context() {
        return Utils.getPopupByTitle("Edit registry");
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
