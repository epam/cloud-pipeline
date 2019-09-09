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

package com.epam.pipeline.manager.git;

import com.epam.pipeline.entity.git.GitCredentials;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class GitlabClientTest {

    private static final String URL_WITHOUT_USER = "http://gitlab.com/root/test-token-pipe.git";
    private static final String URL_WITH_USER = "http://root@gitlab.com/root/test-token-pipe.git";
    private static final String URL_WITH_ENV_VARS =
            "http://${GIT_USER}:${GIT_TOKEN}@gitlab.com/root/test-token-pipe.git";
    private static final String TOKEN = "abc";
    private static final String USER = "root";
    private static final long DURATION = 1L;

    @Test
    @Ignore
    public void testBuildCloneCredentialsWithUser() {
        testBuildCloneUrl(USER, URL_WITH_USER);
    }

    @Test
    @Ignore
    public void testBuildCloneCredentialsWithoutUser() {
        testBuildCloneUrl(USER, URL_WITHOUT_USER);
    }

    private void testBuildCloneUrl(String user, String url) {
        GitlabClient client =
                GitlabClient.initializeGitlabClientFromRepositoryAndToken(user, url, TOKEN, null, null, false);
        GitCredentials credentials = client.buildCloneCredentials(true, DURATION);
        Assert.assertNotNull(credentials);
        Assert.assertEquals(USER, credentials.getUserName());
        Assert.assertEquals(TOKEN, credentials.getToken());
        Assert.assertEquals(URL_WITH_ENV_VARS, credentials.getUrl());
    }
}
