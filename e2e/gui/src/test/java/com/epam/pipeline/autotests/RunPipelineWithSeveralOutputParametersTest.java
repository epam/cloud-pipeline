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
import com.epam.pipeline.autotests.mixins.StorageHandling;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.open;
import static java.util.concurrent.TimeUnit.SECONDS;

public class RunPipelineWithSeveralOutputParametersTest extends AbstractAutoRemovingPipelineRunningTest
        implements StorageHandling {

    private final static String shellTemplate = "/fileKeeper.sh";
    private final static String shellCreatedFile = "storage_rules_test.test";
    private final static String storage = "several-param-storage-" + Utils.randomSuffix();
    private final static String storageFolder = "folder-" + Utils.randomSuffix();
    private final static String anotherStorageFolder = "another-folder-" + Utils.randomSuffix();

    @BeforeClass
    public void initStorage() {
        createStorage(storage)
                .createFolder(storageFolder)
                .createFolder(anotherStorageFolder);
    }

    @AfterClass(alwaysRun = true)
    public void deleteStorage() {
        open(C.ROOT_ADDRESS);
        removeStorage(storage);
    }

    @Test
    @TestCase("EPMCMBIBPC-935")
    public void runPipelineWithSeveralOutputParameters() {
        navigationMenu()
                .createPipeline(Template.SHELL, getPipelineName())
                .firstVersion()
                .codeTab()
                .clearAndFillPipelineFile(
                        getPipelineName().toLowerCase() + ".sh",
                        Utils.readResourceFully(shellTemplate)
                )
                .sleep(2, SECONDS)
                .runPipeline()
                .clickAddOutputParameter()
                .setName("parameter")
                .openPathAdditionDialog()
                .chooseStorage(storage)
                .addToSelection(storageFolder)
                .addToSelection(anotherStorageFolder)
                .ok()
                .close()
                .launch(this)
                .showLog(getRunId())
                .waitForCompletion();

        navigationMenu()
                .library()
                .selectStorage(storage)
                .cd(storageFolder)
                .ensure(byText(shellCreatedFile), visible)
                .cd("..")
                .cd(anotherStorageFolder)
                .ensure(byText(shellCreatedFile), visible);
    }
}
