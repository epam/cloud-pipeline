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

CREATE TABLE IF NOT EXISTS pipeline.datastorage_tool_version (
    datastorage_id INT NOT NULL REFERENCES pipeline.DATASTORAGE (DATASTORAGE_ID) ON DELETE CASCADE,
    tool_id INT NOT NULL REFERENCES pipeline.TOOL (ID) ON DELETE CASCADE,
    tool_version_id INT DEFAULT NULL REFERENCES pipeline.TOOL_VERSION (ID) ON DELETE CASCADE,
    CONSTRAINT unique_datastorage_tool_version UNIQUE(datastorage_id, tool_id, tool_version_id)
);

