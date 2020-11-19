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

package com.epam.pipeline.entity.metadata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MetadataField {
    private String name;
    @JsonIgnore
    private String dbName;
    private boolean predefined = false;

    public MetadataField(String name, String dbName, boolean predefined) {
        this.name = name;
        this.dbName = dbName;
        this.predefined = predefined;
    }

    public MetadataField() {

    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MetadataField that = (MetadataField) o;

        if (predefined != that.predefined) {
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        return dbName != null ? dbName.equals(that.dbName) : that.dbName == null;
    }

    @Override public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (dbName != null ? dbName.hashCode() : 0);
        result = 31 * result + (predefined ? 1 : 0);
        return result;
    }
}
