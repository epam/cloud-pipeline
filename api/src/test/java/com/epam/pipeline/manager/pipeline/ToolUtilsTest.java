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

package com.epam.pipeline.manager.pipeline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ToolUtilsTest {

    private static final String DIGEST = "sha256:6c3c624b58dbbcd3c0dd82b4c53f04194d1247c6eebdaab7c610cf7d66709b3b";

    @Test
    public void shouldReplaceImageTagWithDigest() {
        assertEquals("registry/group/tool@" + DIGEST,
                ToolUtils.getImageWithDigest("registry/group/tool:latest", DIGEST));
    }

    @Test
    public void shouldNotMistakeRegistryPortForImageTag() {
        assertEquals("registry:443/group/tool@" + DIGEST,
                ToolUtils.getImageWithDigest("registry:443/group/tool:latest", DIGEST));
    }

    @Test
    public void shouldAddDigestToImageWithoutTag() {
        assertEquals("registry:443/group/tool@" + DIGEST,
                ToolUtils.getImageWithDigest("registry:443/group/tool", DIGEST));
    }
}
