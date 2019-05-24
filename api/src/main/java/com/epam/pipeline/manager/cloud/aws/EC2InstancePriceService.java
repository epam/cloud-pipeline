/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cloud.aws;

import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.cluster.AwsPriceListReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EC2InstancePriceService implements CloudInstancePriceService<AwsRegion> {
    private static final String AWS_EC2_PRICING_URL_TEMPLATE =
            "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/%s/index.csv";
    private static final int COLUMNS_LINE_INDEX = 5;

    private final InstanceOfferDao instanceOfferDao;
    private final EC2Helper ec2Helper;

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    @Override
    public List<InstanceOffer> refreshPriceListForRegion(final AwsRegion region) {
        final String url = String.format(AWS_EC2_PRICING_URL_TEMPLATE, region.getRegionCode());
        try (InputStream input = new URL(url).openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            //skip first lines
            int skipLines = COLUMNS_LINE_INDEX;
            while (skipLines > 0) {
                final String line = reader.readLine();
                if (line == null) {
                    return Collections.emptyList();
                }
                skipLines--;
            }
            return new AwsPriceListReader(region.getId()).readPriceCsv(reader);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public double getSpotPrice(final String instanceType, final AwsRegion region) {
        return ec2Helper.getSpotPrice(instanceType, region);
    }

    @Override
    public double getPriceForDisk(final List<InstanceOffer> offers, final int instanceDisk,
                                 final String instanceType, final AwsRegion region) {
        if (offers.size() == 1) {
            return offers.get(0).getPricePerUnit() / (DAYS_IN_MONTH * HOURS_IN_DAY) * instanceDisk;
        }
        return 0;
    }

    @Override
    public List<InstanceType> getAllInstanceTypes(final Long regionId, final Boolean spot) {
        InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setTermType(PriceType.ON_DEMAND.getName());
        requestVO.setOperatingSystem(CloudInstancePriceService.LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(CloudInstancePriceService.SHARED_TENANCY);
        requestVO.setUnit(CloudInstancePriceService.HOURS_UNIT);
        requestVO.setProductFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY);
        requestVO.setRegionId(regionId);
        return instanceOfferDao.loadInstanceTypes(requestVO);
    }
}
