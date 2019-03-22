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
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.GROUP;
import static com.epam.pipeline.autotests.ao.Primitive.GROUPS_LIST;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.REGISTRIES_LIST;
import static com.epam.pipeline.autotests.ao.Primitive.REGISTRY;
import static com.epam.pipeline.autotests.ao.Primitive.SEARCH;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.tagName;

public class DockerImageSelection implements AccessObject<DockerImageSelection> {

    private final SelenideElement context = $(PipelineSelectors.modalWithTitle("Select docker image"));
    private final Map<Primitive, SelenideElement> elements;
    private final AccessObject<?> parent;

    public DockerImageSelection(final AccessObject<?> parent) {
        this.parent = parent;
        this.elements = initialiseElements(parent.elements(),
                entry(REGISTRY, context().find(byCssSelector(".ant-modal-body button:nth-of-type(1)"))),
                entry(REGISTRIES_LIST, $(visible(byClassName("ant-dropdown")))),
                entry(GROUP, context().find(byCssSelector(".ant-modal-body button:nth-of-type(2)"))),
                entry(GROUPS_LIST, $(visible(byClassName("ant-dropdown")))),
                entry(SEARCH, context().find(byClassName("ant-input-search")).find(tagName("input"))),
                entry(OK, context.find(button("OK"))),
                entry(CANCEL, context.find(button("Cancel")))
        );
    }

    public DockerImageSelection selectRegistry(final String registry) {
        sleep(1, SECONDS);
        if (get(REGISTRY).has(not(text(registry)))) {
            hover(REGISTRY);
            get(REGISTRIES_LIST).find(button(registry)).shouldBe(visible).click();
        }
        return this;
    }

    public DockerImageSelection selectGroup(final String group) {
        sleep(1, SECONDS);
        if (get(GROUP).has(not(text(group)))) {
            hover(GROUP);
            get(GROUPS_LIST).find(button(group)).shouldBe(visible).click();
        }
        return this;
    }

    public DockerImageSelection selectTool(final String tool) {
        return selectTool(tool, "latest");
    }

    public DockerImageSelection selectTool(final String tool, final String version) {
        setValue(SEARCH, tool);
        final SelenideElement toolRow = context().find(byClassName("ant-table-tbody")).find(withText(tool)).closest(".ant-table-row");
        toolRow.shouldBe(visible).click();
        selectVersion(toolRow, version);
        return this;
    }

    private DockerImageSelection selectVersion(final SelenideElement tool, final String version) {
        sleep(1, SECONDS);
        final SelenideElement versionSelection = tool.find(byClassName("ant-select"));
        if (versionSelection.has(not(exactText(version)))) {
            versionSelection.shouldBe(visible).click();
            $(visible(byClassName("ant-select-dropdown"))).find(byText(version)).shouldBe(visible).click();
        }
        return this;
    }

    @Override
    public SelenideElement context() {
        return context;
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

}
