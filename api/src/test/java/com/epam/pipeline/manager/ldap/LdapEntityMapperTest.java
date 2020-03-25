package com.epam.pipeline.manager.ldap;

import com.epam.pipeline.entity.ldap.LdapEntity;
import com.epam.pipeline.entity.ldap.LdapEntityType;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class LdapEntityMapperTest {

    private static final String NAME_ATTRIBUTE = "cn";
    private static final String NAME = "NAME";
    private static final String SCALAR_ATTRIBUTE = "scalar";
    private static final String SCALAR_ATTRIBUTE_VALUE = "scalar-1";
    private static final String VECTOR_ATTRIBUTE = "vector";
    private static final List<String> VECTOR_ATTRIBUTE_VALUES = Arrays.asList("scalar-11", "scalar-22");

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final LdapEntityMapper mapper = new LdapEntityMapper(preferenceManager);

    @Before
    public void setUp() {
        mockPreference(SystemPreferences.LDAP_NAME_ATTRIBUTE, NAME_ATTRIBUTE);
    }

    @Test
    public void testThatMapConvertsAllEntityAttributes() {
        final BasicAttributes attributes = new BasicAttributes();
        attributes.put(NAME_ATTRIBUTE, NAME);
        attributes.put(SCALAR_ATTRIBUTE, SCALAR_ATTRIBUTE_VALUE);
        final BasicAttribute vectorAttribute = new BasicAttribute(VECTOR_ATTRIBUTE);
        VECTOR_ATTRIBUTE_VALUES.forEach(vectorAttribute::add);
        attributes.put(vectorAttribute);

        final LdapEntity entity = mapper.map(attributes, LdapEntityType.USER);
        
        assertThat(entity.getName(), is(NAME));
        assertThat(entity.getType(), is(LdapEntityType.USER));
        final Map<String, List<String>> attributesMap = entity.getAttributes();
        assertThat(attributesMap.get(NAME_ATTRIBUTE), is(Collections.singletonList(NAME)));
        assertThat(attributesMap.get(SCALAR_ATTRIBUTE), is(Collections.singletonList(SCALAR_ATTRIBUTE_VALUE)));
        assertThat(attributesMap.get(VECTOR_ATTRIBUTE), notNullValue());
        assertThat(attributesMap.get(VECTOR_ATTRIBUTE).size(), is(VECTOR_ATTRIBUTE_VALUES.size()));
        assertThat(attributesMap.get(VECTOR_ATTRIBUTE), contains(VECTOR_ATTRIBUTE_VALUES.stream().toArray()));
    }

    private <T> void mockPreference(final AbstractSystemPreference<T> preference, final T value) {
        doReturn(Optional.of(value)).when(preferenceManager).findPreference(eq(preference));
    }
}
