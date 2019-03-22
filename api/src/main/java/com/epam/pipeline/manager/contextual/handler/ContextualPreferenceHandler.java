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
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import java.util.List;
import java.util.Optional;

/**
 * Element of context preference handlers chain of responsibility.
 *
 * @see <a href="https://refactoring.guru/design-patterns/chain-of-responsibility">Chain of responsibility pattern</a>
 */
public interface ContextualPreferenceHandler {

    /**
     * Checks if the given preference's external resource exists.
     *
     * True means that the preference is valid to be stored in a contextual preferences table.
     *
     * @return True if this or any of the underlying handlers has validated external resource existence.
     */
    boolean isValid(ContextualPreference preference);

    /**
     * Searches for a contextual preference that is related to some of the given resources.
     *
     * If there is no suitable resource than handler should delegate search to the next handler
     * or return an empty optional otherwise.
     *
     * It consistently searches for a preference by the names specified in given preferences list.
     *
     * F.e.
     *
     * With preferences = ['preference1', 'preference2']
     * If preference with name 'preference1' exists then it will be returned.
     *
     * With preferences = ['preference1', 'preference2']
     * If preference with name 'preference1' does not exist but another preference with
     * name 'preference2' exists  then the second one will be returned.
     *
     * With preferences = ['preference1', 'preference2']
     * If none preferences exist then nothing will be returned.
     *
     * @param preferences Contextual preferences names to be searched.
     * @param resources   List of requested or intermediate resources.
     * @return Contextual preference with one of the given names or nothing.
     */
    Optional<ContextualPreference> search(List<String> preferences,
                                          List<ContextualPreferenceExternalResource> resources);
}
