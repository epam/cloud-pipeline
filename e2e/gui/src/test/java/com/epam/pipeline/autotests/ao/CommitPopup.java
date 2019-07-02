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
import com.epam.pipeline.autotests.utils.Utils;
import org.openqa.selenium.By;

import java.util.Map;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.elementWithText;
import static java.util.concurrent.TimeUnit.SECONDS;

public class CommitPopup extends PopupAO<CommitPopup, ConfirmationPopupAO<LogAO>> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(REGISTRY, context().find(byCssSelector(".commit-pipeline-run-form-image-name-container button:nth-of-type(1)"))),
            entry(GROUP, context().find(byCssSelector(".commit-pipeline-run-form-image-name-container button:nth-of-type(2)"))),
            entry(IMAGE_NAME, context().find(byCssSelector(".ant-form-item-control input:nth-of-type(1)"))),
            entry(DELETE_RUNTIME_FILES, context().find(byText("Delete runtime files")).closest(".ant-checkbox-wrapper")),
            entry(STOP_PIPELINE, context().find(elementWithText(byClassName("ant-checkbox-wrapper"), "Stop pipeline"))),
            entry(CANCEL, context().find(byId("commit-pipeline-run-form-cancel-button"))),
            entry(COMMIT, context().find(byId("commit-pipeline-run-form-commit-button"))),
            entry(VERSION, context().find(byCssSelector(".ant-form-item-control div:nth-of-type(3)")).find(byClassName("ant-select-search__field")))
    );

    public static By stopPipeline() {
        return elementWithText(byClassName("ant-checkbox-wrapper"), "Stop pipeline");
    }

    public static By deleteRuntimeFiles() {
        return PipelineSelectors.Combiners.confine(
                byText("Delete runtime files"),
                byClassName("ant-checkbox-wrapper"),
                "delete run times files checkbox"
        );
    }

    public CommitPopup(final LogAO parentAO) {
        super(new ConfirmationPopupAO<>(parentAO));
    }

    public CommitPopup setRegistry(final String registry) {
        sleep(1, SECONDS);
        if (!get(REGISTRY).has(text(registry))) {
            hover(REGISTRY);
            $(PipelineSelectors.visible(byClassName("selectors__navigation-dropdown-container")))
                    .find(button(registry)).shouldBe(visible).click();
        }
        return this;
    }

    public CommitPopup setGroup(final String group) {
        sleep(1, SECONDS);
        if (!get(GROUP).has(text(group))) {
            hover(GROUP);
            $(PipelineSelectors.visible(byClassName("selectors__navigation-dropdown-container")))
                    .find(button(group)).shouldBe(visible).click();
        }
        return this;
    }

    public CommitPopup setVersion(final String toolVersion) {
        setValue(VERSION, toolVersion);
        click(IMAGE_NAME);
        return this;
    }

    public CommitPopup setName(final String name) {
        Utils.clearTextField(get(IMAGE_NAME));
        sleep(1, SECONDS);
        return addToValue(IMAGE_NAME, name);
    }

    @Override
    public ConfirmationPopupAO<LogAO> cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public ConfirmationPopupAO<LogAO> ok() {
        return click(COMMIT).parent();
    }

    @Override
    public void closeAll() {
        cancel();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

}
