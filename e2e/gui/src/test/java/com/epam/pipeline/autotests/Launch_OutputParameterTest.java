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
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;

public class Launch_OutputParameterTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final String LAUNCH_SCRIPT = "/fileKeeper.sh";
    private static final String STORAGE_FOLDER = "storage_rules_folder";

    private final String storage = "epmcmbi-test-storage-362-" + Utils.randomSuffix();

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        navigationMenu()
            .library()
            .removeStorage(storage);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-362"})
    public void preparePipeline() {
        final String pipelineFileName = Utils.getFileNameFromPipelineName(getPipelineName(), "sh");
        navigationMenu()
            .library()
            .createStorage(storage);
        navigationMenu()
            .createPipeline(Template.SHELL, getPipelineName())
            .firstVersion()
            .codeTab()
            .clearAndFillPipelineFile(pipelineFileName, Utils.readResourceFully(LAUNCH_SCRIPT));
    }

    @Test(dependsOnMethods = {"preparePipeline"})
    @TestCase(value = {"EPMCMBIBPC-362"})
    public void launchPipelineWithNewParameter() {
        final String pathToFile = String.format("%s://%s/%s/%s", C.STORAGE_PREFIX, storage, STORAGE_FOLDER,
                getPipelineName());
        new PipelineCodeTabAO(getPipelineName())
            .runPipeline()
            .addOutputParameter("output", pathToFile)
            .launchAndWaitUntilFinished(this);
    }

    @Test(dependsOnMethods = {"launchPipelineWithNewParameter"})
    @TestCase(value = {"EPMCMBIBPC-362"})
    public void shouldSaveOutputToTheStorage() throws InterruptedException {
        Thread.sleep(6000);
        final List<String> filesAndFolders = navigationMenu()
            .library()
            .selectStorage(storage)
            .cd(STORAGE_FOLDER)
            .cd(getPipelineName())
            .filesAndFolders();

        assertEquals(filesAndFolders.get(0), "..");
        assertEquals(filesAndFolders.get(1), "storage_rules_test.test");
    }
}
