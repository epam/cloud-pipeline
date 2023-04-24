package com.epam.pipeline.entity.datastorage;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Data storage conversion action.
 *
 * Actions get executed only after data storage conversion finishes successfully.
 */
@RequiredArgsConstructor
public enum DataStorageConvertRequestAction {

    /**
     * Leaves data storage intact.
     */
    LEAVE,

    /**
     * Deletes data storage in Cloud Pipeline but keeps it in cloud.
     */
    UNREGISTER,

    /**
     * Deletes data storage both in Cloud Pipeline and in cloud.
     */
    DELETE;

    public static boolean isValid(final String name) {
        return findByName(name).isPresent();
    }

    public static Optional<DataStorageConvertRequestAction> findByName(final String name) {
        return Optional.ofNullable(name)
                .flatMap(n -> Arrays.stream(DataStorageConvertRequestAction.values())
                        .filter(action -> action.name().equals(n))
                        .findFirst());
    }
}
