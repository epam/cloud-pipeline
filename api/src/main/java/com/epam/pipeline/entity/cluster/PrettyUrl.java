package com.epam.pipeline.entity.cluster;

import lombok.Value;

/**
 * Pretty url support three formats:
 *
 * 1. If only path is specified
 *      - path = prettypath
 *    Then resulting urls are
 *      - https://cloud.pipeline.egde.domain.com/prettypath
 *      - https://cloud.pipeline.egde.domain.com/ssh/pipeline/prettypath
 *
 * 2. If both domain and path are specified
 *      - path = prettypath
 *      - domain = pretty.domain.com
 *    Then resulting urls are
 *      - https://pretty.domain.com/prettypath
 *      - https://pretty.domain.com/ssh/pipeline/prettypath
 *
 * 3. If only domain is specified
 *      - domain = pretty.domain.com
 *    Then resulting urls are
 *      - https://pretty.domain.com/runpath
 *      - https://pretty.domain.com/ssh/pipeline/runpath
 */
@Value
public class PrettyUrl {
    String path;
    String domain;
}
