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

/**
 * How an {@link OidcClaimRule} compares a claim of an incoming subject token with its configured
 * value. A claim that the token does not carry never matches, whatever the operator.
 */
public enum OidcClaimOperator {

    /**
     * The claim's string form equals the configured value, case-sensitively.
     */
    EQUALS,

    /**
     * The claim is a list and the configured value is one of its elements.
     */
    CONTAINS,

    /**
     * The claim's string form matches the configured glob, where {@code *} stands for any run of
     * characters and {@code ?} for exactly one.
     */
    LIKE
}
