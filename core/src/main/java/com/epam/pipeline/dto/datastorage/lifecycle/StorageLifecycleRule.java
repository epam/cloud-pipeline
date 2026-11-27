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

package com.epam.pipeline.dto.datastorage.lifecycle;

import com.epam.pipeline.dto.datastorage.lifecycle.transition.StorageLifecycleRuleTransition;
import com.epam.pipeline.dto.datastorage.lifecycle.transition.StorageLifecycleTransitionCriterion;
import com.epam.pipeline.dto.datastorage.lifecycle.transition.StorageLifecycleTransitionMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;


/**
 * Describes a rule defines lifecycle of objects in a cloud:

 *   - Which directories to search for a files. {@code pathGlob}
 *   - What files. {@code objectGlob}
 *   - When files should be transferred. See {@link StorageLifecycleRuleTransition}
 *          and {@link StorageLifecycleRuleProlongation}
 *   - Where files should be transferred. See {@link StorageLifecycleRuleTransition}
 *   - How files should be transferred. See {@link StorageLifecycleTransitionMethod}

 * Also {@code notification} could be configured to notify user that this rule is going to be applied and prolong
 * if needed.


 * Example 1:
 * We have a rule:
 *     {
 *        "pathGlob": "/data/",
 *        "objectGlob": "*.csv",
 *        "transitionMethod": "LATEST_FILE",
 *        "transitions": [
 *             {
 *                 "transitionAfterDays": 10,
 *                 "storageClass": Glacier
 *             }
 *        ]
 *     }

 * For such rule we will move all csv files from directory /data/ only when latest file will have an age of 10 days.


 * Example 2:
 * If {@code glob} is provided, eligibility for transitions files matches {@link StorageLifecycleRule} should
 * be checked based on files that match this glob, instead of objectGlob from {@link StorageLifecycleRule}

 * This value will be resolved against path for which {@link StorageLifecycleRule} is applied.

 * We have a rule:
 *     {
 *        "pathGlob": "/data/",
 *        "objectGlob": "*.csv",
 *        "transitionMethod": "LATEST_FILE",
 *        "transitionCriterion": {
 *            "type": "MATCHING_FILES",
 *            "value": "*.pdf",
 *        },
 *        "transitions": [
 *             {
 *                 "transitionAfterDays": 10,
 *                 "storageClass": Glacier
 *             }
 *        ]
 *     }

 * For such rule we will move all csv files from directory /data/ only when in this directory will be located
 * pdf files and latest one will have an age of 10 days.

 * This solves a case when we need to transit one file type depending on condition of another file type
 * (f.e. when we generate one files from another and want to transit after successful generation)

 * For AWS see useful information about some limitation on lifecycle policies:
 * https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-transition-general-considerations.html
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class StorageLifecycleRule {
    private Long id;
    private Long datastorageId;
    private String pathGlob;
    private String objectGlob;
    private StorageLifecycleTransitionCriterion transitionCriterion;
    private StorageLifecycleTransitionMethod transitionMethod;
    private List<StorageLifecycleRuleProlongation> prolongations;
    private List<StorageLifecycleRuleTransition> transitions;
    private StorageLifecycleNotification notification;

    public String toDescriptionString() {
        return "id: " + id + ", datastorageId: " + datastorageId + ", pathGlob: " + pathGlob +
                ", objectGlob: " + objectGlob +
                ", transitionCriterion: " + transitionCriterion.toDescriptionString() +
                ", transitionMethod: " + transitionMethod.name() +
                ", transitions: " + transitions.stream().map(t ->
                        "[to " + t.getStorageClass()
                                + (t.getTransitionDate() != null
                                        ? " on " + t.getTransitionDate()
                                        : " after " + t.getTransitionAfterDays().toString() + " days") + "]"
                ).collect(Collectors.joining(";"));
    }
}
