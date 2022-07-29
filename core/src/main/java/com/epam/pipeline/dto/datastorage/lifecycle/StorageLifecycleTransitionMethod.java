package com.epam.pipeline.dto.datastorage.lifecycle;

/**
 * Defines approach to transfer files to another Storage Class.
 * {@code EARLIEST_FILE} - all eligible files will be transferred together
 *                         only when earliest file will be eligible for transfer
 * {@code LATEST_FILE}   - all eligible files will be transferred together
 *                         when latest file will be eligible for transfer
 * {@code ONE_BY_ONE}    - files will be transferred one by one,
 *                         when they will be eligible for transfer
 * */
public enum StorageLifecycleTransitionMethod {
    EARLIEST_FILE, LATEST_FILE, ONE_BY_ONE
}
