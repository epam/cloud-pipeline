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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.modalWithTitle;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;

public class DetachedConfigurationCreationPopup extends DetachedConfigurationPopup {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(super.elements(),
            entry(CREATE, context().find(byId("edit-configuration-form-create-button")))
    );

    public DetachedConfigurationCreationPopup(final Configuration parent) {
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

    static public By templatesList() {
        return new By() {
            @Override
            public List<WebElement> findElements(final SearchContext context) {
                return Collections.singletonList($(visible(byClassName("ant-select-dropdown"))));
            }

            @Override
            public String toString() {
                return "available templates";
            }
        };
    }
}
