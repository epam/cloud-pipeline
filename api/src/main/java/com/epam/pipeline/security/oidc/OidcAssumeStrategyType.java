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
 * Which platform user an exchanged token is issued for.
 */
public enum OidcAssumeStrategyType {

    /**
     * A fixed user named in the configuration, for CI jobs and other service accounts whose
     * subject token names no person.
     */
    SPECIFIC_USER,

    /**
     * The user named by a claim of the subject token. The claim is chosen by the configuration,
     * never by the caller, so this is not impersonation.
     */
    REQUESTED_USER
}
