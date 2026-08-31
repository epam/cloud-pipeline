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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.security.acl.AclPermission;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.map.MultiKeyMap;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.security.acls.model.Permission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
public class DockerRegistryClaim {

    public static final DockerRegistryClaim LISTING_CLAIM = new DockerRegistryClaim("registry:catalog:*");
    public static final String IMAGE_CLAIM_TEMPLATE = "repository:%s:*";

    private static final String REPOSITORY_PREFIX = "repository:";
    public static final String ACTION_DELIMITER = ",";

    private String type;

    @JsonProperty("name")
    private String imageName;
    private String[] actions;

    public DockerRegistryClaim(String scope) {
        String[] path = scope.split(":", 3);
        if (path.length < 3) {
            throw new IllegalArgumentException("Invalid claim format " + scope);
        }
        type = path[0];
        imageName = path[1];
        actions = path[2].split(ACTION_DELIMITER);
    }

    public static DockerRegistryClaim imageClaim(String image) {
        return new DockerRegistryClaim(String.format(IMAGE_CLAIM_TEMPLATE, image));
    }

    public DockerRegistryClaim(final String type, final String image, final Set<String> actions) {
        this.type = type;
        this.imageName = image;
        this.actions = actions.toArray(new String[0]);
    }

    public static DockerRegistryClaim merge(DockerRegistryClaim first, DockerRegistryClaim second) {
        if (!Objects.equals(first.type, second.type) || !Objects.equals(first.imageName, second.imageName)) {
            throw new IllegalArgumentException(String.format(
                    "You are trying to merge scopes for different docker images: '%s:%s', '%s:%s'",
                    first.type, first.imageName, second.type, second.imageName)
            );
        }
        return new DockerRegistryClaim(
                first.type, first.imageName,
               Arrays.stream((String[]) ArrayUtils.addAll(first.actions, second.actions)).collect(Collectors.toSet())
        );
    }

    public static List<DockerRegistryClaim> parseClaims(String scope) {
        if (!scope.contains(REPOSITORY_PREFIX)) {
            return Collections.singletonList(new DockerRegistryClaim(scope));
        }
        int prefixIndex = scope.indexOf(REPOSITORY_PREFIX);
        final MultiKeyMap result = new MultiKeyMap();
        while (prefixIndex != -1) {
            int nextIndex = scope.indexOf(REPOSITORY_PREFIX, prefixIndex + 1);
            if (nextIndex == -1) {
                nextIndex = scope.length();
            }
            String chunk = scope.substring(prefixIndex, nextIndex);
            if (chunk.endsWith(ACTION_DELIMITER)) {
                chunk = scope.substring(prefixIndex, nextIndex - 1);
            }

            DockerRegistryClaim claim = new DockerRegistryClaim(chunk);
            DockerRegistryClaim prev = (DockerRegistryClaim) result.get(claim.type, claim.imageName);
            if (prev != null) {
                claim = DockerRegistryClaim.merge(prev, claim);
            }

            result.put(claim.type, claim.imageName, claim);
            prefixIndex  = scope.indexOf(REPOSITORY_PREFIX, prefixIndex + 1);
        }
        return new ArrayList<DockerRegistryClaim>(result.values());
    }

    @JsonIgnore
    public List<Permission> getRequestedPermissions() {

        Set<Permission> permissions = new HashSet<>();
        for (String action : actions) {
            if (action.equalsIgnoreCase(DockerRegistryAction.PULL.getAction())) {
                permissions.add(AclPermission.READ);
            } else if (action.equalsIgnoreCase(DockerRegistryAction.PUSH.getAction())) {
                permissions.add(AclPermission.WRITE);
            } else if (action.equalsIgnoreCase(DockerRegistryAction.WILD_CART.getAction())) {
                permissions.add(AclPermission.WRITE);
                permissions.add(AclPermission.READ);
            } else {
                throw new IllegalArgumentException("Unsupported action requested: " + action);
            }
        }
        return new ArrayList<>(permissions);
    }
}
