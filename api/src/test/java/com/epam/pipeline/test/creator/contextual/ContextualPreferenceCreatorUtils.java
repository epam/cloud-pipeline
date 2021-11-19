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

package com.epam.pipeline.test.creator.contextual;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.ContextualPreferenceVO;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.contextual.ContextualPreferenceSearchRequest;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class ContextualPreferenceCreatorUtils {

    public static final TypeReference<Result<ContextualPreference>> CONTEXTUAL_PREFERENCE_TYPE =
            new TypeReference<Result<ContextualPreference>>() { };
    public static final TypeReference<Result<List<ContextualPreference>>> CONTEXTUAL_PREFERENCE_LIST_TYPE =
            new TypeReference<Result<List<ContextualPreference>>>() { };
    private static final PreferenceType PREFERENCE_TYPE = PreferenceType.STRING;
    private static final ContextualPreferenceLevel PREFERENCE_LEVEL = ContextualPreferenceLevel.ROLE;

    private ContextualPreferenceCreatorUtils() {

    }

    public static ContextualPreference getContextualPreference() {
        return new ContextualPreference(TEST_STRING, TEST_STRING);
    }

    public static ContextualPreferenceExternalResource getCPExternalResource() {
        return new ContextualPreferenceExternalResource(PREFERENCE_LEVEL, TEST_STRING);
    }

    public static ContextualPreferenceSearchRequest getCPSearchRequest() {
        return new ContextualPreferenceSearchRequest(
                Collections.singletonList(TEST_STRING), getCPExternalResource()
        );
    }

    public static ContextualPreferenceVO getContextualPreferenceVO() {
        return new ContextualPreferenceVO(
                TEST_STRING, TEST_STRING, PREFERENCE_TYPE, getCPExternalResource());
    }

    public static List<ContextualPreference> getContextualPreferenceList() {
        return Collections.singletonList(getContextualPreference());
    }
}
