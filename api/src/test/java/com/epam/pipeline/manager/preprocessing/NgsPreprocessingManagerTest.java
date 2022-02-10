package com.epam.pipeline.manager.preprocessing;

import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class NgsPreprocessingManagerTest extends AbstractManagerTest {

    @Autowired
    private NgsPreprocessingManager ngsPreprocessingManager;

    @Autowired
    private FolderManager folderManager;

    @MockBean
    private DataStorageManager storageManager;

    @Test
    public void registerSampleSheet() {
    }

    @Test(expected = IllegalStateException.class)
    public void registerSampleSheetFailIfExistAndOverwriteIsFalse() {
    }

    @Test
    public void unregisterSampleSheet() {
    }
}