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

package com.epam.pipeline.dao.region;

enum CloudRegionParameters {
    REGION_ID,
    REGION_NAME,
    NAME,
    IS_DEFAULT,
    CORS_RULES,
    POLICY,
    KMS_KEY_ID,
    KMS_KEY_ARN,
    OWNER,
    CREATED_DATE,
    PROFILE,
    TEMP_CREDENTIALS_ROLE,
    BACKUP_DURATION,
    VERSIONING_ENABLED,
    SSH_KEY_NAME,
    CLOUD_PROVIDER,
    STORAGE_ACCOUNT,
    STORAGE_ACCOUNT_KEY,
    RESOURCE_GROUP,
    SUBSCRIPTION,
    AUTH_FILE,
    SSH_PUBLIC_KEY,
    METER_REGION_NAME,
    AZURE_API_URL,
    PRICE_OFFER_ID,
    PROJECT,
    MOUNT_ID,
    MOUNT_ROOT,
    MOUNT_OPTIONS,
    MOUNT_TYPE;
}
