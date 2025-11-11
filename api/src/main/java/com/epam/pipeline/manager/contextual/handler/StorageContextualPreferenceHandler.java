package com.epam.pipeline.manager.contextual.handler;

import com.epam.pipeline.dao.contextual.ContextualPreferenceDao;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;

/**
 * Storage contextual preference handler.
 *
 * It handles preferences with {@link ContextualPreferenceLevel#STORAGE} level.
 * Handler associates preference with a single {@link AbstractDataStorage} entity.
 */
public class StorageContextualPreferenceHandler extends AbstractDaoContextualPreferenceHandler {

    private final DataStorageDao storageDao;

    public StorageContextualPreferenceHandler(final DataStorageDao storageDao,
                                              final ContextualPreferenceDao contextualPreferenceDao,
                                              final ContextualPreferenceHandler nextHandler) {
        super(ContextualPreferenceLevel.STORAGE, nextHandler, contextualPreferenceDao);
        this.storageDao = storageDao;
    }

    public StorageContextualPreferenceHandler(final DataStorageDao storageDao,
                                              final ContextualPreferenceDao contextualPreferenceDao) {
        this(storageDao, contextualPreferenceDao, null);
    }

    @Override
    boolean externalEntityExists(final ContextualPreference preference) {
        return storageDao.loadDataStorage(Long.valueOf(preference.getResource().getResourceId())) != null;
    }
}
