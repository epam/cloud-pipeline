package com.epam.pipeline.manager;

/**
 * Transfer manager performs data or configuration transferring from one object to another.
 *
 * @param <S> data or configuration source object type.
 * @param <T> data or configuration target object type.
 */
public interface TransferManager<S, T> {

    /**
     * Copies data or configuration from source to target.
     *
     * As a result of the operation a source object should remain intact.
     *
     * @param source of data or configuration to transfer.
     * @param target to copy data or configuration to.
     */
    void transfer(S source, T target);
}
