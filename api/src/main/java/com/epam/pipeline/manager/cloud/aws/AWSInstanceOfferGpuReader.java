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

import com.epam.pipeline.entity.cluster.GpuDevice;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.cloud.offer.InstanceOfferReader;
import com.epam.pipeline.utils.CommonUtils;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class AWSInstanceOfferGpuReader implements InstanceOfferReader {

    private static final int BATCH_SIZE = 100;

    private final InstanceOfferReader reader;
    private final AwsRegion region;
    private final EC2GpuHelper ec2GpuHelper;
    private final Map<String, Integer> gpuCoresMapping;

    public List<InstanceOffer> read() throws IOException {
        final List<InstanceOffer> offers = reader.read();
        final Map<String, GpuDevice> gpus = collectGpus(offers);
        return offers.stream()
                .map(offer -> withGpus(offer, gpus))
                .collect(Collectors.toList());
    }

    private Map<String, GpuDevice> collectGpus(final List<InstanceOffer> offers) {
        return findGpus(getGpuInstanceTypes(offers));
    }

    private List<String> getGpuInstanceTypes(final List<InstanceOffer> offers) {
        return offers.stream()
                .filter(it -> CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY.equals(it.getProductFamily()))
                .filter(it -> it.getGpu() > 0)
                .map(InstanceOffer::getInstanceType)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, GpuDevice> findGpus(final List<String> instanceTypes) {
        log.debug("Retrieving {} instance offers gpus for region {} {} #{}...",
                instanceTypes.size(), region.getProvider(), region.getRegionCode(), region.getId());
        final Map<String, GpuDevice> gpus = StreamUtils.chunked(instanceTypes.stream(), BATCH_SIZE)
                .map(chunk -> ec2GpuHelper.findGpus(chunk, region))
                .reduce(CommonUtils::mergeMaps)
                .orElseGet(Collections::emptyMap);
        log.debug("Retrieved {} instance offers gpus for region {} {} #{}.",
                gpus.size(), region.getProvider(), region.getRegionCode(), region.getId());
        return gpus;
    }

    private InstanceOffer withGpus(final InstanceOffer offer, final Map<String, GpuDevice> gpus) {
        Optional.ofNullable(offer.getInstanceType())
                .map(gpus::get)
                .map(gpu -> gpu.toBuilder().cores(gpuCoresMapping.get(gpu.getManufacturerAndName())).build())
                .ifPresent(offer::setGpuDevice);
        return offer;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
