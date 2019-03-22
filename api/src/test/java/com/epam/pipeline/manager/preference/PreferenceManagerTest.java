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

package com.epam.pipeline.manager.preference;

import java.util.Collections;
import java.util.Map;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.preference.PreferenceType;
import io.reactivex.Observable;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SuppressWarnings("unchecked")
public class PreferenceManagerTest extends AbstractSpringTest {

    private static final String GROUP = "group";
    private static final String NAME = "pref";
    private static final String VALUE = "string";
    private static final String NEW_VALUE = "new_value";
    private static final String OBJECT = "{" +
        "\"name\" : \"first\"," +
        "\"object\": {\"param1\":1, \"param2\":2}" +
        "}";

    @Autowired
    private PreferenceManager preferenceManager;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void storeAndLoadTest() {
        Preference preference = new Preference(
                NAME, VALUE, GROUP,
                 "", PreferenceType.STRING, true);
        preferenceManager.update(Collections.singletonList(preference));
        String load = preferenceManager.getStringPreference(NAME);
        Assert.assertEquals(VALUE, load);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void castTest() {
        Preference preference = new Preference(
                NAME, "1", GROUP,
                "", PreferenceType.INTEGER, true);
        preferenceManager.update(Collections.singletonList(preference));
        Integer load = preferenceManager.getIntPreference(NAME);
        Assert.assertEquals(1, load.intValue());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void castToObjectTest() {
        Preference preference = new Preference(
                NAME, OBJECT, GROUP,
                "", PreferenceType.OBJECT, true);
        preferenceManager.update(Collections.singletonList(preference));
        Map<String, Object> load = preferenceManager.getObjectPreference(NAME);
        Assert.assertEquals("first", load.get("name"));
        Assert.assertEquals(1, ((Map<String, Object>)load.get("object")).get("param1"));
        Assert.assertEquals(2, ((Map<String, Object>)load.get("object")).get("param2"));

    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void castExceptionTest() {
        Preference preference = new Preference(
                NAME, "1", GROUP,
                "", PreferenceType.STRING, true);
        preferenceManager.update(Collections.singletonList(preference));
        preferenceManager.getIntPreference(NAME);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void updateTest() {
        Preference preference = new Preference(
                NAME, VALUE, GROUP,
                "", PreferenceType.STRING, true);
        preferenceManager.update(Collections.singletonList(preference));
        String load = preferenceManager.getStringPreference(NAME);

        Assert.assertEquals(VALUE, load);

        preference.setValue(NEW_VALUE);
        preferenceManager.update(Collections.singletonList(preference));
        load = preferenceManager.getStringPreference(NAME);

        Assert.assertEquals(NEW_VALUE, load);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void deleteTest() {
        Preference preference = new Preference(
                NAME, VALUE, GROUP,
                "", PreferenceType.STRING, true);
        preferenceManager.update(Collections.singletonList(preference));
        String load = preferenceManager.getStringPreference(NAME);

        Assert.assertEquals(VALUE, load);

        preferenceManager.delete(NAME);

        Assert.assertNull(preferenceManager.getStringPreference(NAME));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void fetchTest() {
        Preference preference = new Preference(
                NAME, VALUE, GROUP,
                "", PreferenceType.STRING, true);
        preferenceManager.update(Collections.singletonList(preference));

        Preference load = preferenceManager.load(NAME).get();
        String fetch = preferenceManager.getStringPreference(NAME);

        Assert.assertEquals(fetch, load.getValue());

        preference.setValue(NEW_VALUE);
        preferenceManager.update(Collections.singletonList(preference));

        load = preferenceManager.load(NAME).get();
        fetch = preferenceManager.getStringPreference(NAME);

        Assert.assertEquals(fetch, load.getValue());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testGetObservablePreference() {
        Observable<Integer> commitTimeoutObservable = preferenceManager.getObservablePreference(
            SystemPreferences.COMMIT_TIMEOUT);

        TestSubscriber<Integer> test1 = new TestSubscriber<>();
        TestSubscriber<Integer> test2 = new TestSubscriber<>();
        commitTimeoutObservable.subscribe(test1::onNext);
        commitTimeoutObservable.subscribe(test2::onNext);

        Preference commitTimeout = SystemPreferences.COMMIT_TIMEOUT.toPreference();
        commitTimeout.setValue("1111");
        preferenceManager.update(Collections.singletonList(commitTimeout));

        test1.assertValueCount(1);
        test2.assertValueCount(1);

        preferenceManager.delete(commitTimeout.getName());
        test1.assertValueCount(2);
        test2.assertValueCount(2);
    }
}