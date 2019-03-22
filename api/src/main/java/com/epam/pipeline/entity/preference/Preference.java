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

package com.epam.pipeline.entity.preference;

import java.util.Date;
import java.util.function.Function;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;

/**
 * Describes a system preference, that is stored in the database and controls some aspects of application runtime.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class Preference {
    private String name;
    private Date createdDate;
    private String value;
    private String preferenceGroup;
    private String description;

    /**
     * Defines if a preference should be visible to users on UI
     */
    private boolean visible = true;
    private PreferenceType type;

    public Preference(String name, String value, String group,
                      String description, PreferenceType type, boolean visible) {
        this.name = name;
        this.value = value;
        this.description = description;
        this.preferenceGroup = group;
        this.type = type;
        this.visible = visible;
    }

    public Preference(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Casts a value of a preference to a required type with a specified cast function
     * @param castFunction a function, that transforms string value of preference to a required type
     * @param <T> required preference type
     * @return required preference value
     */
    public <T> T get(Function<String, T> castFunction) {
        if (StringUtils.isNotBlank(value)) {
            return castFunction.apply(value);
        }

        return null;
    }
}
