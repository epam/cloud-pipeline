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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.acls.model.Permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.epam.pipeline.security.acl.AclPermission;
import lombok.Getter;
import lombok.Setter;

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

    public static List<DockerRegistryClaim> parseClaims(String scope) {
        if (!scope.contains(REPOSITORY_PREFIX)) {
            return Collections.singletonList(new DockerRegistryClaim(scope));
        }
        int prefixIndex = scope.indexOf(REPOSITORY_PREFIX);
        List<DockerRegistryClaim> result = new ArrayList<>();
        while (prefixIndex != -1) {
            int nextIndex = scope.indexOf(REPOSITORY_PREFIX, prefixIndex + 1);
            if (nextIndex == -1) {
                nextIndex = scope.length();
            }
            String chunk = scope.substring(prefixIndex, nextIndex);
            if (chunk.endsWith(ACTION_DELIMITER)) {
                chunk = scope.substring(prefixIndex, nextIndex - 1);
            }
            result.add(new DockerRegistryClaim(chunk));
            prefixIndex  = scope.indexOf(REPOSITORY_PREFIX, prefixIndex + 1);
        }
        return result;
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
