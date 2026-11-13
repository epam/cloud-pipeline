/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import static java.util.concurrent.TimeUnit.SECONDS;

public class ObjectMetadataPipelineTest extends AbstractBfxPipelineTest implements Navigation {

    private final String folderWithPipeline = "object-metadata-test-folder-with-pipeline-" + Utils.randomSuffix();
    private final String pipelineName = "object-metadata-test-pipeline-" + Utils.randomSuffix();
    private final String emptyKeyErrorMessage = "Enter key";
    private final String emptyValueErrorMessage = "Enter value";

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
                .assertKeyIs(key1)
                .validateKeyBackgroundIsGrey()
                .ensure(DELETE_ICON, visible)
                .assertValueIs(value1);
    }

    /**
     * Runs before {@link #addMetadataToPipelineValidation()}: the pipeline has no attributes yet, so its attributes panel
     * is closed when the page opens (the "Attributes" item toggles it), and the key of the third check does not exist
     * (a duplicate key is reported before an empty value).
     */
    @Test(priority = -1)
    @TestCase(value = {"EPMCMBIBPC-1193"})
    public void addWrongKeyValueMetadataToPipeline() {
        openPipelineMetadata()
                .addKeyWithValue("", "")
                .messageShouldAppear(emptyKeyErrorMessage)
                .sleep(5, SECONDS);
        openPipelineMetadata()
                .addKeyWithValue("", value1)
                .messageShouldAppear(emptyKeyErrorMessage);
        openPipelineMetadata()
                .sleep(5, SECONDS)
                .addKeyWithValue(key1, "")
                .messageShouldAppear(emptyValueErrorMessage);
    }

    @Test(dependsOnMethods = "addMetadataToPipelineValidation")
    @TestCase(value = {"EPMCMBIBPC-871"})
    public void checkMetadataForPipelineInFolder() {
    navigationMenu()
                .library()
                .clickOnFolder(folderWithPipeline)
                .showAttributes()
                .assertNumberOfKeysIs(0);
    }

    @Test(dependsOnMethods = "checkMetadataForPipelineInFolder")
    @TestCase(value = {"EPMCMBIBPC-867"})
    public void listOfCasesForPipeline() {
        navigationMenu()
                .library()
                .clickOnFolder(folderWithPipeline)
                .clickOnPipeline(pipelineName);
        //EPMCMBIBPC-860
        pipelineMetadata()
                .addKeyWithValue(key3, value3)
                .addKeyWithValue(key4, value4)
                .addKeyWithValue(key5, value5)
                .assertKeyWithExactValueIsPresent(key3, value3)
                .assertKeyWithExactValueIsPresent(key4, value4)
                .assertKeyWithExactValueIsPresent(key5, value5);
        //EPMCMBIBPC-942
        pipelineMetadata()
                .selectKeyByOrderNumber(1)
                .editKey(key2)
                .clickOnEmptySpace()
                .assertKeyIs(key2)
                .editValue(value2)
                .clickOnEmptySpace()
                .assertValueIs(value2);
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

    private MetadataSectionAO openPipelineMetadata() {
        return navigationMenu()
                .library()
                .clickOnFolder(folderWithPipeline)
                .clickOnPipeline(pipelineName)
                .showMetadata();
    }
}
