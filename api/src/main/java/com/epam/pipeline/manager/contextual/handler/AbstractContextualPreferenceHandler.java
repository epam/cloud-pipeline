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

package com.epam.pipeline.manager.contextual.handler;

import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import java.util.List;
import java.util.Optional;

abstract class AbstractContextualPreferenceHandler implements ContextualPreferenceHandler {

    private final Optional<ContextualPreferenceHandler> nextHandler;
    protected final ContextualPreferenceLevel level;

    AbstractContextualPreferenceHandler(final ContextualPreferenceLevel level,
                                        final ContextualPreferenceHandler nextHandler) {
        this.level = level;
        this.nextHandler = Optional.ofNullable(nextHandler);
    }

    @Override
    public boolean isValid(final ContextualPreference preference) {
        return preference.getResource().getLevel() == level
                ? externalEntityExists(preference)
                : validateUsingNextHandler(preference).orElse(false);
    }

    /**
     * Checks if the associated external entity exists or not.
     */
    abstract boolean externalEntityExists(ContextualPreference preference);

    /**
     * Delegate external entity existence check to the next handler.
     */
    Optional<Boolean> validateUsingNextHandler(final ContextualPreference preference) {
        return nextHandler.map(handler -> handler.isValid(preference));
    }

    /**
     * Delegate preference search to the next handler.
     */
    Optional<ContextualPreference> searchUsingNextHandler(final List<String> preferences,
                                                          final List<ContextualPreferenceExternalResource> resources) {
        return nextHandler.flatMap(handler -> handler.search(preferences, resources));
    }

}
