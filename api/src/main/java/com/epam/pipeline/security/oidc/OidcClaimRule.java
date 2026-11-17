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

package com.epam.pipeline.security.oidc;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One condition on a claim of an incoming subject token. All the rules of an {@link OidcClient}
 * must pass for that client to match, so a rule list is an implicit AND.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OidcClaimRule {

    /**
     * The name of the claim to read, e.g. {@code repository_id} or {@code azp}.
     */
    private String claim;

    /**
     * How the claim is compared with {@link #value}.
     */
    private OidcClaimOperator operator;

    /**
     * The value the claim is compared with. Always a string: claims arrive as strings, numbers or
     * lists, and are compared in their string form so that an identifier sent as a number by one
     * identity provider and as a string by another behaves the same way.
     */
    private String value;
}
