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

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE_PIPELINE;

public enum Template {

    DEFAULT("DEFAULT"),
    PYTHON("PYTHON"),
    SHELL("SHELL"),
    WDL("WDL"),
    LUIGI("LUIGI"),
    SAMPLE_SHEET_BATCH("SAMPLE_SHEET_BATCH"),
    FOLDER_BATCH("FOLDER_BATCH");

    private final String text;

    Template(String text) {
        this.text = text;
    }

    public CreatePipelinePopupAO clickOnTemplate() {
        new PipelinesLibraryAO().resetMouse().hover(CREATE).hover(CREATE_PIPELINE);
        $(byText(text)).shouldBe(visible).click();
        return new CreatePipelinePopupAO();
    }

    public PipelinesLibraryAO createPipeline(String pipelineName) {
        return clickOnTemplate()
                .setName(pipelineName)
                .ok();
    }
}
