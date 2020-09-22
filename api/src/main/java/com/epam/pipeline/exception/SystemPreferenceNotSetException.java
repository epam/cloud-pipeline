package com.epam.pipeline.exception;

import com.epam.pipeline.manager.preference.AbstractSystemPreference;

public class SystemPreferenceNotSetException extends RuntimeException {
    
    private static final String ERROR_MESSAGE = "System preference %s is not set.";

    public SystemPreferenceNotSetException(final AbstractSystemPreference<?> preference) {
        super(String.format(ERROR_MESSAGE, preference.getKey()));
    }
    
}
