/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.metadata;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;
import lombok.Data;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Data
public class CategoricalAttribute extends AbstractSecuredEntity {

    private String key;
    private List<CategoricalAttributeValue> values;
    private AclClass aclClass = AclClass.CATEGORICAL_ATTRIBUTE;

    public CategoricalAttribute() {
        setName(null);
        this.values = Collections.emptyList();
    }

    public CategoricalAttribute(final String key, final List<CategoricalAttributeValue> values) {
        this.values = values;
        setName(key);
        setCreatedDate(createdFromValues(values));
        final Optional<CategoricalAttributeValue> attributeValue = values.stream().findAny();
        setId(attributeValue.map(CategoricalAttributeValue::getAttributeId).orElse(null));
        setOwner(attributeValue.map(CategoricalAttributeValue::getOwner).orElse(null));
    }

    public void setKey(final String key) {
        setName(key);
    }

    @Override
    public void setName(final String name) {
        this.key = name;
        super.setName(key);
    }

    @Override
    public AbstractSecuredEntity getParent() {
        return null;
    }

    private Date createdFromValues(final List<CategoricalAttributeValue> values) {
        return values.stream()
            .map(CategoricalAttributeValue::getCreatedDate)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(new Date());
    }
}
