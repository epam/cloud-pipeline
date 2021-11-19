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
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.refresh;
import static com.epam.pipeline.autotests.ao.Primitive.DESCRIPTION;
import static com.epam.pipeline.autotests.ao.Primitive.NAME;
import static com.epam.pipeline.autotests.ao.Primitive.REPOSITORY;
import static com.epam.pipeline.autotests.ao.Primitive.TOKEN;
import static com.epam.pipeline.autotests.ao.Template.PYTHON;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public class RemotePipelineRepositoryTest
        extends AbstractBfxPipelineTest
        implements Navigation {

    private final String pipelineName = "remote-pipeline-repository-test-pipeline-" + Utils.randomSuffix();
    private final String pythonFileName = "test-token-pipe.py";
    private final String configJsonFileName = "config.json";
    private final String pipelineForPipelineEditingTest = "remote-pipeline-repository-pipeline-for-pet-test" + Utils.randomSuffix();
    private final String folderNameForPipelineEditingTest = "remote-pipeline-repository-pet-test-folder-for-pet-test" + Utils.randomSuffix();
    private final String pipelineInNonExistingRepository = "remote-pipeline-repository-in-non-existing-repository" + Utils.randomSuffix();
    private final String repository = C.REPOSITORY;
    private final String userName = C.LOGIN.substring(0, C.LOGIN.indexOf("@"));
    private final String nonExistingRepository = "https://"+ userName + "@git.epam.com/epm-cmbi/non-existing-project.git";
    private final String token = C.TOKEN;
    private final String invalidToken = "invalidToken";

    @AfterClass(alwaysRun = true)
    public void removePipeline() {
        open(C.ROOT_ADDRESS);
        sleep(2, SECONDS);
        navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .clickEditButton();
        clickDeleteWithPauses();
    }

    @Test
    @TestCase({"EPMCMBIBPC-701"})
    public void validateFormForUsingRemoteRepository() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .clickCreatePipelineButton()
                .setName(pipelineName)
                .openRepositorySettings()
                .ensureVisible(NAME, DESCRIPTION, REPOSITORY, TOKEN)
                .cancel();
    }

    @Test(dependsOnMethods = "validateFormForUsingRemoteRepository")
    @TestCase({"EPMCMBIBPC-702"})
    public void validateCheckoutFromRemote() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .clickCreatePipelineButton()
                .setName(pipelineName)
                .openRepositorySettings()
                .setRepository(repository)
                .setToken(token)
                .create()
                .validateIsLoading()
                .validatePopupClosed();
    }

    @Test(dependsOnMethods = "validateCheckoutFromRemote")
    @TestCase({"EPMCMBIBPC-703"})
    public void checkPipelineContent() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .v1()
                .codeTab()
                .clickOnFile(pythonFileName)
                .assertFileIsNotEmpty()
                .close()
                .clickOnFile(configJsonFileName)
                .assertFileIsNotEmpty()
                .close();
    }

    @Test(dependsOnMethods = "checkPipelineContent")
    @TestCase({"EPMCMBIBPC-705"})
    public void validateEditingForRemoteRepository() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .draft()
                .codeTab()
                .clearAndFillPipelineFile(pythonFileName, "new code " + pipelineName)
                .messageShouldAppear("Committing changes...");
        refresh();
        navigationMenu()
                .library()
                .clickOnPipeline(pipelineName)
                .draft()
                .codeTab()
                .clickOnFile(pythonFileName)
                .shouldContainInCode("new code " + pipelineName)
                .close();
    }

    @Test(enabled = false)
    @TestCase({"EPMCMBIBPC-706"})
    public void listOfTestCasesForValidationPipelineInRemoteRepository() {
        open(C.ROOT_ADDRESS);
        //EPMCMBIBPC-284
        navigationMenu()
                .library()
                .createFolder(folderNameForPipelineEditingTest);
        //EPMCMBIBPC-285
        navigationMenu()
                .library()
                .cd(folderNameForPipelineEditingTest)
                .clickCreatePipelineFromTemplate(PYTHON)
                .setName(pipelineForPipelineEditingTest)
                .openRepositorySettings()
                .setRepository(repository)
                .setToken(token)
                .create()
                .validatePipeline(pipelineForPipelineEditingTest);

        // it needs to be rewritten if it will be used
        PipelineEditingTest pipelineEditingTest = new PipelineEditingTest();
        //EPMCMBIBPC-286
        pipelineEditingTest.pythonPipelineValidationTest();
        //EPMCMBIBPC-295
        pipelineEditingTest.createPipelineFileTest();
        //EPMCMBIBPC-290
        pipelineEditingTest.editPipelineFileNameTest();
        //EPMCMBIBPC-289
        pipelineEditingTest.editPipelineFileTest();
        //EPMCMBIBPC-291
        pipelineEditingTest.deletePipelineFileTest();
        sleep(5, SECONDS);
        //EPMCMBIBPC-292
        pipelineEditingTest.editPipelineTest();
        sleep(5, SECONDS);
        //EPMCMBIBPC-287 and EPMCMBIBPC-288
        pipelineEditingTest.cleanUp();
    }

    @Test
    @TestCase({"EPMCMBIBPC-709"})
    public void tryCreateWithInvalidToken() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .clickCreatePipelineButton()
                .setName(pipelineName)
                .openRepositorySettings()
                .setRepository(repository)
                .setToken(invalidToken)
                .createWithIncorrectData()
                .ensureTitleIs("Repository does not exist. Create?")
                .ok()
                .messageShouldAppear("Exception while trying to connect to Gitlab API: Failed to create GIT repository: 401 Unauthorized")
                .cancel();
    }

    @Test
    @TestCase({"EPMCMBIBPC-710"})
    public void tryToCreateNewProjectInRemoteRepository() {
        open(C.ROOT_ADDRESS);
        navigationMenu()
                .library()
                .clickCreatePipelineButton()
                .openRepositorySettings()
                .setName(pipelineInNonExistingRepository)
                .setRepository(nonExistingRepository)
                .setToken(token)
                .createWithIncorrectData()
                .ensureTitleIs("Repository does not exist. Create?")
                .ok()
                .sleep(3, SECONDS)
                .messageShouldNotContain("404", "200")
                .cancel()
                .validatePipelineIsNotPresent(pipelineInNonExistingRepository);
    }

    private void clickDeleteWithPauses() {
        sleep(20, SECONDS);
        $(byId("edit-pipeline-form-delete-button")).shouldBe(visible, enabled).click();
        $("#edit-pipeline-delete-dialog-delete-button").shouldBe(visible, enabled).click();
    }
}
