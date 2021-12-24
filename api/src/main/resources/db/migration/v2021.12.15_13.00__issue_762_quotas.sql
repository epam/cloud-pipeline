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

CREATE TABLE IF NOT EXISTS pipeline.quota (
    id SERIAL PRIMARY KEY,
    quota_group TEXT NOT NULL,
    type TEXT,
    period TEXT,
    subject TEXT,
    value DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS pipeline.quota_action (
    id SERIAL PRIMARY KEY,
    quota_id BIGINT REFERENCES pipeline.quota(id),
    threshold DOUBLE PRECISION NOT NULL,
    actions TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pipeline.quota_entity_recipients (
    quota_entity_id BIGINT REFERENCES pipeline.quota(id),
    principal BOOLEAN NOT NULL,
    name TEXT NOT NULL
);
