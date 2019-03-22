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
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.modalWithTitle;

public class DetachedConfigurationEditionPopup extends DetachedConfigurationPopup {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(super.elements(),
            entry(SAVE, context().find(byId("edit-configuration-form-save-button"))),
            entry(DELETE, context().find(byId("edit-configuration-form-delete-button")))
    );

    public DetachedConfigurationEditionPopup(final Configuration configuration) {
        super(configuration);
    }

    public Configuration delete() {
        click(DELETE);
        $(modalWithTitle("Are you sure you want to delete configuration?"))
                .find(byId("edit-configuration-delete-dialog-delete-button"))
                .shouldBe(visible).click();
        return parent();
    }

    @Override
    public Configuration ok() {
        return click(SAVE).parent();
    }

    @Override
    public SelenideElement context() {
        return $(modalWithTitle("Edit configuration info"));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
