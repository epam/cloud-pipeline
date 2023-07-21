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

package com.epam.pipeline.manager.metadata;

import static com.epam.pipeline.util.CategoricalAttributeTestUtils.extractAttributesContent;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.MetadataReadingException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import org.apache.commons.io.IOUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetadataManagerTest extends AbstractSpringTest {

    @Autowired
    private MetadataManager metadataManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private CategoricalAttributeManager categoricalAttributeManager;

    @MockBean
    private UserManager userManager;

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
    private static final String SENSITIVE_KEY = "sensitive_metadata_key";
    private static final long USER_ENTITY_ID = 1L;
    private static final String TEST_USER = "TEST_USER";

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

    @Test
    @Transactional
    public void syncWithCategoricalAttributes() {
        preferenceManager.update(Collections.singletonList(new Preference(
            SystemPreferences.MISC_METADATA_SENSITIVE_KEYS.getKey(),
            String.format("[\"%s\"]", SENSITIVE_KEY))));
        Assert.assertEquals(0, categoricalAttributeManager.loadAll().size());
        Mockito.doReturn(new PipelineUser(TEST_USER)).when(userManager).load(Mockito.anyLong());

        final EntityVO entityVO = new EntityVO(USER_ENTITY_ID, AclClass.PIPELINE_USER);
        final Map<String, PipeConfValue> data = new HashMap<>();
        data.put(KEY_1, new PipeConfValue(TYPE, VALUE_1));
        data.put(KEY_2, new PipeConfValue(TYPE, VALUE_2));
        data.put(SENSITIVE_KEY, new PipeConfValue(TYPE, VALUE_2));
        final MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(entityVO);
        metadataVO.setData(data);
        metadataManager.updateMetadataItem(metadataVO);
        metadataManager.syncWithCategoricalAttributes();

        final Map<String, List<String>> categoricalAttributesAfterSync =
            extractAttributesContent(categoricalAttributeManager.loadAll());
        Assert.assertEquals(2, categoricalAttributesAfterSync.size());
        Assert.assertThat(categoricalAttributesAfterSync.get(KEY_1),
                          CoreMatchers.is(Collections.singletonList(VALUE_1)));
        Assert.assertThat(categoricalAttributesAfterSync.get(KEY_2),
                   CoreMatchers.is(Collections.singletonList(VALUE_2)));
        Assert.assertFalse(categoricalAttributesAfterSync.containsKey(SENSITIVE_KEY));
    }

    @Test
    @Transactional
    public void syncWithCategoricalAttributesWithoutSensitiveKeys() {
        Assert.assertEquals(0, categoricalAttributeManager.loadAll().size());
        Mockito.doReturn(new PipelineUser(TEST_USER)).when(userManager).load(Mockito.anyLong());

        final EntityVO entityVO = new EntityVO(USER_ENTITY_ID, AclClass.PIPELINE_USER);
        final Map<String, PipeConfValue> data = new HashMap<>();
        data.put(KEY_1, new PipeConfValue(TYPE, VALUE_1));
        data.put(KEY_2, new PipeConfValue(TYPE, VALUE_2));
        final MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(entityVO);
        metadataVO.setData(data);
        metadataManager.updateMetadataItem(metadataVO);
        metadataManager.syncWithCategoricalAttributes();

        final Map<String, List<String>> categoricalAttributesAfterSync =
            extractAttributesContent(categoricalAttributeManager.loadAll());
        Assert.assertEquals(2, categoricalAttributesAfterSync.size());
        Assert.assertThat(categoricalAttributesAfterSync.get(KEY_1),
                          CoreMatchers.is(Collections.singletonList(VALUE_1)));
        Assert.assertThat(categoricalAttributesAfterSync.get(KEY_2),
                   CoreMatchers.is(Collections.singletonList(VALUE_2)));
    }

    @Test
    @Transactional
    public void testThatEntityCouldBeSearchedByClassAndKeyOnly() {
        Mockito.doReturn(new PipelineUser(TEST_USER)).when(userManager).load(Mockito.anyLong());

        final EntityVO entityVO = new EntityVO(USER_ENTITY_ID, AclClass.PIPELINE_USER);
        final Map<String, PipeConfValue> data = new HashMap<>();
        data.put(KEY_1, new PipeConfValue(TYPE, VALUE_1));
        final MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(entityVO);
        metadataVO.setData(data);
        metadataManager.updateMetadataItem(metadataVO);

        List<EntityVO> result = metadataManager.searchMetadataByClassAndKeyValue(
                AclClass.PIPELINE_USER, KEY_1, VALUE_2);

        Assert.assertTrue(result.isEmpty());

        result = metadataManager.searchMetadataByClassAndKeyValue(
                AclClass.PIPELINE_USER, KEY_1, null);

        Assert.assertFalse(result.isEmpty());
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
