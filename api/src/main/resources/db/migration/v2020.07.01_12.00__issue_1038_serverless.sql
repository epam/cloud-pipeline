/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

CREATE SEQUENCE pipeline.s_stop_serverless_run START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS pipeline.stop_serverless_run (
    id BIGINT NOT NULL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES pipeline.pipeline_run(run_id),
    last_update TIMESTAMP WITH TIME ZONE NOT NULL
);
