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

import com.epam.pipeline.autotests.ao.MetadataSectionAO;
import com.epam.pipeline.autotests.ao.PipelineLibraryContentAO;
import com.epam.pipeline.autotests.ao.Template;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.*;

public class ObjectMetadataPipelineTest extends AbstractBfxPipelineTest implements Navigation {

    private final String folderWithPipeline = "object-metadata-test-folder-with-pipeline-" + Utils.randomSuffix();
    private final String pipelineName = "object-metadata-test-pipeline-" + Utils.randomSuffix();

    private final String key1 = "1";
    private final String value1 = "2";
    private final String key2 = "2";
    private final String value2 = "3";
    private final String key3 = "qwerty";
    private final String value3 = "asdfg";
    private final String key4 = "very very very long key with spaces";
    private final String value4 = "a";
    private final String key5 = "a";
    private final String value5 = "text with spaces";

    @BeforeClass
    public void createAllEntities() {
        navigationMenu()
                .library()
                .createFolder(folderWithPipeline)
                .cd(folderWithPipeline)
                .createPipeline(Template.SHELL, pipelineName);
    }

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        navigationMenu()
                .library()
                .cd(folderWithPipeline)
                .removePipeline(pipelineName);
        navigationMenu()
                .library()
                .removeFolder(folderWithPipeline);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-866"})
    public void addMetadataToPipelineValidation() {
        navigationMenu()
                .library()
                .clickOnFolder(folderWithPipeline)
                .clickOnPipeline(pipelineName)
                .showMetadata()
                .ensure(ADD_KEY, visible)
                .addKeyWithValue(key1, value1)
                .ensure(REMOVE_ALL_KEYS, visible)
                .selectKey(key1)
                .ensure(DELETE_ICON, visible)
                .validateKeyBackgroundIsGrey();
    }

    @Test(dependsOnMethods = "addMetadataToPipelineValidation")
    @TestCase(value = {"EPMCMBIBPC-871"})
    public void checkMetadataForPipelineInFolder() {
    navigationMenu()
                .library()
                .clickOnFolder(folderWithPipeline)
                .showMetadata()
                .assertNumberOfKeysIs(0);
    }

    @Test(dependsOnMethods = "checkMetadataForPipelineInFolder")
    @TestCase(value = {"EPMCMBIBPC-867"})
    public void listOfCasesForPipeline() {
        //EPMCMBIBPC-842
        navigationMenu()
                .library()
                .clickOnFolder(folderWithPipeline)
                .clickOnPipeline(pipelineName);
        pipelineMetadata()
                .selectKeyByOrderNumber(1)
                .changeKey(key2)
                .assertKeyIs(key2)
                .changeValue(value2)
                .assertValueIs(value2);
        //EPMCMBIBPC-860
        pipelineMetadata()
                .addKeyWithValue(key3, value3)
                .addKeyWithValue(key4, value4)
                .addKeyWithValue(key5, value5)
                .assertKeyWithValueIsPresent(key3, value3)
                .assertKeyWithValueIsPresent(key4, value4)
                .assertKeyWithValueIsPresent(key5, value5);
        //EPMCMBIBPC-863
        pipelineMetadata()
                .deleteKey(key3)
                .cancel()
                .assertKeyIsPresent(key3)
                .deleteKey(key3)
                .ensureTitleIs(String.format("Do you want to delete key \"%s\"?", key3))
                .ok()
                .assertKeyNotPresent(key3);
        //EPMCMBIBPC-864
        pipelineMetadata()
                .deleteAllKeys()
                .cancel()
                .assertNumberOfKeysIs(3)
                .deleteAllKeys()
                .ensureTitleIs("Do you want to delete all metadata?")
                .ok()
                .assertNumberOfKeysIs(0);
    }

    private MetadataSectionAO pipelineMetadata() {
        return new MetadataSectionAO(new PipelineLibraryContentAO(pipelineName));
    }
}
