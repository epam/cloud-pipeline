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

package com.epam.pipeline.dao.region;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.isEmpty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.region.AwsRegion;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@SuppressWarnings("PMD.TooManyStaticImports")
public class AwsRegionDaoTest extends AbstractSpringTest {

    @Autowired
    private AwsRegionDao awsRegionDao;

    @After
    public void tearDown() {
        awsRegionDao.loadAll().stream()
                .map(AwsRegion::getId)
                .forEach(awsRegionDao::delete);
    }

    @Test
    public void loadAllShouldReturnEmptyListIfThereAreNoRegions() {
        assertThat(awsRegionDao.loadAll(), isEmpty());
    }

    @Test
    public void loadAllShouldReturnListWithAllCreatedEntities() {
        final AwsRegion firstRegion = awsRegionDao.create(getCommonRegion());
        final AwsRegion secondRegion = awsRegionDao.create(getCommonRegion());

        final List<AwsRegion> actualAwsRegions = awsRegionDao.loadAll();

        assertThat(actualAwsRegions, containsInAnyOrder(firstRegion, secondRegion));
    }

    @Test
    public void loadByIdShouldReturnEmptyOptionalIfThereIsNoRegionWithSpecifiedId() {
        assertFalse(awsRegionDao.loadById(-1L).isPresent());
    }

    @Test
    public void loadByIdShouldReturnEntityWithTheGivenId() {
        final AwsRegion expectedRegion = awsRegionDao.create(getCommonRegion());

        final Optional<AwsRegion> actualRegionWrapper = awsRegionDao.loadById(expectedRegion.getId());

        assertTrue(actualRegionWrapper.isPresent());
        final AwsRegion actualRegion = actualRegionWrapper.get();
        assertThat(actualRegion.getId(), is(expectedRegion.getId()));
        assertRegionEquals(expectedRegion, actualRegion);
    }

    @Test
    public void loadByNameShouldReturnEmptyOptionalIfThereIsNoRegionWithSpecifiedName() {
        assertFalse(awsRegionDao.loadByName("unexistingRegion").isPresent());
    }

    @Test
    public void loadByNameShouldReturnEntityWithTheGivenName() {
        final AwsRegion expectedRegion = awsRegionDao.create(getCommonRegion());

        final Optional<AwsRegion> actualRegionWrapper = awsRegionDao.loadByName(expectedRegion.getName());

        assertTrue(actualRegionWrapper.isPresent());
        final AwsRegion actualRegion = actualRegionWrapper.get();
        assertThat(actualRegion.getName(), is(expectedRegion.getName()));
        assertRegionEquals(expectedRegion, actualRegion);
    }

    @Test
    public void createShouldSaveEntityWithAllSpecifiedParameters() {
        final AwsRegion expectedRegion = getCommonRegion();
        expectedRegion.setCorsRules("corsRules");
        expectedRegion.setPolicy("policy");
        expectedRegion.setKmsKeyId("kmsKeyId");
        expectedRegion.setKmsKeyArn("kmsKeyArn");

        final AwsRegion createdRegion = awsRegionDao.create(expectedRegion);

        final Optional<AwsRegion> retrievedRegionWrapper = awsRegionDao.loadById(createdRegion.getId());
        assertTrue(retrievedRegionWrapper.isPresent());
        final AwsRegion actualRegion = retrievedRegionWrapper.get();
        assertRegionEquals(expectedRegion, actualRegion);
    }

    @Test
    public void createShouldReturnEntityWithGeneratedId() {
        final AwsRegion region = getCommonRegion();
        assertNull(region.getId());

        final AwsRegion createdRegion = awsRegionDao.create(region);

        assertNotNull(createdRegion.getId());
    }

    @Test
    public void createShouldThrowIfNameIsNotSpecified() {
        final AwsRegion regionWithoutName = getCommonRegion();
        regionWithoutName.setName(null);
        assertThrows(DataIntegrityViolationException.class,
            () -> awsRegionDao.create(regionWithoutName));
    }

    @Test
    public void createShouldThrowIfRegionIdIsNotSpecified() {
        final AwsRegion regionWithoutRegionId = getCommonRegion();
        regionWithoutRegionId.setAwsRegionName(null);
        assertThrows(DataIntegrityViolationException.class,
            () -> awsRegionDao.create(regionWithoutRegionId));
    }

    @Test
    public void deleteShouldRemoveEntityIfItExists() {
        final AwsRegion region = getCommonRegion();
        final AwsRegion createdRegion = awsRegionDao.create(region);

        awsRegionDao.delete(createdRegion.getId());

        assertFalse(awsRegionDao.loadById(createdRegion.getId()).isPresent());
    }

    @Test
    public void updateShouldReplaceAllEntityFields() {
        final AwsRegion originRegion = getCommonRegion();
        originRegion.setCorsRules("corsRules");
        originRegion.setPolicy("policy");
        originRegion.setKmsKeyId("kmsKeyId");
        originRegion.setKmsKeyArn("kmsKeyArn");
        originRegion.setDefault(false);
        final AwsRegion savedRegion = awsRegionDao.create(originRegion);
        final AwsRegion updatedRegion = getCommonRegion();
        updatedRegion.setId(savedRegion.getId());
        updatedRegion.setCorsRules("updatedCorsRules");
        updatedRegion.setPolicy("updatedPolicy");
        updatedRegion.setKmsKeyId("updatedKmsKeyId");
        updatedRegion.setKmsKeyArn("updatedKmsKeyArn");
        updatedRegion.setDefault(true);

        awsRegionDao.update(updatedRegion);

        final Optional<AwsRegion> actualRegionWrapper = awsRegionDao.loadById(updatedRegion.getId());

        assertTrue(actualRegionWrapper.isPresent());
        final AwsRegion actualRegion = actualRegionWrapper.get();
        assertRegionEquals(updatedRegion, actualRegion);
    }

    private AwsRegion getCommonRegion() {
        final AwsRegion awsRegion = new AwsRegion();
        awsRegion.setName("name-" + RandomUtils.nextInt());
        awsRegion.setAwsRegionName("regionId-" + RandomUtils.nextInt());
        awsRegion.setOwner("test-admin");
        awsRegion.setCreatedDate(new Date());
        return awsRegion;
    }

    /**
     * Ignores {@link AwsRegion#getId()} field.
     */
    private void assertRegionEquals(final AwsRegion expectedRegion, final AwsRegion actualRegion) {
        assertThat(expectedRegion.getAwsRegionName(), is(actualRegion.getAwsRegionName()));
        assertThat(expectedRegion.getName(), is(actualRegion.getName()));
        assertThat(expectedRegion.getPolicy(), is(actualRegion.getPolicy()));
        assertThat(expectedRegion.getKmsKeyId(), is(actualRegion.getKmsKeyId()));
        assertThat(expectedRegion.getKmsKeyArn(), is(actualRegion.getKmsKeyArn()));
        assertThat(expectedRegion.getCorsRules(), is(actualRegion.getCorsRules()));
        assertThat(expectedRegion.getOwner(), is(actualRegion.getOwner()));
        assertThat(expectedRegion.getCreatedDate(), is(actualRegion.getCreatedDate()));
    }
}
