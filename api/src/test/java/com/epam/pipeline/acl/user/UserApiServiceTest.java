/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.user;

import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.manager.user.UserApiService;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ContextConfiguration(classes = UserApiService.class)
public class UserApiServiceTest extends AbstractAclTest {

    private final JwtRawToken token = new JwtRawToken(TEST_STRING);

    @Autowired
    private UserApiService userApiService;

    /**
     * The one hop where the renewal flag becomes a refusal. Without a test here, inverting the
     * condition would flip the whole feature — every ordinary login refused a token and every
     * token from the exchange free to renew — and nothing else would notice.
     */
    @Test
    @WithMockUser
    public void shouldIssueATokenForCurrentUserWhenTheTokenIsRenewable() {
        doReturn(context(false)).when(mockAuthManager).getUserContext();
        doReturn(token).when(mockAuthManager).issueTokenForCurrentUser(ID);

        assertThat(userApiService.issueTokenForCurrentUser(ID)).isEqualTo(token);
    }

    @Test
    @WithMockUser
    public void shouldRefuseToIssueATokenForCurrentUserWhenTheTokenIsNonRenewable() {
        doReturn(context(true)).when(mockAuthManager).getUserContext();

        assertThrows(AccessDeniedException.class, () -> userApiService.issueTokenForCurrentUser(ID));
        verify(mockAuthManager, never()).issueTokenForCurrentUser(ID);
    }

    /**
     * An ordinary login carries no such context at all, and must not be refused.
     */
    @Test
    @WithMockUser
    public void shouldIssueATokenWhenThereIsNoContextToAsk() {
        doReturn(null).when(mockAuthManager).getUserContext();
        doReturn(token).when(mockAuthManager).issueTokenForCurrentUser(ID);

        assertThat(userApiService.issueTokenForCurrentUser(ID)).isEqualTo(token);
    }

    private static UserContext context(final boolean nonRenewable) {
        final UserContext context = new UserContext(ID, TEST_STRING);
        context.setNonRenewable(nonRenewable);
        return context;
    }
}
