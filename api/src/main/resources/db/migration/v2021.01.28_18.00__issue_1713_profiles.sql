/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE TABLE cloud_profile_credentials (
    id SERIAL PRIMARY KEY,
    cloud_provider varchar,
    policy varchar,
    profile_name varchar,
    assumed_role varchar
);
CREATE TABLE pipeline.cloud_profile_credentials_user (
    PRIMARY KEY (cloud_profile_credentials_id, user_id),
    cloud_profile_credentials_id integer NOT NULL REFERENCES pipeline.cloud_profile_credentials(id),
    user_id integer NOT NULL REFERENCES pipeline.user(id)
);
CREATE TABLE pipeline.cloud_profile_credentials_role (
    PRIMARY KEY (cloud_profile_credentials_id, role_id),
    cloud_profile_credentials_id integer NOT NULL REFERENCES pipeline.cloud_profile_credentials(id),
    role_id integer NOT NULL REFERENCES pipeline.role(id)
);
ALTER TABLE pipeline.user ADD COLUMN default_profile_id integer REFERENCES pipeline.cloud_profile_credentials(id) NULL;
ALTER TABLE pipeline.role ADD COLUMN default_profile_id integer REFERENCES pipeline.cloud_profile_credentials(id) NULL;
