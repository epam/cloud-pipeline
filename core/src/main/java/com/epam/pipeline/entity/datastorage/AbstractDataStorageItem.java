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

package com.epam.pipeline.entity.datastorage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;
import java.util.Map;

@Getter
@Setter
public abstract class AbstractDataStorageItem {
    private String name;
    private String path;
    private Map<String, String> labels;
    private Map<String, String> tags;
    @Setter(AccessLevel.PACKAGE)
    private DataStorageItemType type;

    @JsonIgnore
    public static Comparator<AbstractDataStorageItem> getStorageItemComparator() {
        return (o1, o2) -> {
            if (!o1.getPath().equals(o2.getPath())) {
                return o1.getPath().compareTo(o2.getPath());
            }
            if (o1.getType() != o2.getType()) {
                return o1.getType() == DataStorageItemType.File ? -1 : 1;
            }
            return 0;
        };
    }

}
