package com.epam.pipeline.entity.cluster;

import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import lombok.Value;

import java.util.List;
import java.util.stream.Collectors;

@Value
public class DiskRegistrationRequest {
    
    private final Long size;

    public static DiskRegistrationRequest from(final DiskAttachRequest request) {
        return new DiskRegistrationRequest(request.getSize());
    }

    public static DiskRegistrationRequest from(final InstanceDisk disk) {
        return new DiskRegistrationRequest(disk.getSize());
    }

    public static List<DiskRegistrationRequest> from(final List<InstanceDisk> disks) {
        return disks.stream().map(DiskRegistrationRequest::from).collect(Collectors.toList());
    }
}
