package com.epam.pipeline.entity.cluster;

import com.epam.pipeline.entity.pipeline.DiskAttachRequest;

import java.util.List;
import java.util.stream.Collectors;

public record DiskRegistrationRequest(Long size) {

    public static DiskRegistrationRequest from(final DiskAttachRequest request) {
        return new DiskRegistrationRequest(request.size());
    }

    public static DiskRegistrationRequest from(final InstanceDisk disk) {
        return new DiskRegistrationRequest(disk.size());
    }

    public static List<DiskRegistrationRequest> from(final List<InstanceDisk> disks) {
        return disks.stream().map(DiskRegistrationRequest::from).collect(Collectors.toList());
    }
}
