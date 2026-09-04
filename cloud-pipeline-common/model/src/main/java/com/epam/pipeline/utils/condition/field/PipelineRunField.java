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

package com.epam.pipeline.utils.condition.field;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.utils.condition.FieldType.BOOLEAN;
import static com.epam.pipeline.utils.condition.FieldType.ENUM;
import static com.epam.pipeline.utils.condition.FieldType.KEY_VALUE;
import static com.epam.pipeline.utils.condition.FieldType.NUMERIC;
import static com.epam.pipeline.utils.condition.FieldType.STRING;
import static com.epam.pipeline.utils.condition.FieldType.TAGS;

/**
 * Filterable fields available in compute quota rule expressions.
 *
 * Fields that require runtime context from the monitoring service (e.g. owner.group,
 * which needs user-group data loaded from the Cloud Pipeline API) are intentionally
 * absent here and handled by the evaluator in the monitoring service directly.
 */
public enum PipelineRunField implements SubjectEntityField<PipelineRun> {

    /** Numeric run identifier. Expression names: {@code run.id}, {@code id}. */
    RUN_ID(NUMERIC,
        run -> str(run.getId()),
            false,
            "run.id", "id"),

    /**
     * Current run status as an enum name (e.g. {@code RUNNING}, {@code STOPPED}).
     * Comparison is case-insensitive. Expression names: {@code run.status}, {@code status}.
     */
    STATUS(ENUM,
        run -> run.getStatus() != null ? run.getStatus().name() : null,
            false,
            "run.status", "status"),

    /**
     * Node instance type (e.g. {@code m5.xlarge}). Supports wildcard patterns such as {@code m5.*}.
     * Expression name: {@code node.type}.
     */
    INSTANCE_TYPE(STRING,
        run -> instance(run) != null ? instance(run).getNodeType() : null,
            false,
            "node.type"),

    /** Root disk size of the node in GB. Expression name: {@code node.disk}. */
    NODE_DISK(NUMERIC,
        run -> instance(run) != null ? str(instance(run).getNodeDisk()) : null,
            false,
            "node.disk"),

    /** Full docker image reference of the run tool. Expression name: {@code docker.image}. */
    DOCKER_IMAGE(STRING,
            PipelineRun::getDockerImage,
            false,
            "docker.image"),

    /** Numeric identifier of the pipeline the run belongs to. Expression name: {@code pipeline.id}. */
    PIPELINE_ID(NUMERIC,
        run -> str(run.getPipelineId()),
            false,
            "pipeline.id"),

    /** Display name of the pipeline the run belongs to. Expression name: {@code pipeline.name}. */
    PIPELINE_NAME(STRING,
            PipelineRun::getPipelineName,
            false,
            "pipeline.name"),

    /**
     * Username of the run owner. Supports wildcard patterns such as {@code john.*}.
     * Expression names: {@code run.owner}, {@code owner}.
     */
    OWNER(STRING,
            AbstractSecuredEntity::getOwner,
            false,
            "run.owner", "owner"),

    /**
     * Whether the run is using a spot (preemptible) instance.
     * Accepts {@code true} or {@code false}. Expression names: {@code run.spot}, {@code spot}.
     */
    SPOT(BOOLEAN,
        run -> instance(run) != null ? str(instance(run).getSpot()) : null,
            false,
            "run.spot", "spot"),

    /**
     * Numeric identifier of the cloud region the run's node is provisioned in.
     * Expression names: {@code run.region_id}, {@code region_id}.
     */
    REGION_ID(NUMERIC,
        run -> instance(run) != null ? str(instance(run).getCloudRegionId()) : null,
            false,
            "run.region_id", "region_id"),

    /**
     * Matches against the keys of the run's tags map.
     * {@code run.tag = IDLE} is true when the run carries a tag whose key equals {@code IDLE}
     * (comparison is case-insensitive).
     * <p>
     * Supports duration: when {@link ConditionExpression#getDuration()} is set on the leaf node,
     * the companion tag {@code <tagName>_date} (e.g. {@code IDLE_date}) is read to determine
     * how long the tag has been continuously present, and the leaf only matches if that elapsed
     * time meets or exceeds the required duration in hours.
     * <p>
     * Expression names: {@code run.tag}, {@code tag}.
     */
    TAG(TAGS,
        null,
        run -> MapUtils.emptyIfNull(run.getTags()),
        true,
        "run.tag", "tag"),

    /**
     * Matches against the set of groups and roles the run owner belongs to.
     * Evaluated by the monitoring service using cached user-group data.
     * Expression names: {@code run.owner.group}, {@code owner.group}.
     */
    OWNER_AUTHORITIES(FieldType.USER_AUTHORITIES,
            PipelineRun::getOwner,
            false,
            "run.owner.authorities", "owner.authorities"),

    /**
     * Matches against the run's pipeline parameters.
     * Expression value is {@code <name>} (parameter present) or {@code <name>=<valuePattern>}
     * (parameter present with value matching a wildcard pattern).
     * Comparison is case-insensitive. Expression names: {@code run.parameter}, {@code parameter}.
     */
    PARAMETER(KEY_VALUE,
            null,
            PipelineRunField::parametersToMap,
            false,
            "run.parameter", "parameter"),

    /**
     * Matches against the run's environment variables.
     * Expression value is {@code <name>} (variable present) or {@code <name>=<valuePattern>}
     * (variable present with value matching a wildcard pattern).
     * Comparison is case-insensitive. Expression names: {@code run.env_var}, {@code env_var}.
     */
    ENV_VAR(KEY_VALUE,
            null,
            PipelineRunField::envVarsToMap,
            false,
            "run.env_var", "env_var");

    /** Value type that governs which operators are valid and how comparisons are performed. */
    @Getter
    private final FieldType type;

    /** Extracts the field's string representation from a run for comparison against the rule value. */
    private final Function<PipelineRun, String> extractor;

    /** Extracts the key-value map from a run; non-null only for {@link FieldType#KEY_VALUE} fields. */
    private final Function<PipelineRun, Map<String, String>> mapExtractor;

    /**
     * Whether this field supports the duration gate on a filter expression leaf.
     * When {@code true}, a non-null {@link ConditionExpression#getDuration()} triggers a check against
     * the companion {@code <tagName>_date} tag instead of a plain boolean match.
     * Currently only {@link #TAG} returns {@code true}.
     */
    @Getter
    private final boolean supportsDuration;

    /** Expression names that reference this field in a rule expression, e.g. {@code run.id}, {@code id}. */
    private final String[] displayNames;

    PipelineRunField(final FieldType type,
                     final Function<PipelineRun, String> extractor,
                     final boolean supportsDuration,
                     final String... displayNames) {
        this(type, extractor, null, supportsDuration, displayNames);
    }

    PipelineRunField(final FieldType type,
                     final Function<PipelineRun, String> extractor,
                     final Function<PipelineRun, Map<String, String>> mapExtractor,
                     final boolean supportsDuration,
                     final String... displayNames) {
        this.type = type;
        this.extractor = extractor;
        this.mapExtractor = mapExtractor;
        this.supportsDuration = supportsDuration;
        this.displayNames = displayNames;
    }

    public String extract(final PipelineRun run) {
        return extractor != null ? extractor.apply(run) : null;
    }

    @Override
    public Map<String, String> extractMap(final PipelineRun run) {
        return mapExtractor != null ? mapExtractor.apply(run) : Collections.emptyMap();
    }

    private static Map<String, String> parametersToMap(final PipelineRun run) {
        final List<PipelineRunParameter> params = run.getPipelineRunParameters();
        if (params == null) {
            return Collections.emptyMap();
        }
        return params.stream()
                .filter(p -> p.getName() != null)
                .collect(Collectors.toMap(
                        PipelineRunParameter::getName,
                    p -> p.getValue() != null ? p.getValue() : "",
                    (a, b) -> a));
    }

    private static Map<String, String> envVarsToMap(final PipelineRun run) {
        final Map<String, String> envVars = run.getEnvVars();
        return envVars != null ? envVars : Collections.emptyMap();
    }

    /** All expression names that resolve to this field. */
    public List<String> getDisplayNames() {
        return Arrays.asList(displayNames);
    }

    private static final Map<String, PipelineRunField> BY_DISPLAY_NAME;

    static {
        final Map<String, PipelineRunField> map = new HashMap<>();
        for (final PipelineRunField f : values()) {
            for (final String name : f.displayNames) {
                map.put(name, f);
            }
        }
        BY_DISPLAY_NAME = Collections.unmodifiableMap(map);
    }

    /**
     * Returns the field for the given display name, or empty if not found.
     * Returns Optional rather than throwing so callers can check for monitoring-service
     * specific fields (like owner.group) before deciding how to proceed.
     */
    public static Optional<PipelineRunField> findByDisplayName(final String name) {
        return Optional.ofNullable(BY_DISPLAY_NAME.get(name));
    }

    private static String str(final Object value) {
        return value != null ? value.toString() : null;
    }

    private static RunInstance instance(final PipelineRun run) {
        return run.getInstance();
    }
}
