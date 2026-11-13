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

import com.epam.pipeline.autotests.ao.PipelineLibraryContentAO;
import com.epam.pipeline.autotests.ao.PipelinesLibraryAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import java.util.List;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.disappear;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.epam.pipeline.autotests.ao.Primitive.REPOSITORY;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.modalWithTitle;
import static org.testng.Assert.assertEquals;

public class PipelineEditingRepositoryTest extends AbstractBfxPipelineTest implements Navigation {

    private final String fileName = String.format("pipeline-editing-repository-test-%d.file", Utils.randomSuffix());
    private final String pipelineName = "pipeline-editing-repository-test-pipeline-" + Utils.randomSuffix();
    private final String anotherPipelineName = "pipeline-editing-repository-test-pipeline-" + Utils.randomSuffix();

    private String repository;
    private List<String> originalFiles;

    @BeforeClass
    public void createPipeline() {
        originalFiles = library()
                .createPipeline(Template.SHELL, pipelineName)
                .clickOnPipeline(pipelineName)
                .draft()
                .codeTab()
                .createFile(fileName)
                .shouldContainFile(fileName)
                .fileNames();
        library().clickOnPipeline(pipelineName).refresh();
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
                .ensure(REPOSITORY, visible)
                .close()
                .ensure(modalWithTitle("Edit pipeline info"), disappear);

        pipelinesLibraryContent()
                .validatePipeline(pipelineName);
    }

    @Test(dependsOnMethods = "validateClosingPipelineEditPopupWithRepositorySettingsOpened")
    @TestCase({"EPMCMBIBPC-707"})
    public void pipelineUnregisteringShouldRemoveItFromLibrary() {
        final PipelineLibraryContentAO pipeline = library()
                .clickOnPipeline(pipelineName);
        repository = pipeline.getGitRepositoryUrl();

        pipeline.clickEditButton()
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

        final List<String> files = pipelinesLibraryContent()
                .clickOnPipeline(anotherPipelineName)
                .draft()
                .codeTab()
                .shouldContainFile(fileName)
                .fileNames();
        assertEquals(files, originalFiles);
    }

    private PipelinesLibraryAO pipelinesLibraryContent() {
        return new PipelinesLibraryAO();
    }
}
