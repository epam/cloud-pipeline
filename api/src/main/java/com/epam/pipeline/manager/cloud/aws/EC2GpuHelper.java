package com.epam.pipeline.manager.cloud.aws;

import com.epam.pipeline.entity.cluster.GpuDevice;
import com.epam.pipeline.entity.region.AwsRegion;

import java.util.List;
import java.util.Map;

public interface EC2GpuHelper {

    Map<String, GpuDevice> findGpus(List<String> instanceTypes, AwsRegion region);
}
