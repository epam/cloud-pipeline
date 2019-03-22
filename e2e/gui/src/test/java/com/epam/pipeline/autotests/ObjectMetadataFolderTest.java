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

import com.epam.pipeline.autotests.ao.LibraryFolderAO;
import com.epam.pipeline.autotests.ao.MetadataSectionAO;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_KEY;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE_ICON;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ObjectMetadataFolderTest extends AbstractBfxPipelineTest implements Navigation {

    private final String folder = "object-metadata-test-folder-" + Utils.randomSuffix();
    private final String subfolder = "object-metadata-test-subfolder-" + Utils.randomSuffix();
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
                .createFolder(folder)
                .cd(folder)
                .createFolder(subfolder);
    }

    @AfterClass(alwaysRun = true)
    public void cleanUp() {
        navigationMenu()
                .library()
                .cd(folder)
                .cd(subfolder)
                .removeFolder(subfolder)
                .removeFolder(folder);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-857"})
    public void addMetadataToFolderValidation() {
        navigationMenu()
                .library()
                .clickOnFolder(folder)
                .showMetadata()
                .ensure(ADD_KEY, visible);
    }

    @Test(dependsOnMethods = "addMetadataToFolderValidation")
    @TestCase(value = {"EPMCMBIBPC-858"})
    public void addKeyToMetadata() {
        openFolderMetadata()
                .addKeyWithValue(key1, value1)
                .selectKey(key1)
                .ensure(DELETE_ICON, visible)
                .validateKeyBackgroundIsGrey();
    }

    @Test(dependsOnMethods = "addKeyToMetadata")
    @TestCase(value = {"EPMCMBIBPC-942"})
    public void updateKeyValidation() {
        folderMetadata()
                .selectKeyByOrderNumber(1)
                .changeKey(key2)
                .assertKeyIs(key2)
                .changeValue(value2)
                .assertValueIs(value2);
    }

    @Test(dependsOnMethods = "updateKeyValidation")
    @TestCase(value = {"EPMCMBIBPC-860"})
    public void addSeveralKeysToMetadata() {
        folderMetadata()
                .addKeyWithValue(key3, value3)
                .addKeyWithValue(key4, value4)
                .addKeyWithValue(key5, value5)
                .assertKeyWithValueIsPresent(key3, value3)
                .assertKeyWithValueIsPresent(key4, value4)
                .assertKeyWithValueIsPresent(key5, value5);
    }

    @Test(dependsOnMethods = "addSeveralKeysToMetadata")
    @TestCase(value = {"EPMCMBIBPC-863"})
    public void removeOneKeyValidation() {
        folderMetadata()
                .deleteKey(key3)
                .cancel()
                .assertKeyIsPresent(key3)
                .deleteKey(key3)
                .ensureTitleIs(String.format("Do you want to delete key \"%s\"?", key3))
                .ok()
                .assertKeyNotPresent(key3);
    }

    @Test(dependsOnMethods = "removeOneKeyValidation")
    @TestCase(value = {"EPMCMBIBPC-864"})
    public void removeAllKeysValidation() {
        folderMetadata()
                .deleteAllKeys()
                .cancel()
                .assertNumberOfKeysIs(3)
                .deleteAllKeys()
                .ensureTitleIs("Do you want to delete all metadata?")
                .ok()
                .assertNumberOfKeysIs(0);
    }

    @Test(dependsOnMethods = "removeAllKeysValidation")
    @TestCase(value = {"EPMCMBIBPC-865"})
    public void checkMetadataForSubfolder() {
        navigationMenu()
                .library()
                .cd(folder)
                .clickOnFolder(subfolder)
                .showMetadata()
                .addKeyWithValue(key1, value1)
                .assertNumberOfKeysIs(1);
        navigationMenu()
                .library()
                .clickOnFolder(folder)
                .showMetadata()
                .assertNumberOfKeysIs(0);
    }

    @Test
    @TestCase(value = {"EPMCMBIBPC-1193"})
    public void addWrongKeyValueMetadataToFolder() {
        openFolderMetadata()
                .addKeyWithValue("", "")
                .messageShouldAppear(emptyKeyErrorMessage)
                .sleep(5, SECONDS);
        openFolderMetadata()
                .addKeyWithValue("", value1)
                .messageShouldAppear(emptyKeyErrorMessage);
        openFolderMetadata()
                .sleep(5, SECONDS)
                .addKeyWithValue(key1, "")
                .messageShouldAppear(emptyValueErrorMessage);
    }

    private MetadataSectionAO folderMetadata() {
        sleep(1, SECONDS);
        return new MetadataSectionAO(new LibraryFolderAO(folder));
    }

    private MetadataSectionAO openFolderMetadata() {
        return navigationMenu()
                .library()
                .clickOnFolder(folder)
                .showMetadata();
    }
}
