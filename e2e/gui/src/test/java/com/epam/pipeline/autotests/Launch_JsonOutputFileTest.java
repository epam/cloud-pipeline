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
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.utils.Json.selectProfileWithName;
import static com.epam.pipeline.autotests.utils.Json.transferringJsonToObject;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Launch_JsonOutputFileTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final String LAUNCH_SCRIPT = "/fileKeeper.sh";
    private static final String CONFIG_JSON = "config.json";
    private static final String STORAGE_RULES_FOLDER = "storage_rules_folder";
    private static final String FILE_TO_STORE_NAME = "storage_rules_test.test";

    private final String storage = "epmcmbi-test-storage-360-" + Utils.randomSuffix();

    @BeforeClass
    public void createPipeline() {
        navigationMenu()
            .library()
            .createStorage(storage)
            .createPipeline(Template.SHELL, getPipelineName());
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        navigationMenu()
            .library()
            .removeStorage(storage);
    }

    @Test
    @TestCase("EPMCMBIBPC-360")
    public void launchPipelineWithJSONParameter() {
        final String pathToFile = String.format("%s://%s/%s/%s", C.STORAGE_PREFIX, storage, STORAGE_RULES_FOLDER,
                getPipelineName());
        final String pipelineScript = Utils.getFileNameFromPipelineName(getPipelineName(), "sh");
        final String parameterName = "result";
        navigationMenu()
            .library()
            .clickOnPipeline(getPipelineName())
            .firstVersion()
            .codeTab()
            .clickOnFile(pipelineScript)
            .editFile(code -> Utils.readResourceFully(LAUNCH_SCRIPT))
            .saveAndCommitWithMessage("test: Replace script with custom one")
            .sleep(1, SECONDS)
            .clickOnFile(CONFIG_JSON)
            .sleep(1, SECONDS)
            .editFile(transferringJsonToObject(profiles -> {
                final ConfigurationProfile profile = selectProfileWithName("default", profiles);
                profile.configuration.parameters.put(parameterName, Parameter.required("output", pathToFile));
                return profiles;
            }))
            .saveAndCommitWithMessage("test: Add required output parameter named result")
            .runPipeline()
            .validateThereIsParameterOfType(parameterName, pathToFile, ParameterType.OUTPUT, true)
            .launchAndWaitUntilFinished(this);
    }

    @Test(dependsOnMethods = {"launchPipelineWithJSONParameter"})
    @TestCase("EPMCMBIBPC-360")
    public void validateOutputRun() {
        navigationMenu()
            .library()
            .selectStorage(storage)
            .navigateUsingAddressBar(String.format("%s/%s", STORAGE_RULES_FOLDER, getPipelineName()))
            .validateElementIsPresent(FILE_TO_STORE_NAME);
    }
}