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

CREATE TYPE pipeline.datastorage_item_type AS ENUM ('FOLDER', 'FILE');
CREATE TYPE pipeline.datastorage_sid_type AS ENUM ('USER', 'GROUP');

CREATE TABLE IF NOT EXISTS pipeline.datastorage_permission (
    datastorage_root_id      BIGINT                   NOT NULL,
    datastorage_path         TEXT                     NOT NULL,
    datastorage_type         datastorage_item_type    NOT NULL,
    sid_name                 TEXT                     NOT NULL,
    sid_type                 datastorage_sid_type     NOT NULL,
    mask                     INT                      NOT NULL,
    created                  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (datastorage_root_id, datastorage_path, datastorage_type, sid_name, sid_type),
    CONSTRAINT datastorage_tag_datastorage_root_id
        FOREIGN KEY (datastorage_root_id)
        REFERENCES pipeline.datastorage_root (datastorage_root_id)
);
