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

import com.epam.pipeline.controller.vo.ContextualPreferenceVO;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.contextual.ContextualPreferenceSearchRequest;
import com.epam.pipeline.entity.preference.PreferenceType;

import java.util.Collections;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class ContextualPreferenceCreatorUtils {

    private static final PreferenceType preferenceType = PreferenceType.STRING;
    private static final ContextualPreferenceLevel preferenceLevel = ContextualPreferenceLevel.ROLE;

    private ContextualPreferenceCreatorUtils() {

    }

    public static ContextualPreference getContextualPreference() {
        final ContextualPreference contextualPreference = new ContextualPreference(TEST_STRING, TEST_STRING);
        return contextualPreference;
    }

    public static ContextualPreferenceExternalResource getCPExternalResource() {
        final ContextualPreferenceExternalResource contextualPreferenceExternalResource
                = new ContextualPreferenceExternalResource(preferenceLevel, TEST_STRING);
        return contextualPreferenceExternalResource;
    }

    public static ContextualPreferenceSearchRequest getCPSearchRequest() {
        final ContextualPreferenceSearchRequest searchRequest = new ContextualPreferenceSearchRequest(
                Collections.singletonList(TEST_STRING), getCPExternalResource()
        );
        return searchRequest;
    }

    public static ContextualPreferenceVO getContextualPreferenceVO() {
        final ContextualPreferenceVO contextualPreferenceVO = new ContextualPreferenceVO(
                TEST_STRING, TEST_STRING, preferenceType, getCPExternalResource()
        );
        return contextualPreferenceVO;
    }
}
