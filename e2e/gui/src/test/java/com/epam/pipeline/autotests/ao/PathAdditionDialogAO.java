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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.Utils;

import java.util.Map;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
import static org.openqa.selenium.By.tagName;

public class PathAdditionDialogAO extends PopupAO<PathAdditionDialogAO, RunParameterAO> {

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(OK, buttons().findBy(text("OK"))),
            entry(CANCEL, buttons().findBy(text("Cancel"))),
            entry(BUCKET_PANEL, context().find(byClassName("Pane1"))),
            entry(FILES_PANEL, context().find(byClassName("Pane2")))
    );

    public PathAdditionDialogAO(RunParameterAO parentAO) {
        super(parentAO);
    }

    public PathAdditionDialogAO chooseStorage(String storage) {
        get(BUCKET_PANEL).find(byText(storage)).shouldBe(visible).click();
        return this;
    }

    public PathAdditionDialogAO addToSelection(String folder) {
        get(FILES_PANEL).find(byText(folder)).closest("tr")
                .find(byClassName("ant-checkbox-wrapper")).shouldBe(visible).click();
        return this;
    }

    public PathAdditionDialogAO openFolder(String folderName) {
        switcher(folderName)
                .shouldBe(visible)
                .click();
        return this;
    }

    public PathAdditionDialogAO closeFolder(String folderName) {
        $(byText(folderName)).closest("li")
                .find(byCssSelector(".ant-tree-switcher.ant-tree-switcher_open")).shouldBe(visible).click();
        return this;
    }

    public static SelenideElement switcher(String folderName) {
        return $(visible(byText(folderName))).closest("li")
                .find(byCssSelector(".ant-tree-switcher.ant-tree-switcher_close"));
    }

    private ElementsCollection buttons() {
        return context().findAll(tagName("button"));
    }

    @Override
    public RunParameterAO ok() {
        return click(OK).parent();
    }

    @Override
    public RunParameterAO cancel() {
        return click(CANCEL).parent();
    }

    @Override
    public SelenideElement context() {
        return Utils.getPopupByTitle("Select folder or file");
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
