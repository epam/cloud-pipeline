package com.epam.pipeline.manager.metadata.transfer;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.manager.SecuredEntityTransferManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MetadataTransferManager implements SecuredEntityTransferManager {

    private final MetadataManager metadataManager;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void transfer(final AbstractSecuredEntity source, final AbstractSecuredEntity target) {
        Optional.ofNullable(metadataManager.loadMetadataItem(source.getId(), source.getAclClass()))
                .ifPresent(sourceMetadata -> metadataManager.updateEntityMetadata(sourceMetadata.getData(),
                        target.getId(), target.getAclClass()));
    }

}
