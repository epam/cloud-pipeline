/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cloud.aws;

import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.utils.CommonUtils;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class AWSInstanceTypeReader implements Closeable {

    private static final int BATCH_SIZE = 100;

    private final AWSPriceListReader reader;
    private final AwsRegion region;
    private final EC2Helper ec2Helper;
    private final Map<String, Integer> gpuCoresMapping;

    public List<InstanceOffer> read() throws IOException {
        final List<InstanceOffer> offers = reader.read();
        final Map<String, String> gpus = resolveGpuNames(offers, region);
        return offers.stream()
                .map(offer -> withGpuDetails(offer, gpus))
                .collect(Collectors.toList());
    }

    private Map<String, String> resolveGpuNames(final List<InstanceOffer> offers, final AwsRegion region) {
        final Stream<String> instanceTypes = offers.stream()
                .filter(it -> CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY.equals(it.getProductFamily()))
                .filter(it -> it.getGpu() > 0)
                .map(InstanceOffer::getInstanceType)
                .distinct();
        return StreamUtils.chunked(instanceTypes, BATCH_SIZE)
                .map(chunk -> ec2Helper.findGpus(chunk, region))
                .reduce(CommonUtils::mergeMaps)
                .orElseGet(Collections::emptyMap);
    }

    private InstanceOffer withGpuDetails(final InstanceOffer offer, final Map<String, String> gpus) {
        final Optional<String> gpuType = Optional.ofNullable(offer.getInstanceType()).map(gpus::get);
        final Optional<Integer> gpuCores = gpuType.map(gpuCoresMapping::get);
        gpuType.ifPresent(offer::setGpuType);
        gpuCores.ifPresent(offer::setGpuCores);
        return offer;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
