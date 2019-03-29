package com.epam.pipeline.manager.cloud.gcp;


import java.util.Arrays;
import java.util.Collection;

public enum GCPInstanceStatus {

    PROVISIONING, STAGING, RUNNING, STOPPING, STOPPED, SUSPENDING, SUSPENDED, TERMINATED;

    public static Collection<GCPInstanceStatus> getWorkingStatuses() {
        return Arrays.asList(PROVISIONING, STAGING, RUNNING);
    }
}
