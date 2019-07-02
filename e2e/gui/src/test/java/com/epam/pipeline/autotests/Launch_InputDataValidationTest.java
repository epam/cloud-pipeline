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

import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.ao.LogAO.Status.SUCCESS;
import static com.epam.pipeline.autotests.ao.LogAO.logMessage;
import static com.epam.pipeline.autotests.ao.LogAO.taskWithName;
import static com.epam.pipeline.autotests.ao.Primitive.SAVE;
import static com.epam.pipeline.autotests.ao.Primitive.STATUS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Launch_InputDataValidationTest extends AbstractAutoRemovingPipelineRunningTest implements Navigation {

    private static final String LAUNCH_SCRIPT = "/inputDataPipelineValidation.sh";
    private static final String PREFIX = C.STORAGE_PREFIX;

    private String storage = "epmcmbi-test-storage-2158-" + Utils.randomSuffix();
    private final String outFolder = "out";
    private final String inFolder = "in";
    private final String commonFolder = "common";
    private final String commonFolder2 = "common2";
    private final String commonFolder3 = "common3";
    private final String commonFile1 = "common1.txt";
    private final String commonFile2 = "common2.txt";
    private final String commonFile3 = "common3.txt";
    private final String commonFile4 = "common4.txt";
    private final String inFile = "in.txt";
    private final String nonCopiedFile = "nonCopied.txt";
    private final String fileText = "editable file text " + Utils.randomSuffix();
    private final String outputPath = String.format("%s://%s/%s", PREFIX, storage, outFolder);
    private final String inputPath = String.format("%s://%s/%s/%s", PREFIX, storage, inFolder, inFile);
    private final String commonPath = String.format("%s://%s/%s, %s://%s/%s/%s", PREFIX, storage, commonFolder,
            PREFIX, storage, commonFolder2, commonFile3);

    @BeforeClass
    public void createInitialResources() {
        navigationMenu()
                .library()
                .createStorage(storage)
                .selectStorage(storage)
                .createFolder(outFolder)
                .createFolder(commonFolder)
                .cd(commonFolder)
                .createAndEditFile(commonFile1, fileText)
                .createAndEditFile(commonFile2, fileText)
                .cd("..")
                .createFolder(commonFolder2)
                .cd(commonFolder2)
                .createAndEditFile(commonFile4, fileText)
                .createAndEditFile(commonFile3, fileText)
                .createFolder(commonFolder3)
                .cd("..")
                .createFolder(inFolder)
                .cd(inFolder)
                .createAndEditFile(inFile, fileText)
                .createAndEditFile(nonCopiedFile, fileText);

        navigationMenu()
                .library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .codeTab()
                .clickOnFile(Utils.getFileNameFromPipelineName(getPipelineName(), "sh"))
                .editFile(code -> Utils.readResourceFully(LAUNCH_SCRIPT))
                .saveAndCommitWithMessage("test: Prepare pipeline script")
                .configurationTab()
                .editConfiguration("default", profile -> {
                    profile
                        .addOutputParameter(outFolder, outputPath)
                        .addInputParameter(inFolder, inputPath)
                        .addCommonParameter(commonFolder, commonPath);
                    profile
                        .click(SAVE);
                })
                .sleep(5, SECONDS)
                .refresh();
    }

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        open(C.ROOT_ADDRESS);
        library()
                .removePipeline(getPipelineName())
                .removeStorage(storage);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-2158"})
    public void inputDataInPipelineValidation() {
        library()
                .clickOnPipeline(getPipelineName())
                .firstVersion()
                .runPipeline()
                .launch(this)
                .sleep(1, SECONDS)
                .showLog(getRunId())
                .waitForCompletion()
                .click(taskWithName("Task1"))
                .ensure(logMessage("Running shell pipeline"), visible)
                .ensure(STATUS, SUCCESS.reached);
        runsMenu()
                .completedRuns()
                .showLog(getRunId())
                .ensure(taskWithName(getPipelineName()), SUCCESS.reached);

        library()
                .selectStorage(storage)
                .cd(outFolder)
                .validateElementIsPresent(commonFolder)
                .cd(commonFolder)
                .validateElementIsPresent(commonFile1)
                .validateElementIsPresent(commonFile2)
                .cd("..")
                .cd(commonFolder2)
                .validateElementIsPresent(commonFile3)
                .cd("..")
                .cd(inFolder)
                .validateElementIsPresent(inFile)
                .validateElementNotPresent(nonCopiedFile);
    }
}
