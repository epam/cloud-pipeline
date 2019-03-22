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

package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.epam.pipeline.config.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class for building S3 bucket policy string from provided String template.
 * Currently supports only postprocessing of {@code [ALLOWED_CIDRS]} and
 * [BUCKET_NAME] placeholders.
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class S3PolicyBuilder {

    private static final String DEFAULT_IP_MASK = "0.0.0.0/0";
    private static final String ALLOWED_CIDRS_PLACEHOLDER = "[ALLOWED_CIDRS]";
    private static final String BUCKET_NAME_TOKEN = "\\[BUCKET_NAME]";

    private String bucketName;
    @Builder.Default
    private boolean shared = false;
    @Builder.Default
    private List<String> allowedCidrs = new ArrayList<>();
    @Builder.Default
    private JsonMapper jsonMapper = new JsonMapper();

    public String buildPolicy(final String policy) throws IOException {
        final JsonNode jsonNode = jsonMapper.readTree(policy);
        traverseNode(jsonNode);
        return jsonMapper.writeValueAsString(jsonNode).replaceAll(BUCKET_NAME_TOKEN, bucketName);
    }

    private void traverseNode(final JsonNode node) {
        if (node.isArray()) {
            processArrayNode(node);
        }
        for (final JsonNode child : node) {
            traverseNode(child);
        }
    }

    private void processArrayNode(final JsonNode node) {
        final ArrayNode arrayNode = (ArrayNode)node;
        final Iterator<JsonNode> elements = node.elements();
        final int index = findAllowedCidrIndex(elements);
        if (index >= 0) {
            arrayNode.remove(index);
            if (shared) {
                if (CollectionUtils.isEmpty(allowedCidrs)) {
                    arrayNode.add(DEFAULT_IP_MASK);
                } else {
                    allowedCidrs.forEach(arrayNode::add);
                }
            }
        }
    }

    private int findAllowedCidrIndex(final Iterator<JsonNode> elements) {
        int index = 0;
        while (elements.hasNext()) {
            JsonNode next = elements.next();
            if (next.isTextual() && ALLOWED_CIDRS_PLACEHOLDER.equals(next.asText())) {
                return index;
            }
            index++;
        }
        return -1;
    }
}
