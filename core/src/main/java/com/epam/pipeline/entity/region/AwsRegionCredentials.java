package com.epam.pipeline.entity.region;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AwsRegionCredentials extends AbstractCloudRegionCredentials {

    private String keyId;
    private String accessKey;
    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }
}
