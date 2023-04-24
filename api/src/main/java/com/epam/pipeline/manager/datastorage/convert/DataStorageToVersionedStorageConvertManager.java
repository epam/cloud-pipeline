package com.epam.pipeline.manager.datastorage.convert;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequest;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequestType;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineType;
import com.epam.pipeline.manager.SecuredEntityTransferManager;
import com.epam.pipeline.manager.datastorage.transfer.DataStorageToPipelineTransferManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataStorageToVersionedStorageConvertManager implements DataStorageToSecuredEntityConvertManager {

    private final PipelineManager pipelineManager;
    private final MessageHelper messageHelper;
    private final List<SecuredEntityTransferManager> entityTransferManagers;
    private final Map<DataStorageType, DataStorageToPipelineTransferManager>
            storageTransferManagers;

    public DataStorageToVersionedStorageConvertManager(
            final PipelineManager pipelineManager,
            final MessageHelper messageHelper,
            final List<SecuredEntityTransferManager> entityTransferManagers,
            final List<DataStorageToPipelineTransferManager> storageTransferManagers) {
        this.pipelineManager = pipelineManager;
        this.messageHelper = messageHelper;
        this.entityTransferManagers = entityTransferManagers;
        this.storageTransferManagers = storageTransferManagers.stream()
                .collect(Collectors.toMap(DataStorageToPipelineTransferManager::getType, Function.identity()));
    }

    @Override
    public DataStorageConvertRequestType getTargetType() {
        return DataStorageConvertRequestType.VERSIONED_STORAGE;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Pipeline convert(final AbstractDataStorage storage,
                            final DataStorageConvertRequest request) {
        log.debug("Converting data storage #{} to versioned storage...", storage.getId());
        final Pipeline pipeline = createVersionedStorage(storage);
        transfer(storage, pipeline);
        return pipeline;
    }

    private Pipeline createVersionedStorage(final AbstractDataStorage storage) {
        return pipelineManager.createEmpty(toVersionedStorage(storage));
    }

    private PipelineVO toVersionedStorage(final AbstractDataStorage storage) {
        final PipelineVO pipelineVO = new PipelineVO();
        pipelineVO.setName(storage.getName());
        pipelineVO.setPipelineType(PipelineType.VERSIONED_STORAGE);
        pipelineVO.setDescription(storage.getDescription());
        pipelineVO.setParentFolderId(storage.getParentFolderId());
        return pipelineVO;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void transfer(final AbstractDataStorage storage,
                          final Pipeline pipeline) {
        try {
            unsafeTransfer(storage, pipeline);
        } catch (Exception e) {
            delete(pipeline);
            throw new DataStorageException(messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_CONVERT_FAILED,
                    storage.getId()), e);
        }
    }

    private void unsafeTransfer(final AbstractDataStorage storage,
                                final Pipeline pipeline) {
        for (SecuredEntityTransferManager entityTransferManager : entityTransferManagers) {
            entityTransferManager.transfer(storage, pipeline);
        }
        getStorageTransferManager(storage).transfer(storage, pipeline);
    }

    private DataStorageToPipelineTransferManager getStorageTransferManager(final AbstractDataStorage storage) {
        return Optional.ofNullable(storageTransferManagers.get(storage.getType()))
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_CONVERT_SOURCE_TYPE_INVALID, storage.getType())));
    }

    private void delete(final Pipeline pipeline) {
        pipelineManager.delete(pipeline.getId(), false);
    }

}
