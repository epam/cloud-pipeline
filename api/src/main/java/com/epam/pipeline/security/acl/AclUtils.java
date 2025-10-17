/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.security.acl;

import com.epam.pipeline.entity.user.DefaultRoles;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.Sid;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AclUtils {

    private AclUtils() {}

    public static Map<SidType, List<Sid>> groupSidsByType(List<Sid> sids) {
        final List<String> roleNames = Arrays.stream(DefaultRoles.values()).map(DefaultRoles::getName)
                .collect(Collectors.toList());

        final List<Sid> principals = sids.stream().filter(sid -> sid instanceof PrincipalSid)
                .collect(Collectors.toList());

        final List<GrantedAuthoritySid> grantedAuthorities = sids.stream()
                .filter(sid -> sid instanceof GrantedAuthoritySid)
                .map(sid -> (GrantedAuthoritySid) sid)
                .collect(Collectors.toList());

        final List<Sid> groups = grantedAuthorities.stream()
                .filter(sid -> !roleNames.contains(sid.getGrantedAuthority()))
                .collect(Collectors.toList());
        final List<Sid> roles = grantedAuthorities.stream()
                .filter(sid -> roleNames.contains(sid.getGrantedAuthority()))
                .collect(Collectors.toList());

        return Stream.of(
                Pair.of(SidType.PRINCIPAL, principals), Pair.of(SidType.GROUP, groups), Pair.of(SidType.ROLE, roles )
        ).collect(Collectors.toMap(Pair::getKey,Pair::getValue));
    }

}
