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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.beans.ConstructorProperties;
import java.time.LocalDateTime;
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
    @JsonProperty("issued_at")
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS"
    )
    private LocalDateTime issuedAt;
    @JsonProperty("expires_at")
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS"
    )
    private LocalDateTime expiresAt;
    private List<String> roles;
    private List<String> groups;

    @ConstructorProperties({"jwtTokenId", "userId", "userName", "orgUnitId", "issuedAt", "expiresAt", "roles", "groups"})
    JwtTokenClaims(String jwtTokenId, String userId, String userName, String orgUnitId, LocalDateTime issuedAt, LocalDateTime expiresAt, List<String> roles, List<String> groups) {
        this.jwtTokenId = jwtTokenId;
        this.userId = userId;
        this.userName = userName;
        this.orgUnitId = orgUnitId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
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
        private LocalDateTime issuedAt;
        private LocalDateTime expiresAt;
        private List<String> roles;
        private List<String> groups;

        JwtTokenClaimsBuilder() {
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder jwtTokenId(String jwtTokenId) {
            this.jwtTokenId = jwtTokenId;
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder orgUnitId(String orgUnitId) {
            this.orgUnitId = orgUnitId;
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder issuedAt(LocalDateTime issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder expiresAt(LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder roles(List<String> roles) {
            this.roles = Collections.unmodifiableList(roles);
            return this;
        }

        public JwtTokenClaims.JwtTokenClaimsBuilder groups(List<String> groups) {
            this.groups = Collections.unmodifiableList(groups);
            return this;
        }

        public JwtTokenClaims build() {
            return new JwtTokenClaims(this.jwtTokenId, this.userId, this.userName, this.orgUnitId, this.issuedAt, this.expiresAt, this.roles, this.groups);
        }

        public String toString() {
            return "JwtTokenClaims.JwtTokenClaimsBuilder(jwtTokenId=" + this.jwtTokenId + ", userId=" + this.userId + ", userName=" + this.userName + ", orgUnitId=" + this.orgUnitId + ", issuedAt=" + this.issuedAt + ", expiresAt=" + this.expiresAt + ", roles=" + this.roles + ")";
        }
    }
}
