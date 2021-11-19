/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.service.impl.loader.CloudRegionLoader;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FileShareMountsService {

    private final Map<Long, Long> sharesWithRegions = new HashMap<>();
    private final Map<Long, MountType> sharesWithMountTypes = new HashMap<>();
    private final CloudRegionLoader regionLoader;

    public FileShareMountsService(final CloudRegionLoader regionLoader) {
        this.regionLoader = regionLoader;
    }

    public void updateSharesRegions() {
        final List<FileShareMount> fileShareMounts = regionLoader.loadAllEntities().stream()
            .map(EntityContainer::getEntity)
            .map(AbstractCloudRegion::getFileShareMounts)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
        sharesWithRegions.putAll(mapSharesUsingFunction(fileShareMounts, FileShareMount::getRegionId));
        sharesWithMountTypes.putAll(mapSharesUsingFunction(fileShareMounts, FileShareMount::getMountType));
    }

    public Long getRegionIdForShare(final Long fileShareId) {
        return sharesWithRegions.get(fileShareId);
    }

    public MountType getMountTypeForShare(final Long fileShareId) {
        return sharesWithMountTypes.get(fileShareId);
    }

    private <T> Map<Long, T> mapSharesUsingFunction(final List<FileShareMount> fileShareMounts,
                                                    final Function<FileShareMount, T> function) {
        return fileShareMounts.stream().collect(Collectors.toMap(FileShareMount::getId, function));
    }
}
