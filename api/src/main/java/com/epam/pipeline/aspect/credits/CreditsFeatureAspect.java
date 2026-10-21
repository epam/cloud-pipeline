/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.aspect.credits;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsMode;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Service;

/**
 * AOP aspect that enforces the platform usage credits feature gate.
 *
 * <p>Intercepts all methods annotated with {@link CreditsFeatureCheck} and throws
 * {@link UnsupportedOperationException} before the method body runs when the
 * {@code platform.usage.credits.mode} preference equals
 * {@link com.epam.pipeline.dto.credits.PlatformUsageCreditsMode#OFF}.
 *
 * <p>This prevents credits API endpoints from returning empty or misleading results
 * while the feature is disabled, and surfaces a clear error message to callers.
 * When the mode is {@code BALANCE_ONLY} or {@code ON} the intercepted method executes
 * normally without any interference from this aspect.
 *
 * @see CreditsFeatureCheck
 */
@Service
@Aspect
@Slf4j
@RequiredArgsConstructor
public class CreditsFeatureAspect {

    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    @Before("@annotation(com.epam.pipeline.aspect.credits.CreditsFeatureCheck)")
    public void checkCreditsFeatureEnabled() {
        if (PlatformUsageCreditsMode.OFF.equals(
                preferenceManager.getPreference(SystemPreferences.USAGE_CREDITS_MODE))) {
            throw new UnsupportedOperationException(
                    messageHelper.getMessage(MessageConstants.ERROR_PLATFORM_USAGE_CREDITS_DISABLED));
        }
    }
}
