package com.epam.pipeline.manager.datastorage.convert;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequest;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.SecuredEntityTransferManager;
import com.epam.pipeline.manager.datastorage.transfer.DataStorageToPipelineTransferManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DataStorageToVersionedStorageConvertManagerTest {

    private static final AbstractDataStorage STORAGE = getNfsDataStorage();
    private static final AbstractDataStorage INVALID_STORAGE = DatastorageCreatorUtils.getS3bucketDataStorage();
    private static final Pipeline PIPELINE = PipelineCreatorUtils.getPipeline(CommonCreatorConstants.ID);

    private final PipelineManager pipelineManager = mock(PipelineManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final SecuredEntityTransferManager entityTransferManager = mock(SecuredEntityTransferManager.class);
    private final List<SecuredEntityTransferManager> entityTransferManagers = Collections.singletonList(
            entityTransferManager);
    private final DataStorageToPipelineTransferManager storageTransferManager = mockStorageToPipelineTransferManager();

    private final List<DataStorageToPipelineTransferManager> storageTransferManagers = Collections.singletonList(
            storageTransferManager);
    private final DataStorageToVersionedStorageConvertManager manager =
            new DataStorageToVersionedStorageConvertManager(pipelineManager, messageHelper, entityTransferManagers,
                    storageTransferManagers);

    @Before
    public void setUp() throws Exception {
        doReturn(PIPELINE).when(pipelineManager).createEmpty(any());
    }

    @Test
    public void convertShouldCreateVersionedStorageWithDataStorageName() {
        manager.convert(STORAGE, request());

        verify(pipelineManager).createEmpty(argThat(matches(pipeline ->
                Objects.equals(pipeline.getName(), STORAGE.getName()))));
    }

    @Test
    public void convertShouldCreateVersionedStorageWithDataStorageDescription() {
        manager.convert(STORAGE, request());

        verify(pipelineManager).createEmpty(argThat(matches(pipeline ->
                Objects.equals(pipeline.getDescription(), STORAGE.getDescription()))));
    }

    @Test
    public void convertShouldCreateVersionedStorageWithinDataStorageFolder() {
        manager.convert(STORAGE, request());

        verify(pipelineManager).createEmpty(argThat(matches(pipeline ->
                Objects.equals(pipeline.getParentFolderId(), STORAGE.getParentFolderId()))));
    }

    @Test
    public void convertShouldTransferConfigurationsUsingAllEntityTransferManagers() {
        manager.convert(STORAGE, request());

        for (final SecuredEntityTransferManager entityTransferManager : entityTransferManagers) {
            verify(entityTransferManager).transfer(STORAGE, PIPELINE);
        }
    }

    @Test
    public void convertShouldTransferDataUsingCorrespondingStorageToPipelineTransferManager() {
        manager.convert(STORAGE, request());

        verify(storageTransferManager).transfer(STORAGE, PIPELINE);
    }

    @Test
    public void convertShouldFailIfCorrespondingStorageToPipelineTransferManagerDoesNotExist() {
        assertThrows(DataStorageException.class, () -> manager.convert(INVALID_STORAGE, request()));
    }

    @Test
    public void convertShouldDeleteVersionedStorageIfAnyEntityTransferManagerFails() {
        doThrow(RuntimeException.class).when(entityTransferManager).transfer(any(), any());

        assertThrows(DataStorageException.class, () -> manager.convert(STORAGE, request()));

        verify(pipelineManager).delete(CommonCreatorConstants.ID, false);
    }

    @Test
    public void convertShouldDeleteVersionedStorageIfStorageTransferManagerFails() {
        doThrow(RuntimeException.class).when(storageTransferManager).transfer(any(), any());

        assertThrows(DataStorageException.class, () -> manager.convert(STORAGE, request()));

        verify(pipelineManager).delete(CommonCreatorConstants.ID, false);
    }

    private static NFSDataStorage getNfsDataStorage() {
        final NFSDataStorage storage = DatastorageCreatorUtils.getNfsDataStorage();
        storage.setParentFolderId(CommonCreatorConstants.ID);
        storage.setName(CommonCreatorConstants.TEST_STRING);
        storage.setDescription(CommonCreatorConstants.TEST_STRING);
        return storage;
    }

    private DataStorageToPipelineTransferManager mockStorageToPipelineTransferManager() {
        final DataStorageToPipelineTransferManager manager = mock(DataStorageToPipelineTransferManager.class);
        doReturn(STORAGE.getType()).when(manager).getType();
        return manager;
    }

    private DataStorageConvertRequest request() {
        return new DataStorageConvertRequest(null, null);
    }
}
