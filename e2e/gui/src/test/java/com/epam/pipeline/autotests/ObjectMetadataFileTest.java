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
import com.epam.pipeline.autotests.ao.StorageContentAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.epam.pipeline.autotests.utils.listener.Cloud;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import static com.codeborne.selenide.Condition.visible;
import static com.epam.pipeline.autotests.ao.Primitive.ADD_KEY;
import static com.epam.pipeline.autotests.ao.Primitive.CLOSE;
import static com.epam.pipeline.autotests.ao.Primitive.DELETE_ICON;
import static com.epam.pipeline.autotests.ao.Primitive.ENLARGE;
import static com.epam.pipeline.autotests.ao.Primitive.FILE_PREVIEW;
import static com.epam.pipeline.autotests.ao.Primitive.REMOVE_ALL;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ObjectMetadataFileTest extends AbstractBfxPipelineTest implements Authorization {

    private final String bucket = "file-metadata-test-bucket-" + Utils.randomSuffix();
    private final String fileLessThan10kb = "expectedRunLogPython.txt";
    private final String fileMoreThan10kb = "fileMoreThan10kb.txt";
    private final String fileMoreThan10kbPartOfContent = "abc1";
    private final String fileIsTooLargeMessage = "File is too large to be shown.";
    private final String emptyKeyErrorMessage = "Enter key";
    private final String emptyValueErrorMessage = "Enter value";

    private final String key1 = "key1";
    private final String value1 = "value1";
    private final String key2 = "key2";
    private final String value2 = "value2";
    private final String key3 = "qwerty";
    private final String value3 = "asdfg";
    private final String key4 = "very very very long key with spaces";
    private final String value4 = "a";
    private final String key5 = "a";
    private final String value5 = "text with spaces";
    private final String key6 = "CP_OWNER";
    private final String value6 = C.LOGIN;
    private final String key7 = " ";
    private final String value7 = " ";

    @BeforeClass
    public void createAllEntities() {
        navigationMenu()
                .library()
                .createStorage(bucket)
                .selectStorage(bucket)
                .uploadFile(getFile(fileLessThan10kb));
    }

    @AfterClass
    public void cleanUp() {
        navigationMenu()
                .library()
                .removeStorage(bucket);
    }

    @Test(priority = 0)
    @TestCase(value = {"EPMCMBIBPC-1159"})
    public void validateMetadataForFileInBucket() {
        navigationMenu()
                .library()
                .selectStorage(bucket)
                .fileMetadata(fileLessThan10kb)
                .ensureVisible(ADD_KEY, ENLARGE, FILE_PREVIEW)
                .assertKeyWithValueIsPresent(key6, getUserNameByAccountLogin(value6));
    }

    @Test(dependsOnMethods = "validateMetadataForFileInBucket")
    @TestCase(value = {"EPMCMBIBPC-1166"})
    public void validateFilePreviewForFilesLessThan10kb() {
        final String fileContent = readFromFile(fileLessThan10kb);
        navigationMenu()
                .library()
                .selectStorage(bucket)
                .fileMetadata(fileLessThan10kb)
                .assertFilePreviewContainsText(fileContent)
                .ensureMetadataSectionNotContainText(fileIsTooLargeMessage)
                .fullScreen()
                .ensureHeaderNotContainText(fileIsTooLargeMessage)
                .assertFullScreenPreviewContainsText(fileContent)
                .ensure(CLOSE, visible)
                .ensureHeaderContainsText(fileLessThan10kb)
                .close();
    }

    @Test(dependsOnMethods = "validateFilePreviewForFilesLessThan10kb")
    @TestCase(value = {"EPMCMBIBPC-1167"})
    public void validateFilePreviewForFilesMoreThan10kb() {
        final File file = Utils.createFileAndFillWithString(
                fileMoreThan10kb,
                fileMoreThan10kbPartOfContent,
                11000
        );
        navigationMenu()
                .library()
                .selectStorage(bucket)
                .uploadFile(file)
                .fileMetadata(fileMoreThan10kb)
                .assertFilePreviewContainsText(fileMoreThan10kbPartOfContent)
                .fullScreen()
                .ensureHeaderContainsText(fileIsTooLargeMessage)
                .assertFullScreenPreviewContainsText(fileMoreThan10kbPartOfContent)
                .close();
    }

    @Test(dependsOnMethods = "validateFilePreviewForFilesMoreThan10kb")
    @TestCase(value = {"EPMCMBIBPC-1172"})
    public void addKeyValueMetadataToFile() {
        navigationMenu()
                .library()
                .selectStorage(bucket)
                .fileMetadata(fileLessThan10kb)
                .addKeyWithValue(key1, value1)
                .selectKey(key1)
                .ensure(DELETE_ICON, visible)
                .validateKeyBackgroundIsGrey()
                .ensureVisible(REMOVE_ALL);
    }

    @Test(dependsOnMethods = "addKeyValueMetadataToFile")
    @TestCase(value = {"EPMCMBIBPC-1173"})
    public void updateKeyValidationForFileInBucket() {
        fileMetadata()
                .selectKeyByOrderNumber(1)
                .changeKey(key2)
                .assertKeyIs(key2)
                .changeValue(value2)
                .assertValueIs(value2);
    }

    @Test(dependsOnMethods = "updateKeyValidationForFileInBucket")
    @TestCase(value = {"EPMCMBIBPC-1174"})
    public void addSeveralKeysToFileMetadata() {
        fileMetadata()
                .addKeyWithValue(key3, value3)
                .addKeyWithValue(key5, value5)
                .assertKeyWithValueIsPresent(key3, value3)
                .assertKeyWithValueIsPresent(key5, value5)
                .addKeyWithValue(key7, value7)
                .messageShouldAppear(emptyKeyErrorMessage);
        if (Cloud.AZURE.name().equals(C.CLOUD_PROVIDER)) {
            return;
        }
        fileMetadata()
                .addKeyWithValue(key4, value4)
                .assertKeyWithValueIsPresent(key4, value4);
    }

    @Test(dependsOnMethods = "addSeveralKeysToFileMetadata")
    @TestCase(value = {"EPMCMBIBPC-1175"})
    public void removeOneKeyValidation() {
        fileMetadata()
                .deleteKey(key3)
                .cancel()
                .assertKeyIsPresent(key3)
                .deleteKey(key3)
                .ensureTitleIs(String.format("Do you want to delete key \"%s\"?", key3))
                .ok()
                .assertKeyNotPresent(key3);
    }

    @Test(dependsOnMethods = "removeOneKeyValidation")
    @TestCase(value = {"EPMCMBIBPC-1176"})
    public void removeAllKeysValidation() {
        fileMetadata()
                .deleteAllKeys()
                .cancel()
                .assertNumberOfKeysIs(3)
                .deleteAllKeys()
                .ensureTitleIs("Do you want to delete all metadata?")
                .ok()
                .assertNumberOfKeysIs(0);
    }

    @Test(dependsOnMethods = "removeAllKeysValidation")
    @TestCase(value = {"EPMCMBIBPC-1194"})
    public void addWrongKeyValueMetadataToFile() {
        openFileMetadata()
                .addKeyWithValue("", "")
                .messageShouldAppear(emptyKeyErrorMessage)
                .sleep(5, SECONDS);
        openFileMetadata()
                .addKeyWithValue("", value1)
                .messageShouldAppear(emptyKeyErrorMessage);
        openFileMetadata()
                .sleep(5, SECONDS)
                .addKeyWithValue(key1, "")
                .messageShouldAppear(emptyValueErrorMessage);
    }

    private File getFile(String filename) {
        try {
            return Paths.get(ClassLoader.getSystemResource(filename).toURI()).toFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to get resource file");
        }
    }

    private String readFromFile(String filename) {
        return Utils.readResourceFully(String.format("/%s", filename));
    }

    private MetadataSectionAO fileMetadata() {
        sleep(1, SECONDS);
        return new MetadataSectionAO(new StorageContentAO());
    }

    private MetadataSectionAO openFileMetadata() {
        return navigationMenu()
                .library()
                .selectStorage(bucket)
                .fileMetadata(fileLessThan10kb);
    }
}
