/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.security.saml.proxy;

import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.security.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collections;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class SAMLProxyAuthenticationTest {

    private static final String USER_NAME = "USER";
    private static final String GROUP = "GROUP";
    private static final String SAML_RESPONSE = "response";

    @Test
    public void shouldGrantTheRolesAndGroupsOfTheUser() {
        final PipelineUser user = new PipelineUser(USER_NAME);
        user.setRoles(Collections.singletonList(DefaultRoles.ROLE_USER.getRole()));
        user.setGroups(Collections.singletonList(GROUP));

        final SAMLProxyAuthentication authentication =
                new SAMLProxyAuthentication(SAML_RESPONSE, new UserContext(user));

        assertThat(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()))
                .containsExactlyInAnyOrder(DefaultRoles.ROLE_USER.getName(), GROUP);
        assertThat(authentication.getName()).isEqualTo(USER_NAME);
    }

    @Test
    public void shouldGrantNothingWithoutAUser() {
        assertThat(new SAMLProxyAuthentication(SAML_RESPONSE, null).getAuthorities()).isEmpty();
    }
}
