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

package com.epam.pipeline.entity.security;

import java.time.LocalDateTime;
import java.util.List;

import com.epam.pipeline.config.Constants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class JwtTokenClaims {
    public static final String CLAIM_USER_ID = "user_id";
    public static final String CLAIM_ORG_UNIT_ID = "org_unit_id";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_GROUPS = "groups";
    public static final String CLAIM_EXTERNAL = "external";

    @JsonProperty("jti")
    private String jwtTokenId;
    @JsonProperty("user_id")
    private String userId;
    @JsonProperty("username")
    private String userName;
    @JsonProperty("org_unit_id")
    private String orgUnitId;
    @JsonProperty("issued_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Constants.SECURITY_DATE_TIME_FORMAT)
    private LocalDateTime issuedAt;
    @JsonProperty("expires_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Constants.SECURITY_DATE_TIME_FORMAT)
    private LocalDateTime expiresAt;
    private List<String> roles;
    private List<String> groups;
    private boolean external;
}
