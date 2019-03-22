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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.exception.MetadataReadingException;
import com.epam.pipeline.manager.metadata.parser.*;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.utils.MetadataParsingUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class MetadataUploadManagerTest extends AbstractSpringTest {

    private static final String TEST_CSV_FILE = "test.csv";
    private static final String TEST_TAB_FILE = "test.tsv";

    public static final String HEADER1 = "Participant:ID${d}name\n";
    public static final String HEADER2 = "Sample:ID${d}SampleName${d}participant:Participant:ID${d}fast_dir:Path\n";

    public static final String LINE1 = "p4${d}Participant1\n";
    public static final String LINE2 = "s1${d}Sample1${d}p1${d}set1\n";
    public static final String LINE3 = "s3${d}Sample2${d}p1${d}set2\n";

    @SpyBean
    private AuthManager authManager;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private MetadataUploadManager uploadManager;

    @Autowired
    private MetadataEntityManager entityManager;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testAllNewUpload() throws IOException {
        Folder folder = prepareRequiredEntities();
        when(authManager.isAdmin()).thenReturn(true);
        try (InputStream inputStream = MetadataFileBuilder.prepareInputData(MetadataParsingUtils.CSV_DELIMITER)) {
            List<MetadataEntity> entities = uploadManager.uploadFromFile(folder.getId(),
                            new MockMultipartFile(TEST_CSV_FILE, TEST_CSV_FILE, null, inputStream));
            assertEquals(2, entities.size());
            assertTrue(entities.stream().allMatch(e -> e.getId() != null));
        }
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testUpdateUpload() throws IOException {
        Folder folder = prepareRequiredEntities();
        MetadataClass sampleClass = entityManager.createMetadataClass(MetadataFileBuilder.SAMPLE_CLASS_NAME);
        MetadataEntity existingSample = entityManager.updateMetadataEntity(
                createEntityVO(folder.getId(), sampleClass.getId(), MetadataFileBuilder.SAMPLE1_ID));
        try (InputStream inputStream = MetadataFileBuilder.prepareInputData(MetadataParsingUtils.TAB_DELIMITER)) {
            List<MetadataEntity> entities = uploadManager.uploadFromFile(folder.getId(),
                            new MockMultipartFile(TEST_TAB_FILE, TEST_TAB_FILE, null, inputStream));
            assertEquals(2, entities.size());
            assertTrue(entities.stream().allMatch(e -> e.getId() != null));
            assertTrue(entities.stream()
                    .filter(e -> e.getExternalId().equals(MetadataFileBuilder.SAMPLE1_ID))
                    .findAny().orElseThrow(AssertionError::new).getId().equals(existingSample.getId()));
        }
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void uploadingMetadataWithConstantFieldTypesPerformsProperly() throws IOException {
        Folder folder = prepareRequiredEntities();
        entityManager.createMetadataClass(MetadataFileBuilder.SAMPLE_CLASS_NAME);
        assertEquals(1, uploadMetadata(folder, Arrays.asList(HEADER1, LINE1)).size());
        assertEquals(1, uploadMetadata(folder, Arrays.asList(HEADER2, LINE2)).size());
    }


    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void uploadingMetadataWithSeveralMultiValueFields() throws IOException {
        Folder folder = prepareRequiredEntities();
        entityManager.createMetadataClass(MetadataFileBuilder.SAMPLE_CLASS_NAME);
        MetadataClass setClass = entityManager.createMetadataClass(MetadataFileBuilder.SET_CLASS_NAME);

        entityManager.updateMetadataEntity(createEntityVO(folder.getId(),
                setClass.getId(), MetadataFileBuilder.SET1_ID));
        entityManager.updateMetadataEntity(createEntityVO(folder.getId(),
                setClass.getId(), MetadataFileBuilder.SET2_ID));
        entityManager.updateMetadataEntity(createEntityVO(folder.getId(),
                setClass.getId(), MetadataFileBuilder.SET3_ID));

        List<String> lines = Arrays.asList(
                MetadataFileBuilder.MULTIVALUE_HEADER,
                MetadataFileBuilder.MULTIVALUE_LINE1,
                MetadataFileBuilder.MULTIVALUE_LINE2,
                MetadataFileBuilder.MULTIVALUE_LINE3);

        try (InputStream inputStream =
                     MetadataFileBuilder.prepareInputData(MetadataParsingUtils.TAB_DELIMITER, lines)) {
            List<MetadataEntity> entities = uploadManager.uploadFromFile(folder.getId(),
                    new MockMultipartFile(TEST_TAB_FILE, TEST_TAB_FILE, null, inputStream));
            assertEquals(1, entities.size());
            String expectedSetField = String.format("[\"%s\",\"%s\",\"%s\"]",
                    MetadataFileBuilder.SET1_ID, MetadataFileBuilder.SET2_ID, MetadataFileBuilder.SET3_ID);

            String expectedPairField = String.format("[\"%s\",\"%s\"]",
                    MetadataFileBuilder.PAIR1_ID, MetadataFileBuilder.PAIR2_ID);

            assertEquals(expectedSetField, entities.get(0).getData().get("sets").getValue());
            assertEquals(expectedPairField, entities.get(0).getData().get("pairs").getValue());
            assertTrue(entities.stream().allMatch(e -> e.getId() != null));
        }
    }

    @Test(expected = MetadataReadingException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void uploadingMetadataWithWrongFieldTypeThrowsException() throws IOException {
        Folder folder = prepareRequiredEntities();
        entityManager.createMetadataClass(MetadataFileBuilder.SAMPLE_CLASS_NAME);
        uploadMetadata(folder, Arrays.asList(HEADER1, LINE1));
        uploadMetadata(folder, Arrays.asList(HEADER2, LINE2));
        String headerWithWrongFieldType = "Sample:ID${d}SampleName${d}participant${d}fast_dir:Path\n";
        uploadMetadata(folder, Arrays.asList(headerWithWrongFieldType, LINE3));
    }

    private Folder prepareRequiredEntities() {
        when(authManager.getAuthorizedUser()).thenReturn("user");
        Folder folder = createFolder();
        MetadataClass participantClass =
                entityManager.createMetadataClass(MetadataFileBuilder.PARTICIPANT_CLASS_NAME);
        MetadataClass pairClass =
                entityManager.createMetadataClass(MetadataFileBuilder.PAIR_CLASS_NAME);

        entityManager.updateMetadataEntity(createEntityVO(folder.getId(),
                participantClass.getId(), MetadataFileBuilder.PARTICIPANT1_ID));
        entityManager.updateMetadataEntity(createEntityVO(folder.getId(),
                participantClass.getId(), MetadataFileBuilder.PARTICIPANT2_ID));

        entityManager.updateMetadataEntity(createEntityVO(folder.getId(),
                pairClass.getId(), MetadataFileBuilder.PAIR1_ID));
        entityManager.updateMetadataEntity(createEntityVO(folder.getId(),
                pairClass.getId(), MetadataFileBuilder.PAIR2_ID));
        return folder;
    }

    private MetadataEntityVO createEntityVO(Long parentId, Long classId, String externalId) {
        MetadataEntityVO vo = new MetadataEntityVO();
        vo.setParentId(parentId);
        vo.setClassId(classId);
        vo.setExternalId(externalId);
        return vo;
    }

    private Folder createFolder() {
        Folder folder = new Folder();
        folder.setName("test");
        folderManager.create(folder);
        return folder;
    }

    private List<MetadataEntity> uploadMetadata(Folder folder, List<String> lines) throws IOException {
        try (InputStream inputStream = MetadataFileBuilder.prepareInputData(
                MetadataParsingUtils.TAB_DELIMITER, lines)
        ) {
            return uploadManager.uploadFromFile(folder.getId(),
                    new MockMultipartFile(TEST_TAB_FILE, TEST_TAB_FILE, null, inputStream));
        }
    }

}
