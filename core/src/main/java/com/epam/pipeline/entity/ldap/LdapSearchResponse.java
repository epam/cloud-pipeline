/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.ldap;

import lombok.Value;

import java.util.Collections;
import java.util.List;

@Value
public class LdapSearchResponse {
    List<LdapEntity> entities;
    LdapSearchResponseType type;

    public static LdapSearchResponse completed(final List<LdapEntity> entities) {
        return new LdapSearchResponse(entities, LdapSearchResponseType.COMPLETED);
    }

    public static LdapSearchResponse truncated(final List<LdapEntity> entities) {
        return new LdapSearchResponse(entities, LdapSearchResponseType.TRUNCATED);
    }
    
    public static LdapSearchResponse timedOut() {
        return new LdapSearchResponse(Collections.emptyList(), LdapSearchResponseType.TIMED_OUT);
    }
}
