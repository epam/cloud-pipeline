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
package com.epam.pipeline.autotests;

import com.codeborne.selenide.SelenideElement;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.SelenideElements;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Selenide.*;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.openqa.selenium.By.cssSelector;
import static org.openqa.selenium.By.tagName;

public class PipelineEditingTest extends AbstractBfxPipelineTest implements Navigation {

    private static String FOLDER_NAME = "seleniumTestFolder-" + Double.doubleToLongBits(Math.random());
    private static String PIPELINE_NAME = "seleniumTestPipe-" + Double.doubleToLongBits(Math.random());
    private static final String NEW_FILE_NAME = "seleniumTestFile-" + Double.doubleToLongBits(Math.random());
    private static final String RENAMED_FILE_NAME = "renamedSeleniumTestFile-" + Double.doubleToLongBits(Math.random());
    private static final String RENAMED_PIPELINE_NAME = "renamedSeleniumTestPipe-" + Double.doubleToLongBits(Math.random());

    private String currentPipelineName = PIPELINE_NAME;
    private SelenideElement OKButton = $(button("OK"));

    @AfterClass(alwaysRun = true)
    @TestCase(value = {"EPMCMBIBPC-287", "EPMCMBIBPC-288"})
    public void cleanUp() {
        library()
                .cd(FOLDER_NAME)
                .removePipeline(currentPipelineName)
                .removeFolder(FOLDER_NAME);
    }

    @Test(priority = 0)
    @TestCase(value = {"EPMCMBIBPC-284"})
    public void createFolderTest() {
        library()
                .createFolder(FOLDER_NAME);
    }

    @Test(dependsOnMethods = {"createFolderTest"})
    @TestCase(value = {"EPMCMBIBPC-285", "EPMCMBIBPC-231"})
    public void createPipelineFromPythonTemplateTest() {
        library()
                .cd(FOLDER_NAME)
                .createPipeline(Template.PYTHON, currentPipelineName)
                .validatePipeline(currentPipelineName);
    }

    @Test(dependsOnMethods = {"createPipelineFromPythonTemplateTest"})
    @TestCase(value = {"EPMCMBIBPC-286"})
    public void pythonPipelineValidationTest() {
        $(byText(currentPipelineName)).shouldBe(visible).click();
        $(byCssSelector("td.browser__tree-item-name")).shouldBe(visible).click();
    }

    @Test(dependsOnMethods = {"pythonPipelineValidationTest"})
    @TestCase(value = {"EPMCMBIBPC-295"})
    public void createPipelineFileTest() {
        clickCodeTab();
        $(button("NEW FILE")).click();
        sleep(1, SECONDS);
        $(cssSelector("input#name.ant-input.ant-input-lg")).setValue(NEW_FILE_NAME);
        OKButton.click();
        sleep(15, SECONDS);
    }

    @Test(dependsOnMethods = {"createPipelineFileTest"})
    @TestCase(value = {"EPMCMBIBPC-290"})
    public void editPipelineFileNameTest() {
        renameButton(NEW_FILE_NAME).shouldBe(visible, enabled).click();
        sleep(2,SECONDS);
        $$(byId("name")).findBy(visible).clear();
        $$(byId("name")).findBy(visible).setValue(RENAMED_FILE_NAME);
        OKButton.click();
    }

    @Test(dependsOnMethods = {"editPipelineFileNameTest"})
    @TestCase(value = {"EPMCMBIBPC-289"})
    public void editPipelineFileTest() {
        sleep(5, SECONDS);
        $(byText(RENAMED_FILE_NAME)).shouldBe(visible).click();

        sleep(3, SECONDS);
        $(byCssSelector("button.ant-btn.pipeline-code-form__button")).shouldBe(visible, enabled).click();
        $(byClassName(" CodeMirror-line ")).shouldBe(visible,enabled);
        actions().moveToElement($(byClassName(" CodeMirror-line ")).shouldBe(visible))
                .click()
                .sendKeys("Some pretty code")
                .perform();
        $(button("Save")).click();
        $(byId("message")).shouldBe(visible).sendKeys("Cool code");
        $(button("Commit")).click();
    }

    @Test(dependsOnMethods = {"editPipelineFileTest"})
    @TestCase(value = {"EPMCMBIBPC-291"})
    public void deletePipelineFileTest() {
        deleteButton(RENAMED_FILE_NAME).shouldBe(visible, enabled).click();
        OKButton.shouldBe(visible).click();
    }

    @Test(dependsOnMethods = {"deletePipelineFileTest"})
    @TestCase(value = {"EPMCMBIBPC-292"})
    public void editPipelineTest() {
        library()
                .sleep(2, SECONDS)
                .clickOnPipeline(PIPELINE_NAME)
                .sleep(2, SECONDS)
                .clickEditButton()
                .rename(currentPipelineName = RENAMED_PIPELINE_NAME)
                .save();
    }

    public void setFolderName(String folderName) {
        FOLDER_NAME = folderName;
    }

    public void setPipelineName(String pipelineName) {
        PIPELINE_NAME = pipelineName;
        currentPipelineName = pipelineName;
    }

    private SelenideElement renameButton(String filename) {
        return file(filename)
                .findAll("button")
                .findBy(text("Rename"));
    }

    private SelenideElement deleteButton(String filename) {
        return file(filename)
                .findAll("button")
                .findBy(text("Delete"));
    }

    private SelenideElement file(final String filename) {
        return SelenideElements.of(tagName("tr"), $(byClassName("pipeline-details__full-height-container")))
                .findBy(text(filename));
    }

    private void clickCodeTab() {
        sleep(1, SECONDS);
        $(".pipeline-details__tabs-menu").findAll("li").findBy(text("CODE")).click();
        sleep(1, SECONDS);
    }
}