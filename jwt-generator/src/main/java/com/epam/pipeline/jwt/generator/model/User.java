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

import java.util.ArrayList;
import java.util.List;

public class User {
    private String id;
    private String userName;
    private String orgUnit;
    private List<String> roles;
    private List<String> groups;

    private static final String CLAIM_DELIMITER = "=";

    public User(List<String> claims) {
        this.roles = new ArrayList<>();
        this.groups = new ArrayList<>();
        claims.forEach(claim -> {
            String[] parts = claim.split(CLAIM_DELIMITER);
            if (parts.length == 2) {
                String name = parts[0];
                String value = parts[1];
                switch (name) {
                    case JwtTokenClaims.CLAIM_USER_ID:
                        this.id = value;
                        break;
                    case JwtTokenClaims.CLAIM_USER_NAME:
                        this.userName = value;
                        break;
                    case JwtTokenClaims.CLAIM_ORG_UNIT_ID:
                        this.orgUnit = value;
                        break;
                    case JwtTokenClaims.CLAIM_ROLE:
                        this.roles.add(value);
                        break;
                    case JwtTokenClaims.CLAIM_GROUP:
                        this.groups.add(value);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    public JwtTokenClaims toClaims() {
        return JwtTokenClaims.builder()
                .userId(id)
                .userName(userName)
                .orgUnitId(orgUnit)
                .roles(roles)
                .groups(groups)
                .build();
    }
}
