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

package com.epam.pipeline.entity.metadata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"id"})
public class CategoricalAttributeValue {

    private Long id;
    private String key;
    private String value;
    private Boolean autofill;
    private List<CategoricalAttributeValue> links;

    public CategoricalAttributeValue(final String key, final String value) {
        this(null, key, value);
    }

    public CategoricalAttributeValue(final Long id, final String key, final String value) {
        this(id, key, value, null);
    }

    public CategoricalAttributeValue(final Long id, final String key, final String value, final Boolean autofill) {
        this(id, key, value, autofill, Collections.emptyList());
    }
}
