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

import com.epam.pipeline.autotests.ao.PipelineLibraryContentAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;

public class PipelineEditingRepositoryTest extends AbstractBfxPipelineTest implements Navigation {

    private final String fileName = String.format("pipeline-editing-repository-test-%d.file", Utils.randomSuffix());
    private final String pipelineName = "pipeline-editing-repository-test-pipeline-" + Utils.randomSuffix();
    private final String anotherPipelineName = "pipeline-editing-repository-test-pipeline-" + Utils.randomSuffix();

    private String repository;

    @BeforeClass
    public void createPipeline() {
        library()
                .createPipeline(Template.SHELL, pipelineName)
                .clickOnPipeline(pipelineName)
                .draft()
                .codeTab()
                .createFile(fileName);
    }

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        library()
            .removePipeline(anotherPipelineName);
    }

    @Test
    @TestCase({"EPMCMBIBPC-840"})
    public void validateClosingPipelineEditPopupWithRepositorySettingsOpened() {
        //EPMCMBIBPC-832 validation
        library()
                .clickOnPipeline(pipelineName)
                .clickEditButton()
                .openRepositorySettings()
                .close();

        pipelinesLibraryContent()
                .validatePipeline(pipelineName);
    }

    @Test(dependsOnMethods = "validateClosingPipelineEditPopupWithRepositorySettingsOpened")
    @TestCase({"EPMCMBIBPC-707"})
    public void pipelineUnregisteringShouldRemoveItFromLibrary() {
        repository =
                library()
                        .clickOnPipeline(pipelineName)
                        .clickEditButton()
                        .openRepositorySettings()
                        .getRepository();

        pipelineEditForm()
                .delete()
                .unregister()
                .ensure(byText(pipelineName), not(visible));
    }

    @Test(dependsOnMethods = "pipelineUnregisteringShouldRemoveItFromLibrary")
    @TestCase({"EPMCMBIBPC-708"})
    public void addExistingInRepositoryPipeline() {
        library()
                .clickCreatePipelineButton()
                .setName(anotherPipelineName)
                .openRepositorySettings()
                .setRepository(repository)
                .create();

        pipelinesLibraryContent()
                .clickOnPipeline(anotherPipelineName)
                .draft()
                .codeTab()
                .shouldContainElement(fileName);
    }

    private PipelinesLibraryAO pipelinesLibraryContent() {
        return new PipelinesLibraryAO();
    }

    private PipelineLibraryContentAO.PipelineEditPopupAO pipelineEditForm() {
        return new PipelineLibraryContentAO(pipelineName).new PipelineEditPopupAO();
    }
}
