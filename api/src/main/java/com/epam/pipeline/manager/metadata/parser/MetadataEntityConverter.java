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

package com.epam.pipeline.manager.metadata.parser;

import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.EnumMap;

public final class MetadataEntityConverter {

    public static Map<String, String> convert(List<MetadataEntity> entities) {
        Map<FireCloudClass, FireCloudData> content = new EnumMap<>(FireCloudClass.class);
        entities.forEach(entity -> {
            FireCloudClass fireCloudClass = entity.getClassEntity().getFireCloudClassName();
            Assert.notNull(fireCloudClass, "Fire Cloud class must be specified.");
            FireCloudData dataContent = content.computeIfAbsent(fireCloudClass, key ->
                    new FireCloudData(fireCloudClass, entity.getData()));
            dataContent.put(entity);
        });
        Map<String, String> result = new HashMap<>();
        content.forEach(((fireCloudClass, fireCloudData) -> {
            result.put(fireCloudClass.getFileName(), fireCloudData.getContent());
            if (fireCloudData.isSet()) {
                result.put(fireCloudClass.getMembershipFileName(), fireCloudData.getMembershipContent());
            }
        }));
        return result;
    }
    private MetadataEntityConverter() {
        // no-op
    }
}
