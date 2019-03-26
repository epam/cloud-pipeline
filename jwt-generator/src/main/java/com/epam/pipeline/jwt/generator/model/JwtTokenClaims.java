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

package com.epam.pipeline.jwt.generator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class JwtTokenClaims {
    public static final String CLAIM_USER_ID = "user_id";
    public static final String CLAIM_USER_NAME = "user_name";
    public static final String CLAIM_ORG_UNIT_ID = "org_unit_id";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_GROUP = "group";
    public static final String CLAIM_GROUPS = "groups";
    @JsonProperty("jti")
    private String jwtTokenId;
    @JsonProperty("user_id")
    private String userId;
    @JsonProperty("username")
    private String userName;
    @JsonProperty("org_unit_id")
    private String orgUnitId;
    private List<String> roles;
    private List<String> groups;

    JwtTokenClaims(String jwtTokenId, String userId, String userName, String orgUnitId,
                   List<String> roles, List<String> groups) {
        this.jwtTokenId = jwtTokenId;
        this.userId = userId;
        this.userName = userName;
        this.orgUnitId = orgUnitId;
        this.roles = Collections.unmodifiableList(roles);
        this.groups = Collections.unmodifiableList(groups);
    }

    public static JwtTokenClaims.JwtTokenClaimsBuilder builder() {
        return new JwtTokenClaims.JwtTokenClaimsBuilder();
    }

    public String getJwtTokenId() {
        return this.jwtTokenId;
    }

    public String getUserId() {
        return this.userId;
    }

    public String getUserName() {
        return this.userName;
    }

    public String getOrgUnitId() {
        return this.orgUnitId;
    }

    public List<String> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    public List<String> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public static class JwtTokenClaimsBuilder {
        private String jwtTokenId;
        private String userId;
        private String userName;
        private String orgUnitId;
        private List<String> roles;
        private List<String> groups;

        JwtTokenClaimsBuilder() {
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder withJwtTokenId(String jwtTokenId) {
            this.jwtTokenId = jwtTokenId;
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder withUserId(String userId) {
            this.userId = userId;
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder withUserName(String userName) {
            this.userName = userName;
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder withOrgUnitId(String orgUnitId) {
            this.orgUnitId = orgUnitId;
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder withRoles(List<String> roles) {
            this.roles = Collections.unmodifiableList(roles);
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder withGroups(List<String> groups) {
            this.groups = Collections.unmodifiableList(groups);
            return this;
        }

        public JwtTokenClaims build() {
            return new JwtTokenClaims(this.jwtTokenId, this.userId, this.userName,
                    this.orgUnitId, this.roles, this.groups);
        }
    }
}
