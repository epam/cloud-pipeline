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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;


/**
 * Describes lifecycle rules that should be applied for the objects in datastorage.
 *   pathRoot   - root folder of the lifecycle policy, this folder contains dataset folder, for these datasets actual
 *              rules should be created based on policy object
 *   objectGlob - string in unix glob format, describes which objects match a rule
 *   enabled    - shows if this policy enabled, if so rules based on this police should be created, dispute that flag
 *                could be set to false we still may create rules from this policy if there is a case, this flag
 *                basically for automation routine code
 * */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "datastorage_lifecycle_rule_template", schema = "pipeline")
public class StorageLifecycleRuleTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String description;
    private Long datastorageId;
    private String pathRoot;
    private String objectGlob;
    private Boolean enabled;
    private String transitionsJson;
    private String notificationJson;
}
