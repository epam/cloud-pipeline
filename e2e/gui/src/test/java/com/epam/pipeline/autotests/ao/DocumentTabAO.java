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
import org.openqa.selenium.By;

import java.nio.file.Path;
import java.util.Map;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static java.util.concurrent.TimeUnit.SECONDS;

public class DocumentTabAO extends AbstractPipelineTabAO<DocumentTabAO> {
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            super.elements(),
            // Upload must be like that because of its input structure
            entry(UPLOAD, $(byXpath("//*[contains(@class, 'ant-upload') and @role = 'button']"))),
            entry(RENAME, $(button("Rename"))),
            entry(DELETE, $(button("Delete"))),
            entry(DOWNLOAD, $(button("Download")))
    );

    public DocumentTabAO(String pipelineName) {
        super(pipelineName);
    }

    @Override
    protected DocumentTabAO open() {
        changeTabTo(DOCUMENTS_TAB);
        return this;
    }

    public DocumentTabAO validateDocumentsNotEditable() {
        return sleep(1, SECONDS)
                .ensure(UPLOAD, not(exist))
                .ensure(RENAME, not(exist))
                .ensure(DELETE, not(exist));
    }

    public DocumentTabAO renameFile(String newName) {
        return openDocumentRenamingDialog()
                .typeInField(newName)
                .ok()
                .shouldContainDocument(newName);
    }

    public DocumentRenamingPopupAO openDocumentRenamingDialog() {
        click(RENAME);
        return new DocumentRenamingPopupAO();
    }

    public DocumentTabAO download() {
        return click(DOWNLOAD);
    }

    public DocumentTabAO delete() {
        click(DELETE);
        $(button("OK")).shouldBe(visible).click();
        return this;
    }

    public DocumentTabAO shouldContainNoDocuments() {
        return ensure(byText("No documents attached. Start with uploading new README.md file"), visible);
    }

    public DocumentTabAO shouldContainDocument(String name) {
        $(".ant-table-tbody").findAll("td").get(0).shouldHave(text(name));
        return this;
    }

    public DocumentTabAO upload(final Path path) {
        get(UPLOAD).shouldBe(visible).find(By.tagName("input")).uploadFile(path.toFile());
        return this;
    }

    public static By fileWithName(final String filename) {
        return By.xpath(String.format(
                "//tr[.//*[contains(@class, 'pipeline-documents__document-name') and text() = '%s']]",
                filename
        ));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public String getVersion() {
        String fullText = $(byClassName("browser__item-header")).text();
        return fullText.substring(fullText.lastIndexOf("-") + 1, fullText.lastIndexOf(")"));
    }

    public class DocumentRenamingPopupAO extends PopupWithStringFieldAO<DocumentRenamingPopupAO, DocumentTabAO> {

        public DocumentRenamingPopupAO() {
            super(DocumentTabAO.this);
        }

        @Override
        public DocumentRenamingPopupAO typeInField(String value) {
            $(byId("name")).setValue(value);
            return this;
        }
    }
}
