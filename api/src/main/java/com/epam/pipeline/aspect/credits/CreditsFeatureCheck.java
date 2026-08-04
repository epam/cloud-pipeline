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

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that requires the platform usage credits feature to be active.
 *
 * <p>When applied to a method, {@link CreditsFeatureAspect} intercepts the call before
 * it executes and throws {@link UnsupportedOperationException} if the
 * {@code platform.usage.credits.mode} preference is set to
 * {@link com.epam.pipeline.dto.credits.PlatformUsageCreditsMode#OFF}.
 *
 * <p>Annotate API-facing methods that expose credits data or management operations
 * (balance queries, manual adjustments, etc.) with this annotation to ensure they
 * fail fast and clearly when the feature is turned off, rather than returning
 * misleading empty results.
 *
 * <p>The annotation is {@link Inherited}, so it propagates through class hierarchies
 * when placed on an overridable method.
 *
 * @see CreditsFeatureAspect
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CreditsFeatureCheck {
}
