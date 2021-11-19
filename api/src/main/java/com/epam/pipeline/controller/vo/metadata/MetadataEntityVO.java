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

package com.epam.pipeline.controller.vo.metadata;

import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@EqualsAndHashCode
public class MetadataEntityVO {
    private Long entityId;
    private Long classId;
    private Long parentId;
    private String entityName;
    private String externalId;
    private String className;
    private Map<String, PipeConfValue> data;

    public MetadataEntity convertToMetadataEntity() {
        MetadataEntity metadataEntity = new MetadataEntity();
        MetadataClass metadataClass = new MetadataClass();
        metadataClass.setId(classId);
        metadataClass.setName(className);
        if (parentId != null) {
            metadataEntity.setParent(new Folder(parentId));
        }
        metadataEntity.setId(entityId);
        metadataEntity.setName(entityName);
        metadataEntity.setExternalId(externalId);
        metadataEntity.setClassEntity(metadataClass);
        metadataEntity.setData(data);
        return metadataEntity;
    }
}
