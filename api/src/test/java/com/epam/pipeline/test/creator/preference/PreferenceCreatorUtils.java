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

package com.epam.pipeline.test.creator.preference;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.Date;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class PreferenceCreatorUtils {

    public static final TypeReference<Result<Collection<Preference>>> PREFERENCE_LIST_TYPE =
            new TypeReference<Result<Collection<Preference>>>() { };
    public static final TypeReference<Result<Preference>> PREFERENCE_TYPE =
            new TypeReference<Result<Preference>>() { };

    private PreferenceCreatorUtils() {

    }

    public static Preference getPreference() {
        final Preference preference = new Preference();
        preference.setCreatedDate(new Date());
        preference.setDescription(TEST_STRING);
        preference.setType(PreferenceType.STRING);
        preference.setName(TEST_STRING);
        return preference;
    }
}
