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

package com.epam.pipeline.entity.contextual;

import com.epam.pipeline.entity.preference.PreferenceType;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

/**
 * Contextual preference.
 *
 * Represents a specific preference for some external {@link #resource}.
 *
 * A tuple of {@link #name}, {@link #resource} is a contextual preference primary key.
 */
@Value
@Wither
@AllArgsConstructor
public class ContextualPreference {
    private final String name;
    private final String value;
    private final PreferenceType type;
    private final Date createdDate;
    private final ContextualPreferenceExternalResource resource;

    public ContextualPreference(final String name, final String value, final PreferenceType type,
                                final ContextualPreferenceExternalResource resource) {
        this(name, value, type, null, resource);
    }

    public ContextualPreference(final String name, final String value, final PreferenceType type) {
        this(name, value, type, null, null);
    }

    public ContextualPreference(final String name, final String value,
                                final ContextualPreferenceExternalResource resource) {
        this(name, value, PreferenceType.STRING, null, resource);
    }

    public ContextualPreference(final String name, final String value) {
        this(name, value, PreferenceType.STRING, null, null);
    }
}
