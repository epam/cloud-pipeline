/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import com.epam.pipeline.autotests.utils.listener.ConditionalTestAnalyzer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import static java.util.concurrent.TimeUnit.SECONDS;
import static com.epam.pipeline.autotests.utils.Utils.sleep;

import static com.codeborne.selenide.Selenide.open;
import static java.lang.String.format;

public class DataStoragesCLITest extends AbstractSeveralPipelineRunningTest
        implements Authorization, Navigation {

    private final String registry = C.DEFAULT_REGISTRY;
    private final String anotherGroup = C.ANOTHER_GROUP;
    private final String testTool = format("%s/%s", anotherGroup, C.TEST_DOCKER_IMAGE);
    private final String rootHost = "root@pipeline";
    private final String testStorage = format("test-storage-2847-%s", Utils.randomSuffix());
    private final String testFile = "testFile";

    @AfterClass(alwaysRun = true)
    public void removeStorages() {
        open(C.ROOT_ADDRESS);
        Utils.removeStorages(this, testStorage);
    }

    @Test
    @TestCase(value = {"2847"})
    public void introduceExtendedAttributesSupportInPipeFuse() {
        String[] commands = {
                "yum install attr -y",
                format("setfattr -n user.key1 -v value1 /cloud-data/%s/%s", testStorage, testFile),
                format("setfattr -n user.key2 -v value2 /cloud-data/%s/%s", testStorage, testFile),
                format("getfattr /cloud-data/%s/%s", testStorage, testFile),
                format("getfattr -n user.key1 /cloud-data/%s/%s", testStorage, testFile),
                format("setfattr -n another.key1 -v value1 /cloud-data/%s/%s", testStorage, testFile),
                format("getfattr -n another.key1 /cloud-data/%s/%s", testStorage, testFile)
        };
        library()
                .createStorage(testStorage)
                .selectStorage(testStorage)
                .createFileWithContent(testFile, testFile);
        tools()
                .perform(registry, anotherGroup, testTool, ToolTab::runWithCustomSettings)
                .setPriceType(ON_DEMAND)
                .launch(this)
                .showLog(getLastRunId())
                .waitForSshLink()
                .sshInNewTab(shell -> {
                    shell
                            .waitUntilTextAppears(getLastRunId())
                            .execute(commands[0])
                            .sleep(60, SECONDS)
                            .assertPackageInstallationCompleted(format("pipeline-%s", getLastRunId()))
                            .execute(commands[1])
                            .assertNextStringIsVisible(commands[1], rootHost)
                            .execute(commands[2])
                            .assertNextStringIsVisible(commands[2], rootHost)
                            .execute(commands[3])
                            .assertNextStringIsVisible(commands[3], rootHost)
                            .assertPageAfterCommandContainsStrings(format("file: cloud-data/%s/%s", testStorage, testFile),
                                    "user.key1", "user.key2")
                            .execute(commands[4])
                            .assertNextStringIsVisible(commands[4], rootHost)
                            .assertPageAfterCommandContainsStrings(commands[4],
                                    format("file: cloud-data/%s/%s", testStorage, testFile), "user.key1=\"value1\"")
                            .execute(commands[5])
                            .assertNextStringIsVisible(commands[5], rootHost)
                            // the error line may show the path with or without its leading "/"
                            .assertPageAfterCommandContainsStrings(commands[5],
                                    format("cloud-data/%s/%s: Operation not supported", testStorage, testFile))
                            .execute(commands[6])
                            .assertNextStringIsVisible(commands[6], rootHost)
                            .assertPageAfterCommandContainsStrings(commands[6],
                                    format("cloud-data/%s/%s", testStorage, testFile), "Operation not supported");
                });
    }
}
