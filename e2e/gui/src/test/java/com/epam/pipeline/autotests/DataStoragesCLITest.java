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

import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import com.epam.pipeline.autotests.utils.listener.CloudProviderOnly;
import com.epam.pipeline.autotests.utils.listener.ConditionalTestAnalyzer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.ao.Primitive.CLOUD_REGION;
import static java.lang.String.format;

@Listeners(value = ConditionalTestAnalyzer.class)
public class DataStoragesCLITest extends AbstractSinglePipelineRunningTest
        implements Authorization, Navigation {

    private String storage1 = "dataStorageCLI-" + Utils.randomSuffix();
    private String storage2 = "dataStorageCLI-" + Utils.randomSuffix();
    private String fileFor1469 = "fileFromStorage1";
    private String anotherCloudRegion = C.ANOTHER_CLOUD_REGION;
    private final String registry = C.DEFAULT_REGISTRY;
    private final String tool = C.TESTING_TOOL_NAME;
    private final String group = C.DEFAULT_GROUP;
    private String pathStorage1;

    @AfterClass(alwaysRun = true)
    public void removeStorages() {
        Utils.removeStorages(this, storage1, storage2);
    }

    @Test
    @TestCase(value = {"1469"})
    @CloudProviderOnly(values = {Cloud.AWS})
    public void checkTransferBetweenRegions() {
        String pathStorage1 = library()
                .createStorage(storage1)
                .selectStorage(storage1)
                .createAndEditFile(fileFor1469, "description1")
                .getStoragePath();
        String pathStorage2 = library()
                .clickOnCreateStorageButton()
                .setStoragePath(storage2)
                .selectValue(CLOUD_REGION, anotherCloudRegion)
                .ok()
                .selectStorage(storage2)
                .getStoragePath();
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .setDefaultLaunchOptions()
                .launchTool(this, Utils.nameWithoutGroup(tool))
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(format("pipe storage cp %s/%s %s/", pathStorage1, fileFor1469, pathStorage2))
                        .assertPageContains("100%")
                        .close());
        library()
                .selectStorage(storage2)
                .validateElementIsPresent(fileFor1469);
        library()
                .selectStorage(storage1)
                .rmFile(fileFor1469)
                .validateCurrentFolderIsEmpty();
        runsMenu()
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(format("pipe storage mv %s/%s %s/", pathStorage2, fileFor1469, pathStorage1))
                        .waitUntilTextAppears("100%")
                        .close());
        library()
                .selectStorage(storage2)
                .validateHeader(storage2)
                .validateCurrentFolderIsEmpty();
        library()
                .selectStorage(storage1)
                .validateElementIsPresent(fileFor1469);
    }
}
