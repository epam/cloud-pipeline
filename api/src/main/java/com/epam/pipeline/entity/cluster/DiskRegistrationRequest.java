package com.epam.pipeline.entity.cluster;

import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import lombok.Value;

@Value
public class DiskRegistrationRequest {
    
    private final Long size;

    public static DiskRegistrationRequest from(final DiskAttachRequest request) {
        return new DiskRegistrationRequest(request.getSize());
    }
}
