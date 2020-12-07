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

package com.epam.pipeline.test.creator.metadata;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.controller.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataClassDescription;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.metadata.MetadataField;
import com.epam.pipeline.entity.metadata.MetadataFilter;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class MetadataCreatorUtils {

    public static final TypeReference<Result<CategoricalAttribute>> ATTRIBUTE_INSTANCE_TYPE =
            new TypeReference<Result<CategoricalAttribute>>() {};
    public static final TypeReference<Result<MetadataEntry>> METADATA_ENTRY_TYPE =
            new TypeReference<Result<MetadataEntry>>() {};
    public static final TypeReference<Result<MetadataClass>> METADATA_CLASS_TYPE =
            new TypeReference<Result<MetadataClass>>() {};
    public static final TypeReference<Result<MetadataEntity>> METADATA_ENTITY_TYPE =
            new TypeReference<Result<MetadataEntity>>() {};
    public static final TypeReference<Result<Result>> RESULT_TYPE =
            new TypeReference<Result<Result>>() {};
    public static final TypeReference<Result<List<CategoricalAttribute>>> ATTRIBUTE_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<CategoricalAttribute>>>() {};
    public static final TypeReference<Result<List<MetadataEntry>>> METADATA_ENTRY_LIST_TYPE =
            new TypeReference<Result<List<MetadataEntry>>>() {};
    public static final TypeReference<Result<List<EntityVO>>> ENTITY_VO_LIST_TYPE =
            new TypeReference<Result<List<EntityVO>>>() {};
    public static final TypeReference<Result<List<MetadataEntryWithIssuesCount>>>
            METADATA_ENTRY_WITH_ISSUES_COUNT_LIST_TYPE =
            new TypeReference<Result<List<MetadataEntryWithIssuesCount>>>() {};
    public static final TypeReference<Result<List<MetadataClass>>> METADATA_CLASS_LIST_TYPE =
            new TypeReference<Result<List<MetadataClass>>>() {};
    public static final TypeReference<Result<PagedResult<List<MetadataEntity>>>> PAGED_RESULT_ENTITY_LIST_TYPE =
            new TypeReference<Result<PagedResult<List<MetadataEntity>>>>() {};
    public static final TypeReference<Result<List<MetadataEntity>>> METADATA_ENTITY_LIST_TYPE =
            new TypeReference<Result<List<MetadataEntity>>>() {};
    public static final TypeReference<Result<List<MetadataField>>> METADATA_FIELD_LIST_TYPE =
            new TypeReference<Result<List<MetadataField>>>() {};
    public static final TypeReference<Result<List<MetadataClassDescription>>> CLASS_DESCRIPTION_LIST_TYPE =
            new TypeReference<Result<List<MetadataClassDescription>>>() {};

    private MetadataCreatorUtils() {

    }

    public static CategoricalAttribute getCategoricalAttribute() {
        return new CategoricalAttribute(TEST_STRING, Collections.singletonList(getCategoricalAttributeValue()));
    }

    public static CategoricalAttributeValue getCategoricalAttributeValue() {
        return new CategoricalAttributeValue();
    }

    public static MetadataVO getMetadataVO() {
        return new MetadataVO();
    }

    public static MetadataEntry getMetadataEntry() {
        return new MetadataEntry();
    }

    public static EntityVO getEntityVO() {
        return new EntityVO();
    }

    public static MetadataClass getMetadataClass() {
        return new MetadataClass();
    }

    public static MetadataEntity getMetadataEntity() {
        return new MetadataEntity();
    }

    public static MetadataFilter getMetadataFilter() {
        return new MetadataFilter();
    }

    public static MetadataField getMetadataField() {
        return new MetadataField(TEST_STRING, null, false);
    }

    public static MetadataClassDescription getMetadataClassDescription() {
        return new MetadataClassDescription();
    }

    public static MetadataEntityVO getMetadataEntityVO() {
        return new MetadataEntityVO();
    }

    public static List<MetadataEntryWithIssuesCount> getListOfEntryWithIssuesCount() {
        return Collections.singletonList(new MetadataEntryWithIssuesCount());
    }

    public static PagedResult<List<MetadataEntity>> getPagedResult() {
        return new PagedResult<>(Collections.singletonList(getMetadataEntity()), TEST_INT);
    }
}
