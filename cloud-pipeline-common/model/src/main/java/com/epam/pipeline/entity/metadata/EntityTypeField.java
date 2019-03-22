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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@EqualsAndHashCode
public class EntityTypeField {
    public static final String REFERENCE_SUFFIX = "ID";
    public static final String NAME_DELIMITER = ":";
    public static final String DEFAULT_TYPE = "string";
    public static final String ARRAY_TYPE = "Array[%s]";
    public static final String PATH_TYPE = "Path";
    private static final Pattern ARRAY_PATTERN = Pattern.compile("Array\\[(\\w+)]");
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("(\\w+):ID");

    private String name;
    private String type;
    private boolean reference = false;
    private boolean multiValue = false;

    public EntityTypeField(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public EntityTypeField(String name) {
        this(name, DEFAULT_TYPE);
    }

    public EntityTypeField(String name, String type, boolean reference, boolean multiValue) {
        this(name, type);
        this.reference = reference;
        this.multiValue = multiValue;
    }

    @JsonIgnore
    public String getTypeString() {
        if (multiValue) {
            return String.format(ARRAY_TYPE, type);
        } else if (reference) {
            return type + NAME_DELIMITER + REFERENCE_SUFFIX;
        } else {
            return type;
        }
    }

    public static boolean isReferenceType(String type) {
        return !StringUtils.isBlank(type) && REFERENCE_PATTERN.matcher(type).matches();
    }

    public static boolean isArrayType(String type) {
        return !StringUtils.isBlank(type) && ARRAY_PATTERN.matcher(type).matches();
    }

    public static EntityTypeField parseFromStringType(String name, String type) {
        String className = parseClass(type);
        if (StringUtils.isNotBlank(className)) {
            return new EntityTypeField(name, className);
        } else {
            return new EntityTypeField(name, type);
        }
    }

    public static String parseClass(String type) {
        Matcher matcher = REFERENCE_PATTERN.matcher(type);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        matcher = ARRAY_PATTERN.matcher(type);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
}
