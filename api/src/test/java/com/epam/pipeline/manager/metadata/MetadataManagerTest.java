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

package com.epam.pipeline.manager.metadata;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.exception.MetadataReadingException;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MetadataManagerTest extends AbstractSpringTest {

    @Autowired
    private MetadataManager metadataManager;

    private Map<String, PipeConfValue> expectedData = new HashMap<>();
    private EntityVO entityVO = new EntityVO(1L, AclClass.PIPELINE);
    private MetadataEntry metadataEntry = new MetadataEntry();

    private static final String KEY_1 = "Project Owner";
    private static final String KEY_2 = "Cell Type";
    private static final String VALUE_1 = "Test User";
    private static final String VALUE_2 = "Leukocyte";
    private static final String TYPE = "string";
    private static final String TSV_HEADER = "Key\tValue\tType";
    private static final String CSV_HEADER = "Key,Value,Type";
    private static final String TSV_FILE_NAME = "test_file.tsv";
    private static final String CSV_FILE_NAME = "test_file.csv";

    @Before
    public void setup() {
        expectedData.put(KEY_1, new PipeConfValue(TYPE, VALUE_1));
        expectedData.put(KEY_2, new PipeConfValue(TYPE, VALUE_2));
        metadataEntry.setEntity(entityVO);
        metadataEntry.setData(expectedData);
    }

    @Test
    public void uploadMetadataTsvFile() throws IOException {
        String exampleContent = TSV_HEADER + "\n" + simpleContent("\t", false);
        assertFileContent(exampleContent, TSV_FILE_NAME);
    }

    @Test
    public void uploadMetadataTsvFileWithoutTypeInLine() throws IOException {
        String exampleContent = TSV_HEADER + "\n" + simpleContent("\t", true);
        assertFileContent(exampleContent, TSV_FILE_NAME);
    }

    @Test
    public void uploadMetadataTsvFileAnotherColumnOrder() throws IOException {
        String exampleContent = "Value\tKey\tType\n" +
                VALUE_1 + "\t" + KEY_1 + "\t" + TYPE + "\n" +
                VALUE_2 + "\t" + KEY_2 + "\t" + TYPE + "\n";
        assertFileContent(exampleContent, TSV_FILE_NAME);
    }

    @Test
    public void uploadMetadataCsvFile() throws IOException {
        String exampleContent = CSV_HEADER + "\n" + simpleContent(",", false);
        assertFileContent(exampleContent, CSV_FILE_NAME);
        exampleContent = CSV_HEADER + "\n" + simpleContent(",", true);
        assertFileContent(exampleContent, CSV_FILE_NAME);
        exampleContent = "Value,Key\n" +
                VALUE_1 + "," + KEY_1 + "\n" +
                VALUE_2 + "," + KEY_2 + "\n";
        assertFileContent(exampleContent, CSV_FILE_NAME);
    }

    @Test
    public void uploadMetadataCsvFileWithoutTypeInLine() throws IOException {
        String exampleContent = CSV_HEADER + "\n" + simpleContent(",", true);
        assertFileContent(exampleContent, CSV_FILE_NAME);
        exampleContent = "Value,Key\n" +
                VALUE_1 + "," + KEY_1 + "\n" +
                VALUE_2 + "," + KEY_2 + "\n";
        assertFileContent(exampleContent, CSV_FILE_NAME);
    }

    @Test
    public void uploadMetadataCsvFileWithoutTypeInHeader() throws IOException {
        String exampleContent = "Value,Key\n" +
                VALUE_1 + "," + KEY_1 + "\n" +
                VALUE_2 + "," + KEY_2 + "\n";
        assertFileContent(exampleContent, CSV_FILE_NAME);
    }

    @Test(expected = MetadataReadingException.class)
    public void metadataFileShouldFailWithoutHeader() throws IOException {
        String exampleContent = simpleContent("\t", false);
        assertFileContent(exampleContent, TSV_FILE_NAME);
    }

    @Test(expected = MetadataReadingException.class)
    public void metadataFileShouldFailWithExtraColumnInHeader() throws IOException {
        String exampleContent = TSV_HEADER + "Id\n" + simpleContent("\t", false);
        assertFileContent(exampleContent, TSV_FILE_NAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void metadataFileShouldFailWithExtraColumnInLine() throws IOException {
        String exampleContent = TSV_HEADER + "\n" +
                KEY_1 + "\t" + VALUE_1 + "\t" + TYPE + "\t" + "Extra\n" +
                KEY_2 + "\t" + VALUE_2 + "\t" + TYPE + "\n";
        assertFileContent(exampleContent, TSV_FILE_NAME);
    }

    @Test(expected = MetadataReadingException.class)
    public void metadataFileShouldFailWithIncorrectDelimiter() throws IOException {
        String exampleContent = TSV_HEADER + "\n" + simpleContent("\t", false);
        assertFileContent(exampleContent, CSV_FILE_NAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void metadataFileShouldFailWithUnsupportedFileExtension() throws IOException {
        String exampleContent = TSV_HEADER + "\n" + simpleContent("\t", false);
        assertFileContent(exampleContent, "test_file.txt");
    }

    @Test(expected = IllegalArgumentException.class)
    public void metadataFileShouldFailWithoutRequiredColumn() throws IOException {
        String exampleContent = "Key\n" + KEY_1 + "\n" + KEY_2 + "\n";
        assertFileContent(exampleContent, TSV_FILE_NAME);
    }

    @Test(expected = MetadataReadingException.class)
    public void metadataFileShouldFailWithIncorrectColumnName() throws IOException {
        String exampleContent = "Key\tValue\tTypes\n" + simpleContent("\t", false);
        assertFileContent(exampleContent, TSV_FILE_NAME);
    }

    private void assertFileContent(String exampleContent, String fileName) throws IOException {
        try (InputStream inputStream = IOUtils.toInputStream(exampleContent, "UTF-8")) {
            Map<String, PipeConfValue> actualData = metadataManager
                    .convertFileContentToMetadata(new MockMultipartFile(fileName, fileName, null, inputStream));
            Assert.assertEquals(expectedData, actualData);
        }
    }

    private String simpleContent(String delimiter, boolean emitSecondType) {
        String secondType = emitSecondType ? "" : delimiter + TYPE;
        return KEY_1 + delimiter + VALUE_1 + delimiter + TYPE + "\n" +
               KEY_2 + delimiter + VALUE_2 + secondType + "\n";
    }
}
