package com.epam.pipeline.manager.datastorage.providers.azure;

import com.epam.pipeline.exception.cloud.azure.AzureException;
import com.microsoft.azure.credentials.AzureCliCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.rest.LogLevel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;

@Slf4j
public class AzureHelper {

    public static Azure buildClient(final String authFile) {
        try {
            final Azure.Configurable builder = Azure.configure()
                    .withLogLevel(LogLevel.BASIC);
            return authenticate(authFile, builder)
                    .withDefaultSubscription();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new AzureException(e);
        }
    }

    private static Azure.Authenticated authenticate(final String authFile,
                                             Azure.Configurable builder) throws IOException {
        if (StringUtils.isBlank(authFile)) {
            return builder.authenticate(AzureCliCredentials.create());
        }
        return builder.authenticate(new File(authFile));
    }

}
