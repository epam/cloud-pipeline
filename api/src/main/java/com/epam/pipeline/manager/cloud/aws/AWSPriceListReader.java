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
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
public class AWSPriceListReader {

    private final Long regionId;
    private final Set<String> computeFamily;

    public AWSPriceListReader(final Long regionId, final Set<String> computeFamily) {
        this.regionId = regionId;
        this.computeFamily = CollectionUtils.isEmpty(computeFamily) ?
                Collections.singleton(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY) :
                computeFamily;
    }


    public List<InstanceOffer> readPriceCsv(BufferedReader reader) {
        try(CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .withIgnoreHeaderCase()
                .withTrim())) {

            return StreamSupport.stream(csvParser.spliterator(), false)
                    .map(this::parseRecord)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private InstanceOffer parseRecord(CSVRecord record) {
        InstanceOffer offer = new InstanceOffer();
        offer.setCloudProvider(CloudProvider.AWS);
        offer.setPriceListPublishDate(new Date());
        offer.setSku(record.get("sku"));
        offer.setTermType(record.get("termtype"));
        offer.setUnit(record.get("unit"));
        offer.setPricePerUnit(parseFloat(record.get("priceperunit")));
        offer.setCurrency(record.get("currency"));
        offer.setInstanceType(record.get("instance type"));
        offer.setTenancy(record.get("tenancy"));
        offer.setOperatingSystem(record.get("operating system"));
        offer.setProductFamily(parseProductFamily(record.get("product family")));
        offer.setVolumeType(record.get("volume type"));
        offer.setVCPU(parseInteger(record.get("vcpu")));
        offer.setGpu(parseInteger(record.get("gpu")));
        offer.setInstanceFamily(record.get("instance family"));
        offer.setRegionId(regionId);
        parseMemoryValue(offer, record.get("memory"));
        return offer;
    }

    private String parseProductFamily(final String productFamily) {
        if (computeFamily.contains(productFamily)) {
            return CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY;
        }
        return productFamily;
    }

    private void parseMemoryValue(InstanceOffer offer, String memoryValue) {
        if (StringUtils.isBlank(memoryValue)) {
            return;
        }
        String[] parts = memoryValue.trim().split(" ");
        if (parts.length < 2) {
            return;
        }
        if (NumberUtils.isNumber(parts[0])) {
            offer.setMemory(Float.parseFloat(parts[0]));
            offer.setMemoryUnit(parts[1]);
        }
    }

    private int parseInteger(String value) {
        if (StringUtils.isBlank(value) || !NumberUtils.isDigits(value)) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private float parseFloat(String value) {
        if (StringUtils.isBlank(value) || !NumberUtils.isNumber(value)) {
            return 0.0f;
        }
        return Float.parseFloat(value);
    }
}
