/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.monitor.monitoring.quota;

import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Caches a username → authorities (groups + roles) mapping loaded from the Cloud Pipeline API.
 *
 * Call {@link #load(List)} once before each quota evaluation cycle so that
 * {@link ComputeQuotaRuleEvaluator} can resolve group memberships without per-run API calls.
 */
@Slf4j
@Component
public class UserGroupHolder {

    private volatile Map<String, Set<String>> userToAuthorities = Collections.emptyMap();

    /**
     * Rebuilds the cache from a fresh user list.
     * Each user's authorities = their explicit groups + all role names.
     */
    public void load(final List<PipelineUser> users) {
        final Map<String, Set<String>> map = new HashMap<>();
        for (final PipelineUser user : users) {
            final Set<String> authorities = new HashSet<>();
            if (user.getGroups() != null) {
                authorities.addAll(user.getGroups());
            }
            if (user.getRoles() != null) {
                authorities.addAll(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList()));
            }
            map.put(user.getUserName(), Collections.unmodifiableSet(authorities));
        }
        this.userToAuthorities = Collections.unmodifiableMap(map);
        log.debug("UserGroupHolder loaded {} users", map.size());
    }

    /**
     * Returns the set of authorities (groups + roles) for the given username,
     * or an empty set when the user is unknown or the username is null.
     */
    public Set<String> getGroupsForUser(final String username) {
        if (username == null) {
            return Collections.emptySet();
        }
        return userToAuthorities.getOrDefault(username, Collections.emptySet());
    }
}
