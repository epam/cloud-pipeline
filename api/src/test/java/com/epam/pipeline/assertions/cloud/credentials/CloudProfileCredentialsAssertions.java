/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.assertions.cloud.credentials;

import com.epam.pipeline.dto.cloud.credentials.aws.AWSProfileCredentials;
import com.epam.pipeline.entity.cloud.credentials.aws.AWSProfileCredentialsEntity;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public final class CloudProfileCredentialsAssertions {

    private CloudProfileCredentialsAssertions() {
        // no-op
    }

    public static void assertEquals(final AWSProfileCredentialsEntity first, final AWSProfileCredentialsEntity second) {
        if (first == null && second == null) {
            return;
        }
        assertThat(first.getId(), is(second.getId()));
        assertThat(first.getAssumedRole(), is(second.getAssumedRole()));
        assertThat(first.getPolicy(), is(second.getPolicy()));
        assertThat(first.getProfileName(), is(second.getProfileName()));
        assertThat(first.getCloudProvider(), is(second.getCloudProvider()));
    }

    public static void assertEquals(final AWSProfileCredentials first, final AWSProfileCredentials second) {
        if (first == null && second == null) {
            return;
        }
        assertThat(first.getId(), is(second.getId()));
        assertThat(first.getAssumedRole(), is(second.getAssumedRole()));
        assertThat(first.getPolicy(), is(second.getPolicy()));
        assertThat(first.getProfileName(), is(second.getProfileName()));
        assertThat(first.getCloudProvider(), is(second.getCloudProvider()));
    }
}
