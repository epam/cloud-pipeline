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

package com.epam.pipeline.security.saml;

/**
 * Represents the SAML user registration strategies set into saml.user.auto.create property.
 * AUTO - creates a new user if not exists
 * EXPLICIT - requires users pre-registration id database (performs by admin)
 * EXPLICIT_GROUP - requires specific groups pre-registration. If users SAML groups have no intersections with
 * registered security groups the authentication will be failed.
 */
public enum SamlUserRegisterStrategy {
    AUTO, EXPLICIT, EXPLICIT_GROUP
}
