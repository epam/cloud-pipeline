/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.autotests.AbstractSeveralPipelineRunningTest;
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;

import java.util.function.Consumer;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.Combiners.confine;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.ignoreScope;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.modalWithTitle;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;

public class MetadataSelection
        implements AccessObject<MetadataSelection> {

    public static By tree = byClassName("Pane1");
    public static By header = byClassName("browser__item-header");
    public static By folders = byClassName("Pane2");
    public static By defineExpression = confine(
            byText("Define expression"),
            byClassName("ant-row"),
            "Define expression row"
    );
    public static By ok = button("OK");
    public static By cancel = button("Cancel");
    public static By clearSelection = button("Clear selection");

    public MetadataSelection cd(final String folder) {
        context().find(tree).find(byText(folder)).shouldBe(visible).click();
        return this;
    }

    public MetadataSelection samples(Consumer<MetadataSamplesAO> action) {
        action.accept(new MetadataSamplesAO());
        return this;
    }

    @Override
    public SelenideElement context() {
        return $(modalWithTitle("Select metadata"));
    }

    public RunsMenuAO launch(final AbstractSeveralPipelineRunningTest test,
                             final String pipelineName) {
        context().find(withText("OK")).closest("button").shouldBe(visible).click();
        click(ignoreScope(visible(button("OK"))), RunsMenuAO::new);

        test.addRunId(Utils.getPipelineRunId(pipelineName));
        return new RunsMenuAO();
    }
}
