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

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AzurePolicy;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.isEmpty;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("PMD.TooManyStaticImports")
@Transactional
public class CloudRegionDaoTest extends AbstractSpringTest {

    private static final String STORAGE_ACCOUNT_KEY = "storageAccountKey";
    private static final AzureRegionCredentials AZURE_CREDENTIALS = new AzureRegionCredentials(STORAGE_ACCOUNT_KEY);

    @Autowired
    private CloudRegionDao cloudRegionDao;

    @Test
    public void loadAllShouldReturnEmptyListIfThereAreNoRegions() {
        assertThat(cloudRegionDao.loadAll(), isEmpty());
    }

    @Test
    public void loadAllShouldReturnListWithAllCreatedEntities() {
        final AbstractCloudRegion firstRegion = cloudRegionDao.create(getAwsRegion());
        final AbstractCloudRegion secondRegion = cloudRegionDao.create(getAwsRegion());

        final List<? extends AbstractCloudRegion> actualAwsRegions = cloudRegionDao.loadAll();

        assertThat(actualAwsRegions, containsInAnyOrder(firstRegion, secondRegion));
    }

    @Test
    public void loadByIdShouldReturnEmptyOptionalIfThereIsNoRegionWithSpecifiedId() {
        assertFalse(cloudRegionDao.loadById(-1L).isPresent());
    }

    @Test
    public void loadByIdShouldReturnEntityWithTheGivenId() {
        final AbstractCloudRegion expectedRegion = cloudRegionDao.create(getAwsRegion());

        final Optional<AbstractCloudRegion> actualRegionWrapper = cloudRegionDao.loadById(expectedRegion.getId());

        assertTrue(actualRegionWrapper.isPresent());
        final AbstractCloudRegion actualRegion = actualRegionWrapper.get();
        assertThat(actualRegion.getId(), is(expectedRegion.getId()));
        assertRegionEquals(expectedRegion, actualRegion);
    }

    @Test
    public void loadByNameShouldReturnEmptyOptionalIfThereIsNoRegionWithSpecifiedName() {
        assertFalse(cloudRegionDao.loadByName("unexistingRegion").isPresent());
    }

    @Test
    public void loadByRegionNameShouldReturnEmptyOptionalIfThereIsNoRegionWithSpecifiedName() {
        assertFalse(cloudRegionDao.loadByRegionName("unexistingRegionName").isPresent());
    }

    @Test
    public void loadByNameShouldReturnEntityWithTheGivenName() {
        final AbstractCloudRegion expectedRegion = cloudRegionDao.create(getAwsRegion());

        final Optional<AbstractCloudRegion> actualRegionWrapper = cloudRegionDao.loadByName(expectedRegion.getName());

        assertTrue(actualRegionWrapper.isPresent());
        final AbstractCloudRegion actualRegion = actualRegionWrapper.get();
        assertThat(actualRegion.getName(), is(expectedRegion.getName()));
        assertRegionEquals(expectedRegion, actualRegion);
    }

    @Test
    public void loadByRegionNameShouldReturnEntityWithTheGivenName() {
        final AbstractCloudRegion expectedRegion = cloudRegionDao.create(getAwsRegion());

        final Optional<AbstractCloudRegion> actualRegionWrapper = cloudRegionDao
                .loadByRegionName(expectedRegion.getRegionCode());

        assertTrue(actualRegionWrapper.isPresent());
        final AbstractCloudRegion actualRegion = actualRegionWrapper.get();
        assertThat(actualRegion.getRegionCode(), is(expectedRegion.getRegionCode()));
        assertRegionEquals(expectedRegion, actualRegion);
    }

    @Test
    public void createShouldSaveEntityWithAllSpecifiedParameters() {
        final AwsRegion expectedRegion = getAwsRegion();
        expectedRegion.setCorsRules("corsRules");
        expectedRegion.setPolicy("policy");
        expectedRegion.setKmsKeyId("kmsKeyId");
        expectedRegion.setKmsKeyArn("kmsKeyArn");

        final AbstractCloudRegion createdRegion = cloudRegionDao.create(expectedRegion);

        final Optional<AbstractCloudRegion> retrievedRegionWrapper = cloudRegionDao.loadById(createdRegion.getId());
        assertTrue(retrievedRegionWrapper.isPresent());
        final AbstractCloudRegion actualRegion = retrievedRegionWrapper.get();
        assertRegionEquals(expectedRegion, actualRegion);
    }

    @Test
    public void createShouldReturnEntityWithGeneratedId() {
        final AwsRegion region = getAwsRegion();
        assertNull(region.getId());

        final AbstractCloudRegion createdRegion = cloudRegionDao.create(region);

        assertNotNull(createdRegion.getId());
    }

    @Test
    public void createShouldThrowIfNameIsNotSpecified() {
        final AwsRegion regionWithoutName = getAwsRegion();
        regionWithoutName.setName(null);
        assertThrows(DataIntegrityViolationException.class,
            () -> cloudRegionDao.create(regionWithoutName));
    }

    @Test
    public void createShouldThrowIfRegionIdIsNotSpecified() {
        final AwsRegion regionWithoutRegionId = getAwsRegion();
        regionWithoutRegionId.setRegionCode(null);
        assertThrows(DataIntegrityViolationException.class,
            () -> cloudRegionDao.create(regionWithoutRegionId));
    }

    @Test
    public void deleteShouldRemoveEntityIfItExists() {
        final AwsRegion region = getAwsRegion();
        final AbstractCloudRegion createdRegion = cloudRegionDao.create(region);

        cloudRegionDao.delete(createdRegion.getId());

        assertFalse(cloudRegionDao.loadById(createdRegion.getId()).isPresent());
    }

    @Test
    public void updateShouldReplaceAllAwsEntityFields() {
        final AwsRegion originRegion = getAwsRegion();
        originRegion.setCorsRules("corsRules");
        originRegion.setPolicy("policy");
        originRegion.setKmsKeyId("kmsKeyId");
        originRegion.setKmsKeyArn("kmsKeyArn");
        originRegion.setDefault(false);
        final AbstractCloudRegion savedRegion = cloudRegionDao.create(originRegion);
        final AwsRegion updatedRegion = getAwsRegion();
        updatedRegion.setId(savedRegion.getId());
        updatedRegion.setCorsRules("updatedCorsRules");
        updatedRegion.setPolicy("updatedPolicy");
        updatedRegion.setKmsKeyId("updatedKmsKeyId");
        updatedRegion.setKmsKeyArn("updatedKmsKeyArn");
        updatedRegion.setDefault(true);

        cloudRegionDao.update(updatedRegion, null);

        final Optional<AbstractCloudRegion> actualRegionWrapper = cloudRegionDao.loadById(updatedRegion.getId());

        assertTrue(actualRegionWrapper.isPresent());
        final AbstractCloudRegion actualRegion = actualRegionWrapper.get();
        assertRegionEquals(updatedRegion, actualRegion);
    }

    @Test
    public void updateShouldReplaceAllAzureEntityFields() {
        final AzureRegion originRegion = getAzureRegion();
        originRegion.setCorsRules("corsRules");
        originRegion.setAuthFile("authFile");
        originRegion.setAzurePolicy(new AzurePolicy("ipMin", "ipMax"));
        originRegion.setDefault(false);
        final AbstractCloudRegion savedRegion = cloudRegionDao.create(originRegion, null);
        final AzureRegion updatedRegion = getAzureRegion();
        updatedRegion.setId(savedRegion.getId());
        updatedRegion.setCorsRules("updatedCorsRules");
        updatedRegion.setAuthFile("updatedAuthFile");
        updatedRegion.setAzurePolicy(new AzurePolicy("updatedIpMin", "updatedIpMax"));
        updatedRegion.setDefault(true);

        cloudRegionDao.update(updatedRegion, null);

        final Optional<AbstractCloudRegion> actualRegionWrapper = cloudRegionDao.loadById(updatedRegion.getId());

        assertTrue(actualRegionWrapper.isPresent());
        final AbstractCloudRegion actualRegion = actualRegionWrapper.get();
        assertRegionEquals(updatedRegion, actualRegion);
    }

    @Test
    public void loadCredentialsShouldReturnNoCredentialsIfRegionDoNotExist() {
        assertFalse(cloudRegionDao.loadCredentials(-1L).isPresent());
    }

    @Test
    public void loadCredentialsShouldReturnNoCredentialsIfRegionProviderIsNotAzure() {
        final AbstractCloudRegion region = cloudRegionDao.create(getAwsRegion());

        assertFalse(cloudRegionDao.loadCredentials(region.getId()).isPresent());
    }

    @Test
    public void loadCredentialsShouldReturnAzureCredentials() {
        final AbstractCloudRegion region = cloudRegionDao.create(getAzureRegion(), AZURE_CREDENTIALS);

        final Optional<AzureRegionCredentials> credentials = cloudRegionDao.loadCredentials(region.getId())
                .map(AzureRegionCredentials.class::cast);

        assertTrue(credentials.isPresent());
        assertThat(credentials.get().getStorageAccountKey(), is(STORAGE_ACCOUNT_KEY));
    }

    private AwsRegion getAwsRegion() {
        return withDefaults(new AwsRegion());
    }

    private AzureRegion getAzureRegion() {
        return withDefaults(new AzureRegion());
    }

    private <REGION extends AbstractCloudRegion> REGION withDefaults(final REGION region) {
        region.setName("name-" + RandomUtils.nextInt());
        region.setRegionCode("regionId-" + RandomUtils.nextInt());
        region.setOwner("test-admin");
        region.setCreatedDate(new Date());
        return region;
    }

    /**
     * Ignores {@link AwsRegion#getId()} field.
     */
    private void assertRegionEquals(final AbstractCloudRegion expectedRegion, final AbstractCloudRegion actualRegion) {
        assertThat(expectedRegion.getRegionCode(), is(actualRegion.getRegionCode()));
        assertThat(expectedRegion.getName(), is(actualRegion.getName()));
        assertThat(expectedRegion.getOwner(), is(actualRegion.getOwner()));
        assertThat(expectedRegion.getCreatedDate(), is(actualRegion.getCreatedDate()));
        assertThat(actualRegion, instanceOf(expectedRegion.getClass()));
    }
}
