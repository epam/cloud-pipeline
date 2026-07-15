/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.dto.credits;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.dto.credits.FieldType.BOOLEAN;
import static com.epam.pipeline.dto.credits.FieldType.ENUM;
import static com.epam.pipeline.dto.credits.FieldType.GROUPS;
import static com.epam.pipeline.dto.credits.FieldType.NUMERIC;
import static com.epam.pipeline.dto.credits.FieldType.STRING;
import static com.epam.pipeline.dto.credits.FieldType.TAGS;

/**
 * Filterable fields available in compute quota rule expressions.
 *
 * This is the API-side counterpart of the monitoring-service {@code RunField}: it carries
 * the same field metadata (type, display names, duration support) but omits the
 * {@code PipelineRun} extractor, which is only needed at evaluation time in the
 * monitoring service.
 */
public enum PipelineRunField {

    /** Numeric run identifier. Expression names: {@code run.id}, {@code id}. */
    RUN_ID(NUMERIC, false, "run.id", "id"),

    /**
     * Current run status (e.g. {@code RUNNING}, {@code STOPPED}).
     * Expression names: {@code run.status}, {@code status}.
     */
    STATUS(ENUM, false, "run.status", "status"),

    /**
     * Node instance type (e.g. {@code m5.xlarge}). Supports wildcard patterns.
     * Expression name: {@code node.type}.
     */
    INSTANCE_TYPE(STRING, false, "node.type"),

    /** Root disk size of the node in GB. Expression name: {@code node.disk}. */
    NODE_DISK(NUMERIC, false, "node.disk"),

    /** Full docker image reference of the run tool. Expression name: {@code docker.image}. */
    DOCKER_IMAGE(STRING, false, "docker.image"),

    /** Numeric identifier of the pipeline. Expression name: {@code pipeline.id}. */
    PIPELINE_ID(NUMERIC, false, "pipeline.id"),

    /** Display name of the pipeline. Expression name: {@code pipeline.name}. */
    PIPELINE_NAME(STRING, false, "pipeline.name"),

    /**
     * Username of the run owner. Supports wildcard patterns.
     * Expression names: {@code run.owner}, {@code owner}.
     */
    OWNER(STRING, false, "run.owner", "owner"),

    /**
     * Whether the run uses a spot (preemptible) instance.
     * Expression names: {@code run.spot}, {@code spot}.
     */
    SPOT(BOOLEAN, false, "run.spot", "spot"),

    /**
     * Numeric identifier of the cloud region.
     * Expression names: {@code run.region_id}, {@code region_id}.
     */
    REGION_ID(NUMERIC, false, "run.region_id", "region_id"),

    /**
     * Run tag key. Supports optional duration gate via
     * {@link ConditionExpression#getDuration()}.
     * Expression names: {@code run.tag}, {@code tag}.
     */
    TAG(TAGS, true, "run.tag", "tag"),

    /**
     * Group or role the run owner belongs to; resolved at monitoring time.
     * Expression names: {@code run.owner.group}, {@code owner.group}.
     */
    OWNER_GROUP(GROUPS, false, "run.owner.group", "owner.group");

    @Getter
    private final FieldType type;

    @Getter
    private final boolean supportsDuration;

    private final String[] displayNames;

    PipelineRunField(final FieldType type, final boolean supportsDuration, final String... displayNames) {
        this.type = type;
        this.supportsDuration = supportsDuration;
        this.displayNames = displayNames;
    }

    /** All expression names that resolve to this field. */
    public List<String> getDisplayNames() {
        return Arrays.asList(displayNames);
    }

    private static final Map<String, PipelineRunField> BY_DISPLAY_NAME;

    static {
        final Map<String, PipelineRunField> map = new HashMap<>();
        for (final PipelineRunField f : values()) {
            for (final String name : f.getDisplayNames()) {
                map.put(name, f);
            }
        }
        BY_DISPLAY_NAME = Collections.unmodifiableMap(map);
    }

    public static Optional<PipelineRunField> findByDisplayName(final String name) {
        return Optional.ofNullable(BY_DISPLAY_NAME.get(name));
    }
}
