package com.epam.pipeline.manager.metadata.transfer;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class MetadataTransferManagerTest {

    private static final AbstractSecuredEntity SOURCE = SecurityCreatorUtils.getEntity(ID, AclClass.DATA_STORAGE);
    private static final AbstractSecuredEntity TARGET = SecurityCreatorUtils.getEntity(ID_2, AclClass.PIPELINE);
    private static final Map<String, PipeConfValue> DATA =
            Collections.singletonMap(TEST_STRING, new PipeConfValue(TEST_STRING, TEST_STRING));
    private static final MetadataEntry METADATA_ENTRY = getMetadataEntry();

    private final MetadataManager metadataManager = mock(MetadataManager.class);
    private final MetadataTransferManager manager = new MetadataTransferManager(metadataManager);

    @Test
    public void transferShouldTransferMetadataFromSourceToTarget() {
        doReturn(METADATA_ENTRY).when(metadataManager).loadMetadataItem(SOURCE.getId(), SOURCE.getAclClass());

        manager.transfer(SOURCE, TARGET);

        verify(metadataManager).updateEntityMetadata(DATA, TARGET.getId(), TARGET.getAclClass());
    }

    @Test
    public void transferShouldDoNothingIfSourceMetadataDoesNotExist() {
        doReturn(null).when(metadataManager).loadMetadataItem(SOURCE.getId(), SOURCE.getAclClass());

        manager.transfer(SOURCE, TARGET);

        verify(metadataManager, never()).updateEntityMetadata(any(), any(), any());
    }

    private static MetadataEntry getMetadataEntry() {
        final MetadataEntry entry = new MetadataEntry();
        entry.setEntity(new EntityVO(SOURCE.getId(), SOURCE.getAclClass()));
        entry.setData(DATA);
        return entry;
    }
}
