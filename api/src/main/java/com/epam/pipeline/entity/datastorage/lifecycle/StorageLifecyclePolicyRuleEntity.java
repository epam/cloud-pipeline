/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.datastorage.lifecycle;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Getter
@Setter
@Builder
@Table(name = "datastorage_lifecycle_rule", schema = "pipeline")
public class StorageLifecyclePolicyRuleEntity {
    Long id;
    Long policyId;
    Long datastorageId;
    String pathRoot;
    String objectGlob;
    String transitionsJson;
}
