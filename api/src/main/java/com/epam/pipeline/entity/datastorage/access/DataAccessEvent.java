package com.epam.pipeline.entity.datastorage.access;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;

public record DataAccessEvent(String path, DataAccessType type, AbstractDataStorage storage) {
}
