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
import com.epam.pipeline.autotests.ao.PipelineCodeTabAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.stream.Stream;

import static com.epam.pipeline.autotests.utils.Json.editingConfiguration;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Launch_DifferentTypesParametersValidationTest extends AbstractAutoRemovingPipelineRunningTest {

    private static final String PATH = "path";
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    private static final String COMMON = "common";

    private final String storage = "epmcmbi-test-storage-367-" + Utils.randomSuffix();

    @BeforeClass
    public void createStorageWithFolders() {
        navigationMenu()
            .library()
            .createStorage(storage)
            .selectStorage(storage)
            .createFolder(PATH)
            .createFolder(INPUT)
            .createFolder(OUTPUT)
            .createFolder(COMMON);
    }

    @AfterClass(alwaysRun = true)
    public void removeStorage() {
        navigationMenu()
            .library()
            .removeStorageIfExists(storage);
    }

    @Test
    @TestCase("EPMCMBIBPC-367")
    public void preparePipeline() {
        navigationMenu()
            .createPipeline(Template.SHELL, getPipelineName())
            .firstVersion()
            .codeTab()
            .clickOnFile("config.json")
            .editFile(editingConfiguration("default", configuration -> {
                final ObjectNode parameters = configuration.with("parameters");
                Stream.of(PATH, INPUT, OUTPUT, COMMON).forEach(type -> parameters.putObject(type)
                        .put("type", type)
                        .put("value", folderPath(type))
                        .put("required", false));
            }))
            .saveAndCommitWithMessage("test: Add path, input, output and common parameters");
    }

    @Test(dependsOnMethods = {"preparePipeline"})
    @TestCase("EPMCMBIBPC-367")
    public void validateParameters() {
        new PipelineCodeTabAO(getPipelineName())
            .sleep(2, SECONDS)
            .runPipeline()
            .validateThereIsParameterOfType(OUTPUT, folderPath(OUTPUT), ParameterType.OUTPUT, false)
            .validateThereIsParameterOfType(INPUT, folderPath(INPUT), ParameterType.INPUT, false)
            .validateThereIsParameterOfType(COMMON, folderPath(COMMON), ParameterType.COMMON, false)
            .validateThereIsParameterOfType(PATH, folderPath(PATH), ParameterType.PATH, false);
    }

    private String folderPath(final String folder) {
        return String.format("%s://%s/%s", C.STORAGE_PREFIX, storage, folder);
    }
}
