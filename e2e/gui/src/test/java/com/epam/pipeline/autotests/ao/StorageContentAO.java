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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.google.common.collect.Comparators;
import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.openqa.selenium.By;
import org.testng.Assert;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byCssSelector;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selectors.withText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.ao.Primitive.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.buttonByIconClass;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.deleteButton;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.editButton;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.modalWithTitle;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.visible;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.By.className;
import static org.openqa.selenium.By.tagName;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class StorageContentAO implements AccessObject<StorageContentAO> {

    private static final Duration UPLOAD_FINISH_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration LIST_LOADED_FOR = Duration.ofSeconds(2);

    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(REFRESH, context().find(byId("refresh-storage-button"))),
            entry(EDIT_STORAGE, context().find(byId("edit-storage-button"))),
            entry(UPLOAD, context().find(byId("upload-button"))),
            entry(SELECT_ALL, context().find(byId("select-all-button"))),
            entry(CREATE, context().find(byId("create-button"))),
            entry(CREATE_FOLDER, context().find(byClassName("create-folder-button"))),
            entry(CREATE_FILE, context().find(byClassName("create-file-button"))),
            entry(ADDRESS_BAR, context().find(byClassName("ant-breadcrumb"))),
            entry(CLEAR_SELECTION, context().find(selectionAction("clear"))),
            entry(REMOVE_ALL, context().find(selectionAction("remove-all"))),
            entry(DESCRIPTION, context().find(byCssSelector(".browser__data-storage-info-container div:first-child"))),
            entry(NAVIGATION, context().find(byClassName("data-storage-navigation__path-components-container"))),
            entry(STORAGEPATH, context().find(byClassName("data-storage-navigation__breadcrumb-item"))),
            entry(HEADER, context().find(byClassName("browser__item-header"))),
            entry(SHOW_METADATA, context().find(byClassName("presentation-action-attributes"))),
            entry(PREV_PAGE, context().find(byId("prev-page-button"))),
            entry(NEXT_PAGE, context().find(byId("next-page-button"))),
            entry(GENERATE_URL, context().find(selectionAction("generate-url"))),
            entry(SELECTION_ACTIONS, context().find(byId("selection-actions"))),
            entry(ACTIONS, context().find(byId("presentation-actions")))
    );

    /**
     * An action of the selected items. The storage page shows a single action as a button with the id
     * {@code selection-action-<key>}, and several in the "Selection" dropdown ({@code #selection-actions}), whose
     * menu items have the class {@code selection-action-<key>} (rc-menu items drop their id).
     */
    private static By selectionAction(final String key) {
        return byCssSelector(format("#selection-action-%1$s, .selection-action-%1$s", key));
    }

    public static By browserItem(final String name) {
        final String browserItemClass = "ant-table-row";
        final String itemNameClass = "browser__tree-item-name";
        return byXpath(format(
            ".//tr[contains(@class, '%s') and ./td[@class = '%s' and . = '%s']]",
            browserItemClass, itemNameClass, name
        ));
    }

    /**
     * Selects a folder by name in a browser view in a storage.
     *
     * @param name Case sensitive string with exact text of a searching folder.
     * @return Qualifier of folder with exact {@code name} in a thee.
     */
    public static By folderWithName(final String name) {
        return byXpath(format(".//*[contains(@class, 'browser__folder') and contains(., '%s')]", name));
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }

    public StorageContentAO cd(String folderName) {
        sleep(1, SECONDS);
        $(byClassName("ant-table-content")).shouldBe(visible)
                .find(byText(folderName)).shouldBe(visible).click();
        return this;
    }

    public List<String> filesAndFolders() {
        return elementsByPredicate(element -> true);
    }

    public List<String> folders() {
        return elementsByPredicate(element -> element.has(cssClass("browser__folder")));
    }

    public List<String> files() {
        return elementsByPredicate(element -> element.has(cssClass("browser__file")));
    }

    private List<String> elementsByPredicate(Predicate<SelenideElement> predicate) {
        sleep(5, SECONDS);
        $(byClassName("ant-table-content")).shouldBe(visible);
        if ($(tagName("tbody")).isDisplayed()) {
            return getElementsRows()
                    .filter(predicate)
                    .map(this::getRowElementName)
                    .collect(toList());
        }
        return Collections.emptyList();
    }

    private Stream<SelenideElement> getElementsRows() {
        return $(tagName("tbody")).findAll(tagName("tr")).stream();
    }

    private String getRowElementName(SelenideElement entry) {
        return entry.findAll(tagName("td")).get(2).text();
    }

    public StorageContentAO validateListOfFoldersIsDisplayed() {
        $(byClassName("ant-table-tbody"))
                .find(byClassName("ant-table-row")).shouldBe(visible);
        return this;
    }

    public StorageContentAO rm(String name) {
        elementRow(name).find(byClassName("ant-btn-danger")).shouldBe(visible).click();
        final ConfirmationPopupAO<StorageContentAO> popup = new ConfirmationPopupAO<>(this);
        popup.ensureTitleIs("Remove folder").ok();
        return this;
    }

    public StorageContentAO rmFile(String name) {
        elementRow(name).find(byClassName("ant-btn-danger")).shouldBe(visible).click();
        new ConfirmationPopupAO<>(this).ensureTitleIs("Remove file").ok();
        return this;
    }

    public StorageContentAO createFolder(String folderName) {
        sleep(1, SECONDS);
        resetMouse().hover(CREATE).click(CREATE_FOLDER);
        $(byId("name")).shouldBe(visible).setValue(folderName);
        $(button("OK")).shouldBe(visible).click();
        return this;
    }

    public ElementEditPopupAO clickOnCreateFolderButton() {
        hover(CREATE).click(CREATE_FOLDER);
        return new ElementEditPopupAO();
    }

    public StorageContentAO createFile(String fileName) {
        sleep(1, SECONDS);
        resetMouse().hover(CREATE).click(CREATE_FILE);
        $$(byId("name")).findBy(visible).setValue(fileName);
        $(button("OK")).shouldBe(visible).click();
        return this;
    }

    public StorageContentAO createFileWithContent(String fileName, String content) {
        sleep(1, SECONDS);
        resetMouse().hover(CREATE).click(CREATE_FILE);
        $$(byId("name")).findBy(visible).setValue(fileName);
        $$(byId("content")).findBy(visible).setValue(content);
        $(button("OK")).shouldBe(visible).click();
        return this;
    }

    public StorageContentAO createAndEditFile(String fileName, String fileText) {
        return createFile(fileName)
                .sleep(1, SECONDS)
                .fileMetadata(fileName)
                .fullScreen()
                .sleep(3, SECONDS)
                .editFileWithText(fileText);
    }

    public StorageContentAO editFile(String fileName, String fileText) {
        return fileMetadata(fileName)
               .fullScreen()
               .editFileWithText(fileText);
    }

    public StorageContentAO validateElementIsPresent(String elementName) {
        elementRow(elementName).shouldBe(visible);
        return this;
    }

    public StorageContentAO validateElementNotPresent(String elementName) {
        elementRow(elementName).shouldNotBe(visible);
        return this;
    }

    public StorageContentAO validateElementByPatternIsPresent(String elementNamePattern) {
        $$(className("ant-table-row")).findBy(matchText(elementNamePattern)).shouldBe(visible);
        return this;
    }

    public SelenideElement elementRow(String elementName) {
        return $$(className("ant-table-row")).findBy(textCaseSensitive(elementName));
    }

    public StorageContentAO validateFoldersOrderIsAlphabetical() {
        Assert.assertTrue(
                Comparators.isInOrder(
                        $$(byCssSelector(".ant-table-row.browser__folder td:nth-child(3)")).texts(),
                        Comparator.naturalOrder()
                )
        );
        return this;
    }

    public StorageContentAO validateCurrentFolderIsEmpty() {
        List<String> filesAndFolders = filesAndFolders();
        if (filesAndFolders.size() == 1) {
            Assert.assertEquals(filesAndFolders.get(0), "..");
        } else if (!filesAndFolders.isEmpty()) {
            fail("Folder is not empty.");
        }
        return this;
    }

    public StorageContentAO validateFilesAndFoldersAre(final List<String> expected) {
        assertEquals(filesAndFolders(), expected, "The list of files and folders has changed.");
        return this;
    }

    public StorageContentAO navigateUsingAddressBar(String destination) {
        sleep(5, SECONDS);
        SelenideElement inputField = getOpenedNavigationBarInput();

        String parentPath = format("%s/", inputField.should(exist).getValue());
        String addressPath = URI.create(parentPath).resolve(destination).toString();
    
        setValue(inputField, addressPath);
        inputField.pressEnter();
        return this;
    }

    public StorageContentAO tryNavigateToAnotherBucket(String anotherBucketName) {
        SelenideElement inputField = getOpenedNavigationBarInput().should(exist);
        String path = inputField.getValue();
        inputField.setValue(replaceBucket(path, anotherBucketName)).pressEnter();
        return this;
    }

    private SelenideElement getOpenedNavigationBarInput() {
        get(SELECT_ALL).shouldBe(exist);
        get(STORAGEPATH).click();
        sleep(3, SECONDS);
        return get(NAVIGATION).find(byXpath(".//input[@class='ant-input']"));
    }

    private String replaceBucket(String path, String newBucketName) {
        Matcher matcher = Pattern.compile(".*://").matcher(path);
        matcher.find();
        return matcher.group().concat(newBucketName);
    }

    public StorageContentAO validateHeader(String message) {
        return ensure(HEADER, text(message));
    }

    public EditStoragePopUpAO clickEditStorageButton() {
        click(EDIT_STORAGE);
        return new EditStoragePopUpAO();
    }

    public StorageContentAO validateDescriptionIsNotEmpty() {
        Assert.assertTrue(getStorageDescription().length() > 0);
        return this;
    }

    public StorageContentAO validateDescription(String expectedDescription) {
        Assert.assertEquals(expectedDescription, getStorageDescription());
        return this;
    }

    private String getStorageDescription() {
        return get(DESCRIPTION).shouldBe(visible).text().substring("Description: ".length());
    }

    public StorageContentAO validateStsDuration(String duration) {
        $(byClassName("browser__data-storage-info-container"))
                .findAll(byClassName("ant-row"))
                .findBy(text("Short-Term Storage duration:"))
                .shouldHave(exactText(format("Short-Term Storage duration: %s days", duration)));
        return this;
    }

    public StorageContentAO validateLtsDuration(String duration) {
        $(byClassName("browser__data-storage-info-container"))
                .findAll(byClassName("ant-row"))
                .findBy(text("Long-Term Storage duration:"))
                .shouldHave(exactText(format("Long-Term Storage duration: %s days", duration)));
        return this;
    }

    public StorageContentAO validateStoragePath() {
        return ensure(STORAGEPATH, matchText(C.STORAGE_PREFIX + "://"));
    }

    public StorageContentAO validateNfsPath() {
        return ensure(STORAGEPATH, matchText("nfs://"));
    }

    public StorageContentAO clickRefreshButton() {
        return click(REFRESH);
    }

    public FileAO selectFile(String fileName) {
        return new FileAO(fileName, 0);
    }

    public MetadataSectionAO fileMetadata(String filename) {
        $(byClassName("ant-table-tbody")).shouldBe(visible);
        $$(byClassName("browser__name-cell")).findBy(text(filename)).click();
        return new MetadataSectionAO(this);
    }

    public FileAO selectNthFileWithName(int n, String fileName) {
        return new FileAO(fileName, n);
    }

    public FolderAO selectFolder(String folderName) {
        return new FolderAO(folderName);
    }

    public SelectedElementsAO selectElementsUsingCheckboxes(String... names) {
        return new SelectedElementsAO(names);
    }

    public StorageContentAO nextPage() {
        return click(NEXT_PAGE);
    }

    public StorageContentAO previousPage() {
        return click(PREV_PAGE);
    }

    public StorageContentAO selectPage() {
        click(SELECT_ALL);
        $(byCssSelector("#selection-actions, #selection-action-clear")).shouldBe(visible);
        return this;
    }

    public StorageContentAO clearSelection() {
        return openSelectionActions().click(CLEAR_SELECTION);
    }

    /**
     * Opens the "Selection" dropdown of the selected items, if they have several actions. A single action is a
     * button on the page, and nothing is opened then.
     */
    public StorageContentAO openSelectionActions() {
        final SelenideElement menu = $(byClassName("selection-actions-menu"));
        if (get(SELECTION_ACTIONS).isDisplayed() && !menu.isDisplayed()) {
            click(SELECTION_ACTIONS);
            menu.shouldBe(visible);
        }
        return this;
    }

    /**
     * Opens the presentation dropdown ({@code #presentation-actions}) of the page: "Show attributes", "Show file
     * versions" etc. are its items, and a ticked one has a check icon.
     */
    private SelenideElement openPresentationAction(final String key) {
        final SelenideElement menu = $(byClassName("presentation-actions-menu"));
        if (!menu.isDisplayed()) {
            click(ACTIONS);
            menu.shouldBe(visible);
        }
        return menu.find(byClassName("presentation-action-" + key)).shouldBe(visible);
    }

    private StorageContentAO closePresentationActions() {
        if ($(byClassName("presentation-actions-menu")).isDisplayed()) {
            click(ACTIONS);
            $(byClassName("presentation-actions-menu")).shouldBe(hidden);
        }
        return this;
    }

    public StorageContentAO validateNoElementsAreSelected() {
        return shouldNotBeSelected(filesAndFolderElements());
    }

    public StorageContentAO validateAllFilesAreSelected() {
        return shouldBeSelected(filesElements());
    }

    private StorageContentAO shouldBeSelected(ElementsCollection elements) {
        checkBoxes(elements).forEach(element -> element.shouldBe(selected));
        return this;
    }

    private StorageContentAO shouldNotBeSelected(ElementsCollection elements) {
        checkBoxes(elements).forEach(element -> element.shouldNotBe(selected));
        return this;
    }

    private Function<SelenideElement, SelenideElement> toCheckBox() {
        return element -> element.find(className("ant-checkbox-input"));
    }

    private Predicate<SelenideElement> hasCheckBox() {
        return element -> element.find(className("ant-checkbox-input")).exists();
    }

    private SelenideElement elementsTable() {
        return $(tagName("tbody")).shouldBe(visible);
    }


    private Stream<SelenideElement> checkBoxes(ElementsCollection elements) {
        return elements.stream().filter(hasCheckBox()).map(toCheckBox());
    }

    private ElementsCollection filesElements() {
        return elementsTable().findAll(className("browser__file"));
    }

    private ElementsCollection filesAndFolderElements() {
        return elementsTable().findAll(className("ant-table-row"));
    }

    public StorageContentAO uploadFile(File file) {
        uploadFileWithoutValidation(file);
        $(byText(file.getName())).should(appear);
        return this;
    }

    public StorageContentAO validateIsUploading() {
        $(withText("Uploading files...")).should(disappear);
        return this;
    }

    /**
     * Uploads the file and checks the "Uploading files..." pop-up: it shows a progress bar and the file name,
     * then closes. The pop-up closes 2 seconds after the upload ends, so it is checked right after the upload starts.
     */
    public StorageContentAO uploadFileAndValidateUploadingPopup(final File file) {
        uploadFileWithoutValidation(file);
        final SelenideElement popup = $$(byClassName("ant-modal-content"))
                .findBy(text("Uploading files...")).shouldBe(visible);
        popup.find(byClassName("ant-progress")).shouldBe(visible);
        popup.shouldHave(text(file.getName()));
        popup.should(disappear);
        return this;
    }

    /**
     * An unstable version of {@link #uploadFile(File)} without validating file appearance.
     * Could be used in pagination tests (when elements count is greater than page can contain).
     */
    public StorageContentAO uploadFileWithoutValidation(File file) {
        // in order to avoid endless file uploading
        sleep(25, SECONDS);
        ensure(UPLOAD, visible);
        $(byClassName("ant-upload")).find(tagName("input")).should(exist).uploadFile(file);
        return this;
    }

    /**
     * Waits until the page is done with the last upload: the "Uploading files..." pop-up has closed and the list
     * it reloads has loaded. The pop-up closes itself 2 seconds after the upload ends and then reloads the list
     * (UploadButton.hideUploadInfo). While the list loads, the table and its toolbar ("Select page" too) are blurred
     * under the loading spinner and do not get clicks, so a click in that transition can be lost. The reload
     * starts a moment after the pop-up has gone, so the list has to stay loaded for {@link #LIST_LOADED_FOR} in a row.
     */
    public StorageContentAO waitForUploadToFinish() {
        $$(byClassName("ant-modal-content")).findBy(text("Uploading files..."))
                .should(disappear, UPLOAD_FINISH_TIMEOUT);
        final long deadline = System.currentTimeMillis() + UPLOAD_FINISH_TIMEOUT.toMillis();
        long loadedSince = System.currentTimeMillis();
        while (System.currentTimeMillis() - loadedSince < LIST_LOADED_FOR.toMillis()) {
            if (!$$(byClassName("ant-spin-blur")).isEmpty()) {
                loadedSince = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() > deadline) {
                fail("The storage list is still loading " + UPLOAD_FINISH_TIMEOUT.getSeconds() + " s after the upload");
            }
            Selenide.sleep(100);
        }
        return this;
    }

    public StorageContentAO validateElementsAreNotEditable() {
        getElementsAOs().forEach(element -> element.ensureNotVisible(DELETE, EDIT));
        return this;
    }

    public StorageContentAO validateElementsAreEditable() {
        getElementsAOs().forEach(element -> element.ensureVisible(DELETE, EDIT));
        return this;
    }

    /**
     * Shows the attributes panel: the "Show attributes" item of the presentation dropdown, unless it is ticked
     * already. The page renders the dropdown once the storage has loaded.
     */
    public MetadataSectionAO showMetadata() {
        get(ACTIONS).shouldBe(visible);
        final SelenideElement attributes = openPresentationAction("attributes");
        if (attributes.find(byClassName("anticon-check")).exists()) {
            closePresentationActions();
        } else {
            attributes.click();
        }
        return new MetadataSectionAO(this);
    }

    private Stream<ElementAO> getElementsAOs() {
        return Stream.concat(getFoldersAOs(), getFilesAOs());
    }

    private Stream<FileAO> getFilesAOs() {
        return files().stream().map(this::selectFile);
    }

    private Stream<FolderAO> getFoldersAOs() {
        return folders().stream().map(this::selectFolder);
    }

    /**
     * Ticks or unticks the "Show file versions" item of the presentation dropdown (shown for a versioned storage
     * only).
     */
    public StorageContentAO showFilesVersions(final boolean requiredState) {
        sleep(1, SECONDS);
        final SelenideElement versions = openPresentationAction("version");
        if (versions.find(byClassName("anticon-check")).exists() != requiredState) {
            versions.click();
            return this;
        }
        return closePresentationActions();
    }

    public StorageContentAO assertShowFilesVersionsIsChecked() {
        openPresentationAction("version").find(byClassName("anticon-check")).shouldBe(visible);
        return closePresentationActions();
    }

    public StorageContentAO assertFilesHaveVersions() {
        $(byClassName("browser__checkbox-cell-versions")).shouldBe(visible);
        return this;
    }

    public StorageContentAO elementsShouldBeSelected(String... elementsNames) {
        Arrays.stream(elementsNames)
                .forEach(name -> elementRow(name).shouldBe(visible)
                        .find(className("ant-checkbox-input")).shouldBe(selected));
        return this;
    }

    public StorageContentAO shouldContainNumberOfElements(int number) {
        filesAndFolderElements().shouldHave(size(number));
        return this;
    }

    public String getStoragePath() {
        return get(STORAGEPATH).text();
    }

    public StorageContentAO removeAllSelectedElements() {
        return selectElementsUsingCheckboxes().removeAllSelected();
    }

    public StorageContentAO markCheckboxByName(String name) {
        SelenideElement checkBox = context().shouldBe(visible)
                .find(byText(format("%s", name)))
                .find(byXpath("preceding-sibling::*[contains(@class, 'browser__checkbox-cell')]"));
        checkBox.shouldBe(visible).click();
        return this;
    }

    public String findFileSizeByPattern(String filename) {
        return Optional.ofNullable(filename)
                .map(fileName -> $(byClassName("ant-table-body"))
                        .findAll(byClassName("browser__file"))
                        .findBy(matchText(filename))
                        .find(byCssSelector("td:nth-child(4)"))
                        .shouldBe(visible)
                        .getText()
                )
                .orElseThrow(() -> new NoSuchElementException(format("%s file was not found.", filename)));
    }

    public class FileAO extends ElementAO<FileAO> {

        private final String fileName;
        private final int index;

        FileAO(String fileName, int index) {
            this.fileName = fileName;
            this.index = index;
            elements().putAll(initialiseElements(
                    entry(RELOAD, context().find(buttonByIconClass("anticon-reload"))),
                    entry(DOWNLOAD, context().find(".anticon-download")),
                    entry(EDIT, context().find(editButton())),
                    entry(DELETE, context().find(deleteButton()))
            ));
        }

        public int size() {
            //it presents like: 123 bytes
            String size = context().find(byCssSelector("td:nth-child(4)")).shouldBe(visible).getText();
            return Integer.parseInt(size.split(" ")[0]);
        }

        public FileAO download() {
            return click(DOWNLOAD).sleep(5, SECONDS);
        }

        public StorageContentAO reload() {
            click(RELOAD);
            return new StorageContentAO();
        }

        public StorageContentAO showVersions() {
            context().find(byClassName("ant-table-row-collapsed")).shouldBe(visible).click();
            return new StorageContentAO();
        }

        public StorageContentAO validateFileHasBackgroundColor(String color) {
            String selectedFileColor = $$(className("ant-table-row"))
                    .findBy(text(fileName))
                    .shouldBe(visible)
                    .getCssValue("background-color");
            assertEquals(Utils.convertRGBColorToHex(selectedFileColor), color);
            return new StorageContentAO();
        }

        public FileAO validateHasSize(long expectedSize) {
            assertEquals(size(), expectedSize);
            return this;
        }

        public StorageContentAO validateHasDateTime() {
            String date = context().find(byCssSelector("td:nth-child(5)")).shouldBe(visible).getText();
            assertTrue(date.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
            return new StorageContentAO();
        }

        @Override
        public SelenideElement context() {
            return Optional.ofNullable(fileName)
                    .map(fileName -> $(byClassName("ant-table-body"))
                            .findAll(byText(fileName))
                            .get(index)
                            .closest("tr")
                            .shouldHave(cssClass("browser__file")))
                    .orElseGet(super::context);
        }
    }

    public class FolderAO extends ElementAO<FolderAO> {
        private final String folderName;

        public FolderAO(String folderName) {
            this.folderName = folderName;
            elements().putAll(initialiseElements(
                    entry(EDIT, context().find(editButton())),
                    entry(DELETE, context().find(deleteButton()))
            ));
        }

        @Override
        public SelenideElement context() {
            return Optional.ofNullable(folderName)
                    .map(folderName -> $(byClassName("ant-table-body"))
                            .find(byText(folderName)).closest(".browser__folder"))
                    .orElseGet(super::context);
        }

        public StorageContentAO deleteFromBucket() {
            context().find(buttonByIconClass("anticon-delete")).shouldBe(visible).click();
            $(byId("delete-bucket-item-modal-delete-from-bucket-button")).shouldBe(visible).click();
            return new StorageContentAO();
        }
    }

    public abstract class ElementAO<ELEMENT_TYPE extends ElementAO<ELEMENT_TYPE>> implements AccessObject<ELEMENT_TYPE> {
        private final Map<Primitive, SelenideElement> elements = initialiseElements();

        public StorageContentAO renameTo(String newName) {
            return clickEditButton().typeInField(newName).ok();
        }

        public StorageContentAO delete() {
            return clickDeleteButton().ok();
        }

        public ElementEditPopupAO clickEditButton() {
            click(EDIT);
            return new ElementEditPopupAO();
        }

        public ElementDeletePopupAO clickDeleteButton() {
            click(DELETE);
            return new ElementDeletePopupAO();
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }

    public class ElementDeletePopupAO extends PopupAO<ElementEditPopupAO, StorageContentAO> {
        public ElementDeletePopupAO() {
            super(StorageContentAO.this);
        }

        @Override
        public StorageContentAO ok() {
            super.ok();
            $(byClassName("ant-modal-body")).shouldNotBe(visible);
            return parent();
        }
    }

    public class ElementEditPopupAO extends PopupWithStringFieldAO<ElementEditPopupAO, StorageContentAO> {
        public ElementEditPopupAO() {
            super(StorageContentAO.this);
        }

        @Override
        public ElementEditPopupAO typeInField(String newName) {
            $(byId("name")).shouldBe(visible).setValue(newName);
            return this;
        }
    }

    public class SelectedElementsAO {

        public SelectedElementsAO(String[] names) {
            Arrays.stream(names)
                    .forEach(elementName -> {
                        sleep(1, SECONDS);
                        $(tagName("tbody"))
                                .find(byText(elementName))
                                .closest("tr")
                                .find(className("ant-checkbox-wrapper")).should(visible).click();
                    });
        }

        public StorageContentAO removeAllSelected() {
            StorageContentAO.this.openSelectionActions().ensureVisible(CLEAR_SELECTION);
            StorageContentAO.this.click(REMOVE_ALL);

            $$(byClassName("ant-modal-content"))
                    .findBy(text("Remove all selected items?"))
                    .find(button("OK")).shouldBe(visible).click();

            $$(byClassName("ant-modal-content"))
                    .findBy(text("Remove all selected items?")).should(disappear);

            return StorageContentAO.this;
        }

        public SelectedElementsAO clickOnRemoveAllSelectedButton() {
            StorageContentAO.this.openSelectionActions().click(REMOVE_ALL);
            return this;
        }

        public StorageContentAO clickCancelInRemoveAllSelectedDialog() {
            $$(byClassName("ant-modal-content"))
                    .findBy(text("Remove all selected items?"))
                    .find(button("Cancel")).shouldBe(visible).click();

            $$(byClassName("ant-modal-content"))
                    .findBy(text("Remove all selected items?")).should(disappear);

            return StorageContentAO.this;
        }
    }

    public static class AbstractEditStoragePopUpAO<POPUP_AO
            extends AbstractEditStoragePopUpAO<POPUP_AO, PARENT_AO>, PARENT_AO> extends PopupAO<POPUP_AO, PARENT_AO> {
        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(NAME, $(byId("name"))),
                entry(PATH, $(byId("edit-storage-storage-path-input"))),
                entry(DESCRIPTION, $(byId("description"))),
                entry(STS_DURATION, $(byId("shortTermStorageDuration"))),
                entry(LTS_DURATION, $(byId("longTermStorageDuration"))),
                entry(ENABLE_VERSIONING, $(withText("Enable versioning"))),
                entry(MOUNT_POINT, $(byId("mountPoint"))),
                entry(MOUNT_OPTIONS, $(byId("mountOptions"))),
                entry(BACKUP_DURATION, $(byId("backupDuration")))
        );

        public AbstractEditStoragePopUpAO(PARENT_AO parentAO) {
            super(parentAO);
        }

        public POPUP_AO setAlias(String alias) {
            return clear(NAME).setValue(NAME, alias);
        }

        public POPUP_AO setMountPoint(String mountPoint) {
            return clear(MOUNT_POINT).setValue(MOUNT_POINT, mountPoint);
        }

        public POPUP_AO setPath(String path) {
            return setValue(PATH, path);
        }

        public POPUP_AO setDescription(String description) {
            return setValue(DESCRIPTION, description);
        }

        public POPUP_AO setDurations(String stsDuration, String ltsDuration) {
            return setValue(STS_DURATION, stsDuration)
                    .setValue(LTS_DURATION, ltsDuration);
        }

        @SuppressWarnings("unchecked")
        public POPUP_AO setVersions(boolean requiredState) {
            if (getVersionsFlagState() != requiredState) {
                context().find(visible(byClassName("ant-checkbox-wrapper"))).shouldBe(visible).click();
            }
            return (POPUP_AO) this;
        }

        private boolean getVersionsFlagState() {
            return context().find(byClassName("ant-checkbox-checked")).is(visible);
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }

    public class EditStoragePopUpAO extends AbstractEditStoragePopUpAO<EditStoragePopUpAO, PipelinesLibraryAO> {
        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                super.elements(),
                entry(SAVE, $(byId("edit-storage-dialog-save-button"))),
                entry(DELETE, $(byId("edit-storage-dialog-delete-button"))),
                entry(CANCEL, $(byId("edit-storage-dialog-cancel-button"))),
                entry(PERMISSIONS, $(byText("Permissions")))
        );

        public EditStoragePopUpAO() {
            super(new PipelinesLibraryAO());
        }

        public EditStoragePopUpAO validateEditFormElements() {
            return ensure(PATH, disabled, visible)
                    .ensure(NAME, visible)
                    .ensure(DESCRIPTION, visible)
                    .performIf(C.CLOUD_PROVIDER.equalsIgnoreCase(Cloud.AWS.name())
                            || C.CLOUD_PROVIDER.equalsIgnoreCase(Cloud.GCP.name()), popup -> popup
                            .ensure(STS_DURATION, visible)
                            .ensure(LTS_DURATION, visible)
                            .ensure(ENABLE_VERSIONING, visible))
                    .ensure(MOUNT_POINT, visible)
                    .ensure(MOUNT_OPTIONS, visible)
                    .ensure(SAVE, visible)
                    .ensure(DELETE, visible)
                    .ensure(CANCEL, visible);
        }

        public EditStoragePopUpAO validateEditFormElementsNfsMount() {
            return ensure(PATH, disabled, visible)
                    .ensure(NAME, visible)
                    .ensure(DESCRIPTION, visible)
                    .ensure(MOUNT_POINT, visible)
                    .ensure(MOUNT_OPTIONS, visible)
                    .ensure(SAVE, visible)
                    .ensure(DELETE, visible)
                    .ensure(CANCEL, visible);
        }

        public EditStoragePopUpAO editForNfsMount() {
            if (!filesAndFolderElements().isEmpty()) {
                $(byClassName("edit-storage-button")).shouldBe(enabled).click();
            }
            return this;
        }

        @Override
        public PipelinesLibraryAO ok() {
           return clickSaveButton();
        }

        public PipelinesLibraryAO clickSaveButton() {
            return click(SAVE).parent();
        }

        public PipelinesLibraryAO clickCancel() {
            return click(CANCEL).ensure(CANCEL, not(visible)).parent();
        }

        public DeleteStorageConfirmationPopUp clickDeleteStorageButton() {
            sleep(1, SECONDS);
            click(DELETE);
            return new DeleteStorageConfirmationPopUp(this);
        }

        public PermissionTabAO clickOnPermissionsTab() {
            click(PERMISSIONS);
            return new PermissionTabAO(this);
        }

        @Override
        public SelenideElement context() {
            return $(modalWithTitle("Edit", "storage"));
        }

        @Override
        public void closeAll() {
            ok();
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }

    public class DeleteStorageConfirmationPopUp implements AccessObject<DeleteStorageConfirmationPopUp> {
        private final Map<Primitive, SelenideElement> elements = initialiseElements(
                entry(DELETE, $(byId("edit-storage-delete-dialog-delete-button"))),
                entry(UNREGISTER, $(byId("edit-storage-delete-dialog-unregister-button"))),
                entry(CANCEL, $(byId("edit-storage-delete-dialog-cancel-button"))),
                entry(CROSS, context().find(className("ant-modal-close")))
        );
        private final EditStoragePopUpAO editStoragePopUpAO;

        public DeleteStorageConfirmationPopUp(EditStoragePopUpAO editStoragePopUpAO) {
            this.editStoragePopUpAO = editStoragePopUpAO;
        }

        public PipelinesLibraryAO clickDelete() {
            click(DELETE).ensure(DELETE, not(visible));
            return new PipelinesLibraryAO();
        }

        public PipelinesLibraryAO clickUnregister() {
            click(UNREGISTER).ensure(UNREGISTER, not(visible));
            return new PipelinesLibraryAO();
        }

        public EditStoragePopUpAO clickCrossButton() {
            click(CROSS).ensure(CROSS, not(visible));
            return editStoragePopUpAO;
        }

        public EditStoragePopUpAO clickCancel() {
            click(CANCEL).ensure(CANCEL, not(visible));
            return editStoragePopUpAO;
        }

        @Override
        public SelenideElement context() {
            return Utils.getPopupByTitle("Do you want to delete a storage itself or only unregister it?");
        }

        @Override
        public Map<Primitive, SelenideElement> elements() {
            return elements;
        }
    }
}
