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

import com.epam.pipeline.autotests.ao.ParameterType;
import com.epam.pipeline.autotests.ao.StorageRulesTabAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.*;

import java.util.List;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.utils.Json.selectProfileWithName;
import static com.epam.pipeline.autotests.utils.Json.transferringJsonToObject;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;

public class StorageRulesTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final String STORAGE_RULES_FOLDER = "storage_rules_folder";
    private static final String FILE_TO_STORE_NAME = "storage_rules_test.test";
    private static final String LAUNCH_SCRIPT = "/saveFilesScript.sh";
    private final String storage = "epmcmbi-test-storage-363-" + Utils.randomSuffix();

    @AfterClass(alwaysRun = true)
    public void removeOutputFolder() {
        navigationMenu()
            .library()
            .removeStorage(storage);
    }

    @Test
    @TestCase("EPMCMBIBPC-363")
    public void preparePipeline() {
        final String pathToFile = String.format("%s://%s/%s/%s/", C.STORAGE_PREFIX, storage, STORAGE_RULES_FOLDER,
                getPipelineName());
        final String parameterName = "result";
        navigationMenu()
                .library()
                .createStorage(storage);

        navigationMenu()
                .createPipeline(Template.SHELL, getPipelineName())
                .firstVersion()
                .codeTab()
                .clickOnFile(getPipelineName().toLowerCase() + ".sh")
                .editFile(code -> Utils.readResourceFully(LAUNCH_SCRIPT))
                .saveAndCommitWithMessage("test: Prepare pipeline script")
                .clickOnFile("config.json")
                .sleep(3, SECONDS)
                .editFile(transferringJsonToObject(profiles -> {
                    final ConfigurationProfile profile = selectProfileWithName("default", profiles);
                    profile.configuration.parameters.put(parameterName, Parameter.required("output", pathToFile));
                    return profiles;
                }))
                .saveAndCommitWithMessage("test: Prepare configuration file")
                .storageRulesTab()
                .deleteStorageRule("*")
                .addNewStorageRule("*.test");
    }

    @Test(dependsOnMethods = {"preparePipeline"})
    @TestCase("EPMCMBIBPC-363")
    public void runPipelineAndWaitUntilFinished() {
        final String pathToFile = String.format("%s://%s/%s/%s/", C.STORAGE_PREFIX, storage, STORAGE_RULES_FOLDER,
                getPipelineName());
        new StorageRulesTabAO(getPipelineName())
                .runPipeline()
                .validateThereIsParameterOfType("result", pathToFile, ParameterType.OUTPUT,true)
                .waitUntilLaunchButtonAppear()
                .launchAndWaitUntilFinished(this);
    }

    @Test(dependsOnMethods = {"runPipelineAndWaitUntilFinished"})
    @TestCase("EPMCMBIBPC-363")
    public void outputShouldBeValid() {
        List<String> files = navigationMenu()
                .library()
                .selectStorage(storage)
                .navigateUsingAddressBar(String.format("%s/%s", STORAGE_RULES_FOLDER, getPipelineName()))
                .filesAndFolders();

        assertEquals(files.size(), 2);
        assertEquals(files.get(0), "..");
        assertEquals(files.get(1), FILE_TO_STORE_NAME);
    }
}