package com.epam.pipeline.manager.datastorage.transfer;

import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageMounter;
import com.epam.pipeline.manager.pipeline.transfer.LocalPathToPipelineTransferManager;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NFSDataStorageToPipelineTransferManagerTest {

    private static final NFSDataStorage STORAGE = DatastorageCreatorUtils.getNfsDataStorage();
    private static final Pipeline PIPELINE = PipelineCreatorUtils.getPipeline(CommonCreatorConstants.ID);
    private static final Path MOUNT_DIR = Paths.get("");

    private final NFSStorageMounter nfsStorageMounter = mock(NFSStorageMounter.class);
    private final LocalPathToPipelineTransferManager localPathToPipelineTransferManager =
            mock(LocalPathToPipelineTransferManager.class);
    private final NFSDataStorageToPipelineTransferManager manager = new NFSDataStorageToPipelineTransferManager(
            nfsStorageMounter, localPathToPipelineTransferManager);

    @Before
    public void setUp() {
        doReturn(MOUNT_DIR.toFile()).when(nfsStorageMounter).mount(any());
    }

    @Test
    public void transferShouldMountNFSDataStorage() {
        manager.transfer(STORAGE, PIPELINE);

        verify(nfsStorageMounter).mount(STORAGE);
    }

    @Test
    public void transferShouldTransferMountedLocalPathToPipeline() {
        manager.transfer(STORAGE, PIPELINE);

        verify(localPathToPipelineTransferManager).transfer(MOUNT_DIR, PIPELINE);
    }
}
