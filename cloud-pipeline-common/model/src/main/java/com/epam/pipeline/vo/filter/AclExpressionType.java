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

package com.epam.pipeline.vo.filter;

import java.util.HashMap;
import java.util.Map;

public enum AclExpressionType {
    NONE("none"),
    PIPELINE_IDS("pipeline_ids"),
    OWNERSHIP("ownership");

    private String aclType;

    private static Map<String, AclExpressionType> operandsMap = new HashMap<>();
    static {
        operandsMap.put(NONE.aclType, NONE);
        operandsMap.put(PIPELINE_IDS.aclType, PIPELINE_IDS);
        operandsMap.put(OWNERSHIP.aclType, OWNERSHIP);
    }

    AclExpressionType(String type) {
        this.aclType = type;
    }

    public static AclExpressionType getByString(String type) {
        if (type == null) {
            return null;
        }
        return operandsMap.get(type);
    }


    public String getAclType() {
        return this.aclType;
    }
}
