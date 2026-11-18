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

import lombok.Getter;
import lombok.Setter;

/**
 * How {@link OidcAssumeStrategyType#MAPPED_USER} finds the platform user: the value of
 * {@link #claim} is looked for in the {@link #metadataKey} attribute of a user's metadata.
 * <p>
 * It exists for a provider that identifies a caller by something the platform does not store on the
 * user — a GitHub organisation, a workload identity, an employee number. Recording that value as
 * user metadata lets a deployment map it without renaming anybody.
 */
@Getter
@Setter
public class OidcUserMapping {

    /**
     * The claim whose value identifies the user. Chosen by the configuration, never by the caller.
     */
    private String claim;

    /**
     * The user metadata attribute the claim's value is matched against. Exactly one user may carry
     * that value.
     */
    private String metadataKey;
}
