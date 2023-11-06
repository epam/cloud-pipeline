/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
 *  limitations under the License.
 */

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InstanceTypeCRUDService {

    private final InstanceOfferDao instanceOfferDao;

    public Optional<InstanceType> find(final CloudProvider provider, final Long regionId, final String name) {
        return find(provider, regionId).stream()
                .filter(type -> Objects.equals(name, type.getName()))
                .findFirst();
    }

    public List<InstanceType> find(final CloudProvider provider, final Long regionId) {
        final InstanceOfferRequestVO request = new InstanceOfferRequestVO();
        request.setTermType(CloudInstancePriceService.TermType.ON_DEMAND.getName());
        request.setOperatingSystem(CloudInstancePriceService.LINUX_OPERATING_SYSTEM);
        request.setTenancy(CloudInstancePriceService.SHARED_TENANCY);
        request.setUnit(CloudInstancePriceService.HOURS_UNIT);
        request.setProductFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY);
        request.setRegionId(regionId);
        request.setCloudProvider(provider.name());
        return ListUtils.emptyIfNull(instanceOfferDao.loadInstanceTypes(request));
    }
}
