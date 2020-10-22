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
import com.epam.pipeline.autotests.utils.PipelineSelectors;
import java.util.Map;
import java.util.Optional;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.FOLDERS;
import static com.epam.pipeline.autotests.ao.Primitive.OK;
import static com.epam.pipeline.autotests.ao.Primitive.TREE;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.folderWithName;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.modalWithTitle;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.pipelineWithName;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.version;

public class PipelineSelection extends PopupAO<PipelineSelection, Configuration> {

    private String pipeline;
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(TREE, context().find(byClassName("Pane1"))),
            entry(FOLDERS, context().find(byClassName("Pane2"))),
            entry(CANCEL, context().find(button("Cancel"))),
            entry(OK, context().find(button("OK")))
    );

    public PipelineSelection(final Configuration parentAO) {
        super(parentAO);
    }

    public PipelineSelection selectPipeline(final String pipeline) {
        this.pipeline = pipeline;
        context().find(pipelineWithName(pipeline, "browser__tree-item-title")).shouldBe(visible).click();
        return this;
    }

    public PipelineSelection selectFirstVersion() {
        new PipelineLibraryContentAO(Optional.ofNullable(pipeline)
                .orElseThrow(() -> new RuntimeException("Pipeline wasn't selected in pipeline selection popup."))
        ).firstVersion();
        return this;
    }

    public PipelineSelection selectConfiguration(final String pipelineConfiguration) {
        context().find(version()).find(byClassName("ant-select")).shouldBe(visible).click();
        $(PipelineSelectors.visible(byClassName("ant-select-dropdown-menu")))
                .find(byText(pipelineConfiguration)).shouldBe(visible).click();
        return this;
    }

    public PipelineSelection selectFolder(final String folder) {
        context().find(folderWithName(folder)).shouldBe(visible).click();
        return this;
    }

    @Override
    public Configuration cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public Configuration ok() {
        return ensure(OK, enabled).click(OK).parent();
    }

    @Override
    public SelenideElement context() {
        return $(modalWithTitle("Select pipeline"));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
