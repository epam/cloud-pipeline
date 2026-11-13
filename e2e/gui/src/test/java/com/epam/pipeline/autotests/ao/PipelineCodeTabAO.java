/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.codeborne.selenide.WebDriverRunner;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.ConfigurationProfile;
import com.epam.pipeline.autotests.utils.Json;
import com.epam.pipeline.autotests.utils.Utils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.interactions.Actions;
import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.CollectionCondition.sizeGreaterThan;
import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.$x;
import static com.codeborne.selenide.Selenide.actions;
import static com.epam.pipeline.autotests.ao.Primitive.CLOSE;
import static com.epam.pipeline.autotests.ao.Primitive.CODE_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE_FOLDER;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE;
import static com.epam.pipeline.autotests.ao.Primitive.EDIT;
import static com.epam.pipeline.autotests.ao.Primitive.NEW_FILE;
import static com.epam.pipeline.autotests.ao.Primitive.RENAME;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.UPLOAD;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.buttonByIconClass;
import static java.lang.String.format;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.tagName;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PipelineCodeTabAO extends AbstractPipelineTabAO<PipelineCodeTabAO> {

    private static final int EDITOR_CLEAR_ATTEMPTS = 3;

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            super.elements(),
            entry(RENAME, $$(tagName("button")).findBy(text("Rename"))),
            entry(DELETE, $$(tagName("button")).findBy(text("Delete"))),
            entry(NEW_FILE, $$(tagName("button")).findBy(text("NEW FILE"))),
            entry(UPLOAD, $$(tagName("button")).findBy(text("UPLOAD"))),
            entry(CREATE_FOLDER, $(buttonByIconClass("anticon-plus")))
    );

    public PipelineCodeTabAO(String pipelineName) {
        super(pipelineName);
    }

    @Override
    protected PipelineCodeTabAO open() {
        changeTabTo(CODE_TAB);
        return this;
    }

    public PipelineCodeTabAO clearAndFillPipelineFile(String fileName, String newText) {
        $(byText(fileName)).click();

        //Click Edit
        $$(".pipeline-code-form__button").findBy(text("Edit")).click();

        sleep(500, MILLISECONDS);
        clearEditor();

        Utils.clickAndSendKeysWithSlashes($(byClassName("CodeMirror-line")), newText);

        $$(".pipeline-code-form__button").findBy(text("Save")).click();
        $("#message").setValue("test commit message");
        final String versionUrl = WebDriverRunner.url();
        $$("button").findBy(text("Commit")).click();
        waitForNewVersionPage(versionUrl);

        return this;
    }

    /**
     * Deletes the whole text of the opened file editor. Right after "Edit" the editor can still redraw its lines, so the
     * line the clearing starts from can be replaced before the key presses reach it (StaleElementReferenceException
     * from the actions, seen in EPMCMBIBPC-289 and 378). The clearing then starts again from the
     * redrawn line; deleting from an editor that is already partly empty does no harm.
     */
    private static void clearEditor() {
        for (int attempt = 1; ; attempt++) {
            try {
                final Actions action = actions().moveToElement($(byClassName("CodeMirror-line"))).click();
                for (int i = 0; i < 1000; i++) {
                    action.sendKeys("\b").sendKeys(Keys.DELETE);
                }
                action.perform();
                return;
            } catch (StaleElementReferenceException e) {
                if (attempt == EDITOR_CLEAR_ATTEMPTS) {
                    throw e;
                }
                Utils.sleep(500, MILLISECONDS);
            }
        }
    }

    /**
     * After a commit the page reloads the pipeline and then opens its new version (the URL changes): an action
     * made before that, e.g. a click on RUN, is undone by this navigation. Waits for it as a user waits for the
     * page; if the URL does not change (e.g. the commit has failed), goes on after the default timeout.
     */
    private static void waitForNewVersionPage(final String versionUrl) {
        final long deadline = System.currentTimeMillis() + C.DEFAULT_TIMEOUT;
        while (versionUrl.equals(WebDriverRunner.url()) && System.currentTimeMillis() < deadline) {
            Utils.sleep(500, MILLISECONDS);
        }
    }

    public PipelineCodeTabAO uploadFile(File file) {
        sleep(5, SECONDS);
        ensure(UPLOAD, visible);
        $(byClassName("ant-upload-select")).find(tagName("input")).should(exist).uploadFile(file);
        return this;
    }

    public PipelineCodeTabAO assertThereIsNoEditButtons() {
        // the Rename and Delete buttons are rendered with the rows, so the absence is checked on a loaded table
        $$(".ant-table-tbody tr").shouldHave(sizeGreaterThan(0));
        return ensure(CREATE_FOLDER, not(exist))
                .ensure(NEW_FILE, not(exist))
                .ensure(UPLOAD, not(exist))
                .ensure(RENAME, not(exist))
                .ensure(DELETE, not(exist));
    }

    public PipelineCodeTabAO assertFileNotEditable(String file) {
        return clickOnFile(file)
                .ensure(CLOSE, visible)
                .assertFileIsNotEditable()
                .close();
    }

    public EditFilePopupAO clickOnRenameFileButton(String filename) {
        $$(tagName("tr"))
                .find(text(filename))
                .find(button("Rename"))
                .click();

        return new EditFilePopupAO();
    }

    public PipelineCodeTabAO waitUntilRenameButtonAppears(String filename) {
        $$(tagName("tr"))
                .find(text(filename))
                .find(button("Rename"))
                .shouldBe(exist);
        return this;
    }

    public FileEditingPopupAO clickOnFile(String fileName) {
        $(withText(fileName)).shouldBe(visible).click();
        return new FileEditingPopupAO(this);
    }

    public PipelineCodeTabAO shouldContainElement(String folderName) {
        $(".ant-table-tbody").findAll("tr")
                .shouldHave(size(3))
                .get(0).shouldHave(text(folderName));
        return this;
    }

    public PipelineCodeTabAO clickOnFolder(String folderName) {
        $(byText(folderName)).shouldBe(visible).click();
        return this;
    }

    public PipelineCodeTabAO createFile(String fileName) {
        return openCreateFileDialog()
                .typeInField(fileName)
                .ok();
    }

    public PipelineCodeTabAO createFolder(String name) {
        return openCreateFolderDialog()
                .typeInField(name)
                .ok();
    }

    public EditFilePopupAO openCreateFolderDialog() {
        click(CREATE_FOLDER);
        return new EditFilePopupAO();
    }

    public EditFilePopupAO openCreateFileDialog() {
        click(NEW_FILE);
        return new EditFilePopupAO();
    }

    public PipelineCodeTabAO deleteFile(final String fileName) {
        $$(tagName("tr"))
                .find(text(fileName))
                .find(button("Delete"))
                .click();
        click(button("OK"));
        return this;
    }

    public PipelineCodeTabAO shouldContainFile(final String fileName) {
        $(".ant-table-tbody").shouldHave(text(fileName));
        return this;
    }

    public List<String> fileNames() {
        return $(".ant-table-tbody").findAll("tr").shouldHave(sizeGreaterThan(0)).stream()
                .map(row -> row.findAll("td").get(1).text())
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public static class FileEditingPopupAO extends PopupAO<FileEditingPopupAO, PipelineCodeTabAO> {
        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(EDIT, $$(className("pipeline-code-form__button")).findBy(exactText("EDIT"))),
                entry(SAVE, $$(className("pipeline-code-form__button")).findBy(exactText("SAVE"))),
                entry(CLOSE, $$(className("pipeline-code-form__button")).findBy(exactText("CLOSE")))
        );
        private final PipelineCodeTabAO parentAO;

        public FileEditingPopupAO(PipelineCodeTabAO parentAO) {
            super(parentAO);
            this.parentAO = parentAO;
        }

        @Override
        public SelenideElement context() {
            return $$(className("ant-modal-content")).findBy(visible);
        }

        public FileEditingPopupAO assertFileIsNotEditable() {
            return ensure(EDIT, not(visible));
        }

        public FileEditingPopupAO assertFileIsNotEmpty() {
            $(byClassName("CodeMirror-code")).shouldBe(visible);
            sleep(1, SECONDS);
            List<String> linesList = $$(byClassName("CodeMirror-line")).texts();
            assertFalse(linesList.isEmpty());
            return this;
        }

        private FileEditingPopupAO fillWith(String newText) {
            Utils.clickAndSendKeysWithSlashes($(byClassName("CodeMirror-line")), newText);
            return this;
        }

        public PipelineCodeTabAO saveAndCommitWithMessage(String message) {
            final String versionUrl = WebDriverRunner.url();
            final PipelineCodeTabAO codeTab = openCommitDialog().typeInField(message).ok();
            waitForNewVersionPage(versionUrl);
            return codeTab;
        }

        private CommitPopupAO<PipelineCodeTabAO> openCommitDialog() {
            click(SAVE);
            return new CommitPopupAO<>(parentAO);
        }

        private FileEditingPopupAO clear() {
            final SelenideElement editor = $(byClassName("CodeMirror-code"));
            final int codeLength = editor.innerText().length();
            final SelenideElement mirrorLine = editor.find(byClassName("CodeMirror-line")).shouldBe(visible);
            mirrorLine.click();
            for (int i = 0; i < codeLength; i++) {
                 actions().sendKeys(Keys.BACK_SPACE, Keys.DELETE).perform();
            }
            return this;
        }

        private FileEditingPopupAO clickEdit() {
            return click(EDIT);
        }

        public FileEditingPopupAO editFile(final UnaryOperator<String> action) {
            clickEdit();
            final SelenideElement editor = $(className("code-editor__editor")).should(appear);
            sleep(1, SECONDS);
            editor.click();
            final List<String> lines = $(className("CodeMirror")).findAll(className("CodeMirror-line")).texts();
            final String code = String.join("", lines);
            final String edited = action.apply(code);
            if (code.equals(edited)) {
                return this;
            }
            clear()
                    .fillWith(edited)
                    .deleteExtraBrackets(100);
            return this;
        }

        public FileEditingPopupAO deleteExtraBrackets(int num) {
            deleteExtraBrackets($(byClassName("CodeMirror-line")), num);
            return this;
        }

        public FileEditingPopupAO shouldContainInCode(final String expectedCode) {
            final Function<String, SelenideElement> lineWithText =
                    text -> $x(format(".//pre[contains(@class, 'CodeMirror-line') and contains(., '%s')]", text));
            Arrays.stream(expectedCode.split("\n"))
                    .map(String::trim)
                    .map(lineWithText)
                    .forEach(el -> el.should(exist));
            return this;
        }

        public FileEditingPopupAO shouldContainCodeLineWith(final String... parts) {
            final String partsCondition = Arrays.stream(parts)
                    .map(part -> format("contains(., '%s')", part))
                    .collect(Collectors.joining(" and "));
            $x(format(".//pre[contains(@class, 'CodeMirror-line') and %s]", partsCondition)).should(exist);
            return this;
        }

        public FileEditingPopupAO profileShouldHave(final String profileName,
                                                    final Consumer<ConfigurationProfile> check) {
            $(className("CodeMirror")).find(className("CodeMirror-line")).should(exist);
            final List<String> lines = $(className("CodeMirror")).findAll(className("CodeMirror-line")).texts();
            final ConfigurationProfile[] profiles = Json.stringToProfiles(String.join("", lines));
            check.accept(Json.selectProfileWithName(profileName, profiles));
            return this;
        }

        /**
         * Parses config.json from the editor, so the check fails while the editor has no content yet.
         */
        public FileEditingPopupAO profileShouldNotExist(final String profileName) {
            $(className("CodeMirror")).find(className("CodeMirror-line")).should(exist);
            final List<String> lines = $(className("CodeMirror")).findAll(className("CodeMirror-line")).texts();
            final ConfigurationProfile[] profiles = Json.stringToProfiles(String.join("", lines));
            assertTrue(profiles.length > 0, "config.json has no configuration profiles.");
            assertFalse(Arrays.stream(profiles).anyMatch(profile -> profileName.equals(profile.name)),
                    String.format("Configuration profile %s is still in config.json.", profileName));
            return this;
        }

        public PipelineCodeTabAO close() {
            return click(CLOSE).parent();
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }

    }

    public class EditFilePopupAO extends PopupWithStringFieldAO<EditFilePopupAO, PipelineCodeTabAO> {

        public EditFilePopupAO() {
            super(PipelineCodeTabAO.this);
        }

        @Override
        public EditFilePopupAO typeInField(String value) {
            sleep(1, SECONDS);
            $$(byClassName("ant-modal-content")).findBy(visible).find(byId("name")).setValue(value);
            return this;
        }
    }
}
