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
import org.openqa.selenium.By;

import java.util.Collections;
import java.util.Map;

import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selectors.withText;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.*;

public class NodePage implements AccessObject<NodePage> {
    public static By mainInfo() {
        return By.className("cluster__node-main-info");
    }

    public static By labelWithText(final String text) {
        return Combiners.confine(withText(text), mainInfo(), "label with text " + text);
    }

    public static By labelWithType(final String type) {
        return Combiners.confine(byAttribute("label", type), mainInfo(), "label with type " + type);
    }

    public static By section(final String title) {
        final String sectionClass = "ant-table";
        final String titleClass = "ant-table-title";
        return byXpath(String.format(
            ".//*[contains(concat(' ', @class, ' '), ' %s ') and .//*[@class = '%s' and .//*[text() = '%s']]]",
            sectionClass, titleClass, title
        ));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return Collections.emptyMap();
    }
}
