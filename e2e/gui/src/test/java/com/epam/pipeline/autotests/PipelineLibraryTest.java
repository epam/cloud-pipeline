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
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.ao.DocumentTabAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.ConfigurationProfile;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.Primitive.FILE_PREVIEW;
import static com.epam.pipeline.autotests.utils.Json.selectProfileWithName;
import static com.epam.pipeline.autotests.utils.Json.transferringJsonToObject;
import static com.epam.pipeline.autotests.utils.Utils.getCurrentURL;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertNotEquals;

public class PipelineLibraryTest extends AbstractSeveralPipelineRunningTest implements Authorization {
    private final String pipeline = "pipeline-1353-" + Utils.randomSuffix();
    private final String readMeText = "\nnew string";
    private final String testInstanceDisk = "26";

    @AfterClass(alwaysRun = true)
    public void removePipelines() {
        library().removePipelineIfExists(pipeline);
    }

    @Test
    @TestCase(value = {"1353"})
    public void checkPipelineSourcesForPreviousDraftVersions() {
        final String[] instanceDisk = new String[1];
        library()
                .createPipeline(pipeline)
                .clickOnDraftVersion(pipeline);
        String pipelineDraftVersionUrl = getCurrentURL();
        new DocumentTabAO(pipeline)
                .addStringToReadMeFile(readMeText)
                .saveAndCommitWithMessage("test: Change ReadMe file")
                .sleep(3, SECONDS)
                .ensure(FILE_PREVIEW, text(readMeText));
        String pipelineNewVersionUrl1 = getCurrentURL();
        assertNotEquals(pipelineDraftVersionUrl, pipelineNewVersionUrl1,
                "Initial and new page addresses should be different");

        new DocumentTabAO(pipeline)
                .codeTab()
                .clickOnFile("config.json")
                .editFile(transferringJsonToObject(profiles -> {
                    final ConfigurationProfile profile = selectProfileWithName("default", profiles);
                    instanceDisk[0] = profile.configuration.instanceDisk;
                    profile.configuration.instanceDisk = testInstanceDisk;
                    return profiles;
                }))
                .saveAndCommitWithMessage("test: Change instance_disk")
                .sleep(2, SECONDS)
                .clickOnFile("config.json")
                .shouldContainInCode(format("\"instance_disk\" : \"%s\"", testInstanceDisk))
                .close();
        String pipelineNewVersionUrl2 = getCurrentURL();
        assertNotEquals(pipelineNewVersionUrl2, pipelineNewVersionUrl1,
                "Initial and new page addresses should be different");
        open(pipelineDraftVersionUrl);
        new DocumentTabAO(pipeline)
                .ensure(FILE_PREVIEW, not(text(readMeText)))
                .codeTab()
                .clickOnFile("config.json")
                .shouldContainInCode(format("\"instance_disk\" : \"%s\"", instanceDisk));
        open(pipelineNewVersionUrl1);
        new DocumentTabAO(pipeline)
                .ensure(FILE_PREVIEW, text(readMeText))
                .codeTab()
                .clickOnFile("config.json")
                .shouldContainInCode(format("\"instance_disk\" : \"%s\"", instanceDisk))
                .close();
    }
}
