/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.cluster.pool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NodePoolJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldSerializeTheReservationFlagOnceAsIsCapacityReservation() {
        final NodePool pool = new NodePool();
        pool.setCapacityReservation(true);

        final JsonNode json = mapper.valueToTree(pool);

        assertTrue(json.get("isCapacityReservation").asBoolean());
        assertFalse(json.has("capacityReservation"));
    }
}
