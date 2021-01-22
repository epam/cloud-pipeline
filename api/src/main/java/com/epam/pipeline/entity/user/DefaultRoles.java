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

package com.epam.pipeline.entity.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Provides access to predefined roles, created during deploy
 */
@Getter
@AllArgsConstructor
public enum DefaultRoles {

    ROLE_ADMIN(new Role(1L, "ROLE_ADMIN", true, false, null, null, null)),
    ROLE_USER(new Role(2L, "ROLE_USER", true, true, null, null, null)),
    ROLE_BILLING_MANAGER(new Role(null, "ROLE_BILLING_MANAGER", true, false, null, null, null)),
    ROLE_ANONYMOUS_USER(new Role(null, "ROLE_ANONYMOUS_USER", true, false, null, null, null));

    private Role role;

    public Long getId() {
        return role.getId();
    }

    public String getName() {
        return role.getName();
    }
}
