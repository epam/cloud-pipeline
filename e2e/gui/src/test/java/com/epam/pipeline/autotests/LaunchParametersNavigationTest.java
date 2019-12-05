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
import com.epam.pipeline.autotests.mixins.FolderHandling;
import com.epam.pipeline.autotests.mixins.StorageHandling;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static com.codeborne.selenide.Condition.not;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.open;
import static java.util.concurrent.TimeUnit.SECONDS;

public class LaunchParametersNavigationTest extends AbstractAutoRemovingPipelineRunningTest
        implements StorageHandling, FolderHandling {

    private final static String shellTemplate = "/fileKeeper.sh";
    private final static String storage = "params-navigation-storage-" + Utils.randomSuffix();
    private final static String storageFolder = "storage-folder-" + Utils.randomSuffix();
    private final static String folder = "folder-" + Utils.randomSuffix();
    private final static String innerFolder = "inner-folder-" + Utils.randomSuffix();
    private final static String innerStorage = "inner-storage-" + Utils.randomSuffix();

    @BeforeClass
    public void initStorage() {
        createStorage(storage)
                .createFolder(storageFolder);
    }

    @BeforeClass
    public void initFolder() {
        createFolder(folder)
                .createFolder(innerFolder)
                .cd(innerFolder)
                .createStorage(innerStorage);
    }

    @AfterClass(alwaysRun = true)
    public void deleteStorage() {
        open(C.ROOT_ADDRESS);
        removeStorage(storage);
    }

    @AfterClass(alwaysRun = true)
    public void deleteFolder() {
        open(C.ROOT_ADDRESS);
        removeStorage(innerStorage, folder, innerFolder);
        removeFolder(folder, innerFolder);
        removeFolder(folder);
    }

    @Test
    @TestCase({"EPMCMBIBPC-932"})
    public void navigationInSetParametersPopupOnLaunchTab() {
        navigationMenu()
                .library()
                .createPipeline(Template.SHELL, getPipelineName())
                .clickOnPipeline(getPipelineName())
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
                .openFolder(folder)
                .ensure(byText(innerFolder), visible)
                .closeFolder(folder)
                .ensure(byText(innerFolder), not(visible))
                .chooseStorage(storage)
                .ensure(byText(storageFolder), visible)
                .cancel();
    }
}
