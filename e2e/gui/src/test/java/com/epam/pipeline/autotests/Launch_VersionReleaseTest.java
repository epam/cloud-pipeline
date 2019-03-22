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

import com.epam.pipeline.autotests.ao.PipelineCodeTabAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;

public class Launch_VersionReleaseTest extends AbstractAutoRemovingPipelineRunningTest {

    public final String NEW_VERSION = "1.1.1-" + getPipelineName();
    public static final String JSON_CONTENT = "invalid json";

    @Test
    @TestCase(value = {"EPMCMBIBPC-380"})
    public void shouldReleaseNewVersion() {
        navigationMenu()
                .createPipeline(Template.SHELL, getPipelineName())
                .releaseFirstVersion(NEW_VERSION)
                .assertVersion(NEW_VERSION)
                .assertThereIsNoReleaseButton()
                .firstVersion()
                .codeTab();
    }

    @Test(dependsOnMethods = {"shouldReleaseNewVersion"})
    @TestCase(value = {"EPMCMBIBPC-378"})
    public void exceptionShouldBeHandledWhenInvalidJson() {
        new PipelineCodeTabAO(getPipelineName())
                .clearAndFillPipelineFile("config.json", JSON_CONTENT)
                .runPipeline()
                .validateException("Failed to load pipeline configuration from file: " + JSON_CONTENT);
    }

    @Test(dependsOnMethods = {"exceptionShouldBeHandledWhenInvalidJson"})
    @TestCase(value = {"EPMCMBIBPC-383"})
    public void shouldCreateNewVersionAfterCommit() {
        navigationMenu()
                .library()
                .clickOnPipeline(getPipelineName())
                .assertVersionNot(NEW_VERSION)
                .assertReleaseButton()
                .firstVersion()
                .codeTab()
                .clickOnFile("config.json")
                .shouldContainInCode(JSON_CONTENT)
                .close();
    }

    @Test(dependsOnMethods = {"shouldCreateNewVersionAfterCommit"})
    @TestCase(value = {"EPMCMBIBPC-384"})
    public void shouldNotBeAbleToEditReleasedVersionAfterCommit() {
        new PipelineCodeTabAO(getPipelineName())
                .clearAndFillPipelineFile("config.json", JSON_CONTENT + " some salt");

        navigationMenu()
                .library()
                .clickOnPipeline(getPipelineName())
                .version(NEW_VERSION)
                .codeTab()
                .assertThereIsNoEditButtons()
                .assertFileNotEditable("config.json")
                .assertFileNotEditable(Utils.getFileNameFromPipelineName(getPipelineName(), "sh"))
                .documentsTab()
                .validateDocumentsNotEditable();
    }
}
