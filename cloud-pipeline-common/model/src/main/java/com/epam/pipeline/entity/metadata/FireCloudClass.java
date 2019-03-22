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

package com.epam.pipeline.entity.metadata;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FireCloudClass {
    PARTICIPANT("participant", "entity:participant_id"),
    SAMPLE("sample", "entity:sample_id"),
    PAIR("pair", "entity:pair_id"),
    PARTICIPANT_SET("participant_set_entity", "entity:participant_set_id", "participant_set_membership",
            "membership:participant_set_id", "participant"),
    SAMPLE_SET("sample_set_entity", "entity:sample_set_id", "sample_set_membership", "membership:sample_set_id",
            "sample"),
    PAIR_SET("pair_set_entity", "entity:pair_set_id", "pair_set_membership", "membership:pair_set_id", "pair");

    private String fileName;
    private String headerEntityId;
    private String membershipFileName;
    private String membershipHeaderEntityId;
    private String membershipEntity;

    FireCloudClass(String fileName, String headerEntityId) {
        this.fileName = fileName;
        this.headerEntityId = headerEntityId;
    }
}
