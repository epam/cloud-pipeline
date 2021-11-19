package com.epam.pipeline.manager.datastorage.convert;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequest;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequestAction;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequestType;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataStorageConvertManager {

    public static final DataStorageConvertRequestAction FALLBACK_SOURCE_ACTION = DataStorageConvertRequestAction.LEAVE;

    private final DataStorageManager dataStorageManager;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;
    private final Map<DataStorageConvertRequestType, DataStorageToSecuredEntityConvertManager> managers;

    public DataStorageConvertManager(final DataStorageManager dataStorageManager,
                                     final PreferenceManager preferenceManager,
                                     final MessageHelper messageHelper,
                                     final List<DataStorageToSecuredEntityConvertManager> managers) {
        this.dataStorageManager = dataStorageManager;
        this.preferenceManager = preferenceManager;
        this.messageHelper = messageHelper;
        this.managers = managers.stream().collect(Collectors.toMap(
                DataStorageToSecuredEntityConvertManager::getTargetType,
                Function.identity()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AbstractSecuredEntity convert(final Long id, final DataStorageConvertRequest request) {
        final AbstractDataStorage storage = dataStorageManager.load(id);
        final AbstractSecuredEntity target = getConvertManager(request).convert(storage, request);
        executeSourceAction(storage, request);
        return target;
    }

    private DataStorageToSecuredEntityConvertManager getConvertManager(final DataStorageConvertRequest request) {
        final DataStorageConvertRequestType targetType = Optional.ofNullable(request.getTargetType())
                .orElse(DataStorageConvertRequestType.VERSIONED_STORAGE);
        final DataStorageToSecuredEntityConvertManager manager = managers.get(targetType);
        Assert.notNull(manager, messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_CONVERT_TARGET_TYPE_INVALID,
                targetType));
        return manager;
    }

    private AbstractDataStorage executeSourceAction(final AbstractDataStorage storage,
                                                    final DataStorageConvertRequest request) {
        switch (getSourceAction(request)) {
            case UNREGISTER:
                return dataStorageManager.delete(storage.getId(), false);
            case DELETE:
                return dataStorageManager.delete(storage.getId(), true);
            default:
                return storage;
        }
    }

    private DataStorageConvertRequestAction getSourceAction(final DataStorageConvertRequest request) {
        return Optional.ofNullable(request.getSourceAction()).map(Optional::of)
                .orElseGet(this::getSourceActionFromPreferences)
                .orElse(FALLBACK_SOURCE_ACTION);
    }

    private Optional<DataStorageConvertRequestAction> getSourceActionFromPreferences() {
        return Optional.of(SystemPreferences.STORAGE_CONVERT_SOURCE_ACTION)
                .map(AbstractSystemPreference::getKey)
                .map(preferenceManager::getStringPreference)
                .flatMap(DataStorageConvertRequestAction::findByName);
    }
}
