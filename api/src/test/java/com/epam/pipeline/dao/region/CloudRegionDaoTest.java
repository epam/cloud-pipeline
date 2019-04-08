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
import com.epam.pipeline.entity.region.GCPCustomInstanceType;
import com.epam.pipeline.entity.region.GCPRegion;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
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
    private static final String AUTH_FILE = "authFile";
    private static final String CORS_RULES = "corsRules";
    private static final String UPDATED_CORS_RULES = "updatedCorsRules";
    private static final String UPDATED_AUTH_FILE = "updatedAuthFile";
    private static final String POLICY = "policy";
    private static final String KMS_KEY_ID = "kmsKeyId";
    private static final String KMS_KEY_ARN = "kmsKeyArn";
    private static final String UPDATED_POLICY = "updatedPolicy";
    private static final String UPDATED_KMS_KEY_ID = "updatedKmsKeyId";
    private static final String UPDATED_KMS_KEY_ARN = "updatedKmsKeyArn";
    private static final String SSH_PUBLIC_KEY_PATH = "ssh";
    private static final double RAM = 3.75;
    private static final int CPU = 2;
    private static final int GPU = 1;
    private static final String GPU_TYPE = "K80";

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
        assertRegionEqualsCommon(expectedRegion, actualRegion);
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
        assertRegionEqualsCommon(expectedRegion, actualRegion);
    }

    @Test
    public void loadByRegionNameShouldReturnEntityWithTheGivenName() {
        final AbstractCloudRegion expectedRegion = cloudRegionDao.create(getAwsRegion());
        final Optional<AbstractCloudRegion> actualRegionWrapper = cloudRegionDao
                .loadByRegionName(expectedRegion.getRegionCode());
        assertTrue(actualRegionWrapper.isPresent());
        final AbstractCloudRegion actualRegion = actualRegionWrapper.get();
        assertThat(actualRegion.getRegionCode(), is(expectedRegion.getRegionCode()));
        assertRegionEqualsCommon(expectedRegion, actualRegion);
    }

    @Test
    public void createShouldSaveEntityWithAllSpecifiedParameters() {
        final AwsRegion expectedRegion = getAwsRegion();
        expectedRegion.setCorsRules(CORS_RULES);
        expectedRegion.setPolicy(POLICY);
        expectedRegion.setKmsKeyId(KMS_KEY_ID);
        expectedRegion.setKmsKeyArn(KMS_KEY_ARN);
        final AbstractCloudRegion createdRegion = cloudRegionDao.create(expectedRegion);
        final AwsRegion actualRegion = loadAndCheckType(createdRegion.getId(), AwsRegion.class);
        assertRegionEquals(expectedRegion, actualRegion);
    }

    @Test
    public void createShouldSaveGCPEntityWithAllSpecifiedParameters() {
        final GCPRegion expectedRegion = getGCPRegion();
        expectedRegion.setProject("project");
        expectedRegion.setImpersonatedAccount("acc");
        expectedRegion.setAuthFile(AUTH_FILE);
        expectedRegion.setSshPublicKeyPath(SSH_PUBLIC_KEY_PATH);
        expectedRegion.setApplicationName("App");
        expectedRegion.setCustomInstanceTypes(Arrays.asList(
            GCPCustomInstanceType.cpu(CPU, RAM),
            GCPCustomInstanceType.gpu(CPU, RAM, GPU, GPU_TYPE)
        ));
        final AbstractCloudRegion createdRegion = cloudRegionDao.create(expectedRegion);
        final GCPRegion actualRegion = loadAndCheckType(createdRegion.getId(), GCPRegion.class);
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
        originRegion.setCorsRules(CORS_RULES);
        originRegion.setPolicy(POLICY);
        originRegion.setKmsKeyId(KMS_KEY_ID);
        originRegion.setKmsKeyArn(KMS_KEY_ARN);
        originRegion.setDefault(false);
        final AbstractCloudRegion savedRegion = cloudRegionDao.create(originRegion);
        final AwsRegion updatedRegion = getAwsRegion();
        updatedRegion.setId(savedRegion.getId());
        updatedRegion.setCorsRules(UPDATED_CORS_RULES);
        updatedRegion.setPolicy(UPDATED_POLICY);
        updatedRegion.setKmsKeyId(UPDATED_KMS_KEY_ID);
        updatedRegion.setKmsKeyArn(UPDATED_KMS_KEY_ARN);
        updatedRegion.setDefault(true);

        cloudRegionDao.update(updatedRegion, null);
        final AwsRegion actualRegion = loadAndCheckType(updatedRegion.getId(), AwsRegion.class);
        assertRegionEquals(updatedRegion, actualRegion);
    }

    @Test
    public void updateShouldReplaceAllAzureEntityFields() {
        final AzureRegion originRegion = getAzureRegion();
        originRegion.setCorsRules(CORS_RULES);
        originRegion.setAuthFile(AUTH_FILE);
        originRegion.setAzurePolicy(new AzurePolicy("ipMin", "ipMax"));
        originRegion.setDefault(false);
        final AbstractCloudRegion savedRegion = cloudRegionDao.create(originRegion, null);
        final AzureRegion updatedRegion = getAzureRegion();
        updatedRegion.setId(savedRegion.getId());
        updatedRegion.setCorsRules(UPDATED_CORS_RULES);
        updatedRegion.setAuthFile(UPDATED_AUTH_FILE);
        updatedRegion.setAzurePolicy(new AzurePolicy("updatedIpMin", "updatedIpMax"));
        updatedRegion.setDefault(true);

        cloudRegionDao.update(updatedRegion, null);
        final AzureRegion actualRegion = loadAndCheckType(updatedRegion.getId(), AzureRegion.class);
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

    private GCPRegion getGCPRegion() {
        return withDefaults(new GCPRegion());
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
    private void assertRegionEqualsCommon(final AbstractCloudRegion expectedRegion,
                                          final AbstractCloudRegion actualRegion) {
        assertThat(expectedRegion.getRegionCode(), is(actualRegion.getRegionCode()));
        assertThat(expectedRegion.getName(), is(actualRegion.getName()));
        assertThat(expectedRegion.getOwner(), is(actualRegion.getOwner()));
        assertThat(expectedRegion.getCreatedDate(), is(actualRegion.getCreatedDate()));
        assertThat(expectedRegion.getProvider(), is(actualRegion.getProvider()));
        assertThat(actualRegion, instanceOf(expectedRegion.getClass()));
    }

    private void assertRegionEquals(final AwsRegion expectedRegion, final AwsRegion actualRegion) {
        assertRegionEqualsCommon(expectedRegion, actualRegion);
        assertThat(expectedRegion.getCorsRules(), is(actualRegion.getCorsRules()));
        assertThat(expectedRegion.getPolicy(), is(actualRegion.getPolicy()));
        assertThat(expectedRegion.getKmsKeyId(), is(actualRegion.getKmsKeyId()));
        assertThat(expectedRegion.getProfile(), is(actualRegion.getProfile()));
        assertThat(expectedRegion.getSshKeyName(), is(actualRegion.getSshKeyName()));
        assertThat(expectedRegion.getTempCredentialsRole(), is(actualRegion.getTempCredentialsRole()));
        assertThat(expectedRegion.getBackupDuration(), is(actualRegion.getBackupDuration()));
        assertThat(expectedRegion.isVersioningEnabled(), is(actualRegion.isVersioningEnabled()));
    }

    private void assertRegionEquals(final AzureRegion expectedRegion, final AzureRegion actualRegion) {
        assertRegionEqualsCommon(expectedRegion, actualRegion);
        assertThat(expectedRegion.getResourceGroup(), is(actualRegion.getResourceGroup()));
        assertThat(expectedRegion.getStorageAccount(), is(actualRegion.getStorageAccount()));
        assertThat(expectedRegion.getAzurePolicy(), is(actualRegion.getAzurePolicy()));
        assertThat(expectedRegion.getCorsRules(), is(actualRegion.getCorsRules()));
        assertThat(expectedRegion.getSubscription(), is(actualRegion.getSubscription()));
        assertThat(expectedRegion.getAuthFile(), is(actualRegion.getAuthFile()));
        assertThat(expectedRegion.getSshPublicKeyPath(), is(actualRegion.getSshPublicKeyPath()));
        assertThat(expectedRegion.getMeterRegionName(), is(actualRegion.getMeterRegionName()));
        assertThat(expectedRegion.getAzureApiUrl(), is(actualRegion.getAzureApiUrl()));
        assertThat(expectedRegion.getPriceOfferId(), is(actualRegion.getPriceOfferId()));
    }

    private void assertRegionEquals(final GCPRegion expectedRegion, final GCPRegion actualRegion) {
        assertRegionEqualsCommon(expectedRegion, actualRegion);
        assertThat(expectedRegion.getAuthFile(), is(actualRegion.getAuthFile()));
        assertThat(expectedRegion.getSshPublicKeyPath(), is(actualRegion.getSshPublicKeyPath()));
        assertThat(expectedRegion.getProject(), is(actualRegion.getProject()));
        assertThat(expectedRegion.getApplicationName(), is(actualRegion.getApplicationName()));
        assertThat(expectedRegion.getImpersonatedAccount(), is(actualRegion.getImpersonatedAccount()));
        assertThat(expectedRegion.getCustomInstanceTypes(), is(actualRegion.getCustomInstanceTypes()));
    }

    private <T extends AbstractCloudRegion> T loadAndCheckType(final Long id, final Class<T> type) {
        final Optional<AbstractCloudRegion> actualRegionWrapper = cloudRegionDao.loadById(id);
        assertTrue(actualRegionWrapper.isPresent());
        final AbstractCloudRegion actualRegion = actualRegionWrapper.get();
        assertTrue(type.isAssignableFrom(actualRegion.getClass()));
        return type.cast(actualRegion);
    }
}
