package com.epam.pipeline.manager.metadata.transfer;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.manager.SecuredEntityTransferManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetadataTransferManager implements SecuredEntityTransferManager {

    private final MetadataManager metadataManager;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void transfer(final AbstractSecuredEntity source, final AbstractSecuredEntity target) {
        final MetadataEntry sourceMetadata = metadataManager.loadMetadataItem(source.getId(), source.getAclClass());
        metadataManager.updateEntityMetadata(sourceMetadata.getData(), target.getId(), target.getAclClass());
    }

}
