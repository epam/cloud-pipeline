package com.epam.pipeline.manager.datastorage.providers.azure;

import com.azure.core.management.exception.ManagementException;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.CredentialUnavailableException;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.exception.cloud.azure.AzureException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.AzureResourceManager;
import com.epam.pipeline.exception.AuthenticationException;
import com.epam.pipeline.manager.cloud.azure.AzureCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Slf4j
public final class AzureHelper {

    private static final String MANAGEMENT_AZURE_COM_DEFAULT = "https://management.azure.com/.default";
    private static final String CLIENT_ID = "clientId";
    private static final String CLIENT_SECRET = "clientSecret";
    private static final String TENANT_ID = "tenantId";
    private static final String SUBSCRIPTION_ID = "subscriptionId";

    private AzureHelper() {
        //no op
    }

    public static AzureResourceManager buildClient(final AzureRegion region) {
        final AzureCredentials azureCredentials = getAzureCredentials(region);
        return buildClient(azureCredentials);
    }

    public static AzureResourceManager buildClient(final AzureCredentials credentials) throws AuthenticationException {
        try {
            final AzureResourceManager azure = AzureResourceManager
                    .authenticate(credentials.getCredential(), credentials.getProfile())
                    .withDefaultSubscription();
            log.info("Authenticated to subscription: {}", azure.subscriptionId());
            return azure;
        } catch (ManagementException e) {
            throw new AuthenticationException(String.format("Failed to authenticate to Azure: %s", e.getMessage()));
        }
    }

    public static AzureCredentials getAzureCredentials(final AzureRegion region) throws AuthenticationException {
        TokenCredential credential;
        String tenantId;
        String subscriptionId;
        AzureProfile profile = new AzureProfile(AzureEnvironment.AZURE);
        if (StringUtils.isNotBlank(region.getManagedIdentity())) {
            log.info("Authentication using Managed Identity");
            credential = new DefaultAzureCredentialBuilder()
                    .managedIdentityClientId(region.getManagedIdentity())
                    .build();
        } else if (StringUtils.isNotBlank(region.getAuthFile())) {
            log.info("Authentication using Azure auth file");
            final File authFile = new File(region.getAuthFile());
            final Map<String, String> config;
            try {
                config = new ObjectMapper().readValue(authFile, Map.class);
            } catch (IOException e) {
                throw new AuthenticationException("Failed to get Azure credentials from auth file.");
            }
            final String clientId = config.get(CLIENT_ID);
            final String clientSecret = config.get(CLIENT_SECRET);
            tenantId = config.get(TENANT_ID);
            subscriptionId = config.get(SUBSCRIPTION_ID);
            credential = new ClientSecretCredentialBuilder()
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .tenantId(tenantId)
                    .build();
            profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);
        } else {
            throw new AuthenticationException("Failed to get Azure credentials.");
        }
        return AzureCredentials.builder()
                .credential(credential)
                .profile(profile)
                .build();
    }

    public static String getBearerToken(final TokenCredential credential) {
        final String[] scopes = {MANAGEMENT_AZURE_COM_DEFAULT};
        final TokenRequestContext context = new TokenRequestContext().addScopes(scopes);
        try {
            final AccessToken accessToken = credential.getTokenSync(context);
            return accessToken.getToken();
        } catch (CredentialUnavailableException e) {
            throw new AzureException(String.format("Error getting access token: %s", e.getMessage()));
        }
    }
}
