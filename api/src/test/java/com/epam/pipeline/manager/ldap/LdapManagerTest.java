package com.epam.pipeline.manager.ldap;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.ldap.LdapEntity;
import com.epam.pipeline.entity.ldap.LdapEntityType;
import com.epam.pipeline.entity.ldap.LdapSearchRequest;
import com.epam.pipeline.entity.ldap.LdapSearchResponse;
import com.epam.pipeline.entity.ldap.LdapSearchResponseType;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.springframework.ldap.TimeLimitExceededException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LdapManagerTest {

    private static final String NAME = "NAME";
    private static final String QUERY = "QUERY";
    private static final int COUNT_LIMIT = 10;
    private static final int TIME_LIMIT = 1000;
    private static final String BASE_PATH = "dc=epam,dc=com";
    private static final String[] ATTRIBUTES = {"cn", "mail", "attribute"};
    private static final String USER_FILTER = "(&(objectClass=person)(attribute=*%s*))";
    private static final String GROUP_FILTER = "(&(objectClass=group)(attribute=*%s*))";
    public static final LdapEntity ENTITY = new LdapEntity(NAME, LdapEntityType.USER, Collections.emptyMap());

    private final LdapTemplate ldapTemplate = mock(LdapTemplate.class);
    private final LdapEntityMapper ldapEntityMapper = mock(LdapEntityMapper.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final LdapManager ldapManager = new LdapManager(ldapTemplate, ldapEntityMapper, preferenceManager, 
            messageHelper);

    @Before
    public void setUp() {
        mockEmptyPreferences();
        mockPreference(SystemPreferences.LDAP_RESPONSE_SIZE, COUNT_LIMIT);
        mockPreference(SystemPreferences.LDAP_BASE_PATH, BASE_PATH);
        mockPreference(SystemPreferences.LDAP_USER_FILTER, USER_FILTER);
        mockPreference(SystemPreferences.LDAP_GROUP_FILTER, GROUP_FILTER);
        mockPreference(SystemPreferences.LDAP_RESPONSE_SIZE, COUNT_LIMIT);
        mockPreference(SystemPreferences.LDAP_RESPONSE_TIMEOUT, TIME_LIMIT);
        mockPreference(SystemPreferences.LDAP_ENTITY_ATTRIBUTES, String.join(",", ATTRIBUTES));
        mockEntities(Collections.emptyList());
    }

    @Test
    public void testThatSearchRetrievesCompleteEntitiesListFromLdap() {
        mockEntities(Collections.nCopies(COUNT_LIMIT / 2, ENTITY));

        final LdapSearchResponse response = ldapManager.search(LdapSearchRequest.forUser(QUERY));
        
        assertThat(response.getType(), is(LdapSearchResponseType.COMPLETED));
        assertThat(response.getEntities().size(), is(COUNT_LIMIT / 2));
    }

    @Test
    public void testThatSearchRetrievesTruncatedEntitiesListFromLdap() {
        mockEntities(Collections.nCopies(COUNT_LIMIT * 2, ENTITY));

        final LdapSearchResponse response = ldapManager.search(LdapSearchRequest.forUser(QUERY));
        
        assertThat(response.getType(), is(LdapSearchResponseType.TRUNCATED));
        assertThat(response.getEntities().size(), is(COUNT_LIMIT * 2));
    }

    @Test
    public void testThatSearchHandlesTimedOutEntitiesRequests() {
        mockEntitiesRetrievalTimeout();
        
        final LdapSearchResponse response = ldapManager.search(LdapSearchRequest.forUser(QUERY));
        
        assertThat(response.getType(), is(LdapSearchResponseType.TIMED_OUT));
        assertThat(response.getEntities().size(), is(0));
    }

    @Test
    public void testThatSearchUsesBasePathFromPreferences() {
        ldapManager.search(LdapSearchRequest.forUser(QUERY));

        verifyQuery(query -> query.base().toString().equals(BASE_PATH));
    }

    @Test
    public void testThatSearchUsesUserFilterFromPreferences() {
        ldapManager.search(LdapSearchRequest.forUser(QUERY));

        verifyQuery(query -> query.filter().toString().equals(String.format(USER_FILTER, QUERY)));
    }

    @Test
    public void testThatSearchUsesGroupFilterFromPreferences() {
        ldapManager.search(LdapSearchRequest.forGroup(QUERY));

        verifyQuery(query -> query.filter().toString().equals(String.format(GROUP_FILTER, QUERY)));
    }

    @Test
    public void testThatSearchUsesSearchLimitsFromPreferences() {
        ldapManager.search(LdapSearchRequest.forUser(QUERY));

        verifyQuery(query -> query.countLimit().equals(COUNT_LIMIT));
        verifyQuery(query -> query.timeLimit().equals(TIME_LIMIT));
    }

    @Test
    public void testThatSearchUsesAttributesFromPreferences() {
        ldapManager.search(LdapSearchRequest.forUser(QUERY));

        verifyQuery(query -> Arrays.equals(query.attributes(), ATTRIBUTES));
    }

    private void mockEmptyPreferences() {
        doReturn(Optional.empty()).when(preferenceManager).findPreference(any());
    }

    private <T> void mockPreference(final AbstractSystemPreference<T> preference, final T value) {
        doReturn(Optional.of(value)).when(preferenceManager).findPreference(eq(preference));
    }

    @SuppressWarnings("unchecked")
    private void mockEntities(final List<LdapEntity> entities) {
        doReturn(entities).when(ldapTemplate).search(any(LdapQuery.class), any(AttributesMapper.class));
    }
    
    @SuppressWarnings("unchecked")
    private void mockEntitiesRetrievalTimeout() {
        doThrow(TimeLimitExceededException.class)
                .when(ldapTemplate).search(any(LdapQuery.class), any(AttributesMapper.class));
    }

    @SuppressWarnings("unchecked")
    private void verifyQuery(final Predicate<LdapQuery> predicate) {
        verify(ldapTemplate).search(argThat(matches(predicate)), any(AttributesMapper.class));
    }

    private <T> BaseMatcher<T> matches(final Predicate<T> predicate) {
        return new BaseMatcher<T>() { 
            @Override
            public boolean matches(final Object item) {
                return predicate.test((T) item);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("Query doesn't match the expected one.");
            }
        };
    }
}
