package com.epam.pipeline.dao.datastorage.tags;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.tags.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTag;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.test.creator.region.RegionCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Transactional
public class DataStorageTagDaoTest extends AbstractSpringTest {

    private static final String STORAGE_FOLDER_PATH = "folder";
    private static final String STORAGE_PATH = STORAGE_FOLDER_PATH + "/path";
    private static final String ANOTHER_STORAGE_FOLDER_PATH = "another/folder";
    private static final String ANOTHER_STORAGE_PATH = ANOTHER_STORAGE_FOLDER_PATH + "/path";
    private static final String KEY = "KEY";
    private static final String ANOTHER_KEY = "ANOTHER_KEY";
    private static final String VALUE = "VALUE";
    private static final String ANOTHER_VALUE = "UPDATED_VALUE";
    
    private static final String TEST_STORAGE_NAME = "test-storage-name";
    private static final String TEST_STORAGE_PATH = "test-storage-path";
    private static final String ANOTHER_TEST_STORAGE_NAME = "another-test-storage-name";
    private static final String ANOTHER_TEST_STORAGE_PATH = "another-test-storage-path";
    private static final String NON_EXISTING_STORAGE_PATH = "non-existing-storage-path";

    @Autowired
    private DataStorageTagDao dataStorageTagDao;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Autowired
    private FolderDao folderDao;

    @Autowired
    private CloudRegionDao cloudRegionDao;

    @Before
    public void setUp() throws Exception {
        final Folder folder = ObjectCreatorUtils.createFolder(TEST_STRING, null);
        folder.setOwner(TEST_STRING);
        folderDao.createFolder(folder);
        
        final AwsRegion awsRegion = RegionCreatorUtils.getDefaultAwsRegion();
        cloudRegionDao.create(awsRegion);

        final S3bucketDataStorage objectStorage = 
                new S3bucketDataStorage(null, TEST_STORAGE_NAME, TEST_STORAGE_PATH);
        objectStorage.setParentFolderId(folder.getId());
        objectStorage.setRegionId(awsRegion.getId());
        objectStorage.setOwner(TEST_STRING);
        dataStorageDao.createDataStorage(objectStorage);

        final S3bucketDataStorage anotherObjectStorage = 
                new S3bucketDataStorage(null, ANOTHER_TEST_STORAGE_NAME, ANOTHER_TEST_STORAGE_PATH);
        anotherObjectStorage.setParentFolderId(folder.getId());
        anotherObjectStorage.setRegionId(awsRegion.getId());
        anotherObjectStorage.setOwner(TEST_STRING);
        dataStorageDao.createDataStorage(anotherObjectStorage);
    }

    @Test
    @Transactional
    public void upsertShouldCreateDataStorageTag() {
        final DataStorageObject object = new DataStorageObject(STORAGE_PATH);
        
        final DataStorageTag tag = dataStorageTagDao.upsert(TEST_STORAGE_PATH, new DataStorageTag(object, KEY, VALUE));

        final Optional<DataStorageTag> loadedTag = dataStorageTagDao.load(TEST_STORAGE_PATH, object, KEY);
        assertTrue(loadedTag.isPresent());
        assertThat(loadedTag.get(), is(tag));
    }

    @Test
    @Transactional
    public void upsertShouldUpdateDataStorageTagValueIfItAlreadyExists() {
        final DataStorageObject object = new DataStorageObject(STORAGE_PATH);
        final DataStorageTag tag = new DataStorageTag(object, KEY, VALUE);
        
        dataStorageTagDao.upsert(TEST_STORAGE_PATH, tag);
        dataStorageTagDao.upsert(TEST_STORAGE_PATH, tag.withValue(ANOTHER_VALUE));

        final Optional<DataStorageTag> loadedTag = dataStorageTagDao.load(TEST_STORAGE_PATH, object, KEY);
        assertTrue(loadedTag.isPresent());
        assertThat(loadedTag.get().getValue(), is(ANOTHER_VALUE));
    }
    
    @Test
    @Transactional
    public void upsertShouldSetCreatedDateWhileCreatingDataStorageTag() {
        final DataStorageObject object = new DataStorageObject(STORAGE_PATH);
        final DataStorageTag tag = new DataStorageTag(object, KEY, VALUE);

        dataStorageTagDao.upsert(TEST_STORAGE_PATH, tag);

        final Optional<DataStorageTag> loadedTag = dataStorageTagDao.load(TEST_STORAGE_PATH, object, KEY);
        assertTrue(loadedTag.isPresent());
        assertNotNull(loadedTag.get().getCreatedDate());
    }

    @Test
    public void copyFolderShouldCopyKeepOriginalDataStorageTags() {
        final DataStorageObject object = new DataStorageObject(STORAGE_PATH);
        final DataStorageTag tag = new DataStorageTag(object, KEY, VALUE);
        dataStorageTagDao.upsert(TEST_STORAGE_PATH, tag);

        dataStorageTagDao.copyFolder(TEST_STORAGE_PATH, STORAGE_FOLDER_PATH, ANOTHER_STORAGE_FOLDER_PATH);

        final Optional<DataStorageTag> loadedTag = dataStorageTagDao.load(TEST_STORAGE_PATH, object, KEY);
        assertTrue(loadedTag.isPresent());
    }

    @Test
    public void copyFolderShouldCopyAllDataStorageTags() {
        final DataStorageObject object = new DataStorageObject(STORAGE_PATH);
        final DataStorageTag tag = new DataStorageTag(object, KEY, VALUE);
        dataStorageTagDao.upsert(TEST_STORAGE_PATH, tag);
        
        dataStorageTagDao.copyFolder(TEST_STORAGE_PATH, STORAGE_FOLDER_PATH, ANOTHER_STORAGE_FOLDER_PATH);

        final DataStorageObject copiedObject = new DataStorageObject(ANOTHER_STORAGE_PATH);
        final Optional<DataStorageTag> loadedTag = dataStorageTagDao.load(TEST_STORAGE_PATH, copiedObject, KEY);
        assertTrue(loadedTag.isPresent());
    }

    @Test
    @Transactional
    public void loadShouldReturnEmptyOptionalIfDataStorageDoesNotExist() {
        assertFalse(dataStorageTagDao.load(NON_EXISTING_STORAGE_PATH, new DataStorageObject(STORAGE_PATH), KEY)
                .isPresent());
    }

    @Test
    @Transactional
    public void loadShouldReturnEmptyOptionalIfTagDoesNotExist() {
        assertFalse(dataStorageTagDao.load(TEST_STORAGE_PATH, new DataStorageObject(STORAGE_PATH), KEY).isPresent());
    }

    @Test
    @Transactional
    public void loadShouldReturnTag() {
        final DataStorageObject object = new DataStorageObject(STORAGE_PATH);
        final DataStorageTag tag = dataStorageTagDao.upsert(TEST_STORAGE_PATH, new DataStorageTag(object, KEY, VALUE));
        
        final Optional<DataStorageTag> loadedTag = dataStorageTagDao.load(TEST_STORAGE_PATH, object, KEY);
        
        assertTrue(loadedTag.isPresent());
        assertThat(loadedTag.get(), is(tag));
    }

    @Test
    @Transactional
    public void loadAllShouldReturnEmptyListIfDataStorageDoesNotExist() {
        assertTrue(dataStorageTagDao.load(NON_EXISTING_STORAGE_PATH, new DataStorageObject(STORAGE_PATH)).isEmpty());
    }

    @Test
    @Transactional
    public void loadAllShouldReturnEmptyListIfTagsDoNotExist() {
        assertTrue(dataStorageTagDao.load(TEST_STORAGE_PATH, new DataStorageObject(STORAGE_PATH)).isEmpty());
    }

    @Test
    @Transactional
    public void loadAllShouldReturnTags() {
        final DataStorageObject object = new DataStorageObject(STORAGE_PATH);
        final DataStorageTag firstTag = new DataStorageTag(object, KEY, VALUE);
        final DataStorageTag secondTag = new DataStorageTag(object, ANOTHER_KEY, VALUE);
        final List<DataStorageTag> tags = dataStorageTagDao.batchUpsert(TEST_STORAGE_PATH, firstTag, secondTag);

        final List<DataStorageTag> loadedTags = dataStorageTagDao.load(TEST_STORAGE_PATH, object);

        assertThat(loadedTags.size(), is(2));
        assertThat(loadedTags, containsInAnyOrder(tags.toArray()));
    }
    
    @Test
    @Transactional
    public void bulkLoadShouldReturnNothingIfDataStorageDoesNotExist() {
        assertFalse(dataStorageTagDao.load(NON_EXISTING_STORAGE_PATH, new DataStorageObject(STORAGE_PATH), STORAGE_PATH)
                .isPresent());
    }
    
    @Test
    @Transactional
    public void bulkLoadShouldReturnNothingIfTagsDoNotExist() {
        assertFalse(dataStorageTagDao.load(TEST_STORAGE_PATH, new DataStorageObject(STORAGE_PATH), STORAGE_PATH)
                .isPresent());
    }
    
    @Test
    @Transactional
    public void bulkLoadShouldReturnTags() {
        final DataStorageObject firstObject = new DataStorageObject(STORAGE_PATH);
        final DataStorageObject secondObject = new DataStorageObject(ANOTHER_STORAGE_PATH);
        final DataStorageTag firstTag = new DataStorageTag(firstObject, KEY, VALUE);
        final DataStorageTag secondTag = new DataStorageTag(secondObject, KEY, VALUE);
        final List<DataStorageTag> tags = dataStorageTagDao.batchUpsert(TEST_STORAGE_PATH, firstTag, secondTag);

        final List<DataStorageTag> loadedTags = dataStorageTagDao.batchLoad(TEST_STORAGE_PATH, 
                Arrays.asList(STORAGE_PATH, ANOTHER_STORAGE_PATH));
        
        assertThat(loadedTags.size(), is(2));
        assertThat(loadedTags, containsInAnyOrder(tags.toArray()));
    }

    @Test
    @Transactional
    public void bulkUpsertShouldCreateTags() {
        final DataStorageObject firstObject = new DataStorageObject(STORAGE_PATH);
        final DataStorageObject secondObject = new DataStorageObject(ANOTHER_STORAGE_PATH);
        final DataStorageTag firstTag = new DataStorageTag(firstObject, KEY, VALUE);
        final DataStorageTag secondTag = new DataStorageTag(secondObject, KEY, VALUE);

        final List<DataStorageTag> tags = dataStorageTagDao.batchUpsert(TEST_STORAGE_PATH, firstTag, secondTag);

        final List<DataStorageTag> loadedTags = dataStorageTagDao.batchLoad(TEST_STORAGE_PATH, 
                Arrays.asList(STORAGE_PATH, ANOTHER_STORAGE_PATH));
        assertThat(loadedTags.size(), is(2));
        assertThat(loadedTags, containsInAnyOrder(tags.toArray()));
    }

    @Test
    @Transactional
    public void deleteShouldRemoveObjectTag() {
        final DataStorageObject object = new DataStorageObject(STORAGE_PATH);
        dataStorageTagDao.upsert(TEST_STORAGE_PATH, new DataStorageTag(object, KEY, VALUE));
        
        dataStorageTagDao.delete(TEST_STORAGE_PATH, object, KEY);
        
        assertFalse(dataStorageTagDao.load(TEST_STORAGE_PATH, object, KEY).isPresent());
    }

    @Test
    @Transactional
    public void deleteShouldRemoveAllSpecifiedObjectTags() {
        final DataStorageObject object = new DataStorageObject(STORAGE_PATH);
        final List<DataStorageTag> tags = dataStorageTagDao.batchUpsert(TEST_STORAGE_PATH, 
                new DataStorageTag(object, KEY, VALUE),
                new DataStorageTag(object, ANOTHER_KEY, VALUE));

        dataStorageTagDao.delete(TEST_STORAGE_PATH, object, ANOTHER_KEY);

        final List<DataStorageTag> loadedTags = dataStorageTagDao.load(TEST_STORAGE_PATH, object);
        assertThat(loadedTags.size(), is(1));
        assertThat(loadedTags, containsInAnyOrder(tags.get(0)));
    }

    @Test
    @Transactional
    public void deleteShouldRemoveAllObjectTags() {
        final DataStorageObject object = new DataStorageObject(STORAGE_PATH);
        dataStorageTagDao.batchUpsert(TEST_STORAGE_PATH, 
                new DataStorageTag(object, KEY, VALUE), 
                new DataStorageTag(object, ANOTHER_KEY, VALUE));
        
        dataStorageTagDao.delete(TEST_STORAGE_PATH, object);
        
        assertTrue(dataStorageTagDao.load(TEST_STORAGE_PATH, object).isEmpty());
    }

    @Test
    @Transactional
    public void bulkDeleteShouldRemoveObjectTags() {
        final DataStorageObject firstObject = new DataStorageObject(STORAGE_PATH);
        final DataStorageObject secondObject = new DataStorageObject(ANOTHER_STORAGE_PATH);
        final DataStorageTag firstTag = new DataStorageTag(firstObject, KEY, VALUE);
        final DataStorageTag secondTag = new DataStorageTag(secondObject, KEY, VALUE);
        dataStorageTagDao.batchUpsert(TEST_STORAGE_PATH, firstTag, secondTag);

        dataStorageTagDao.batchDelete(TEST_STORAGE_PATH, firstObject, secondObject);

        assertTrue(dataStorageTagDao.batchLoad(TEST_STORAGE_PATH, Arrays.asList(STORAGE_PATH, ANOTHER_STORAGE_PATH))
                .isEmpty());
    }
}
