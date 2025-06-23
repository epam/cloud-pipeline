package com.epam.pipeline.manager.gcp;

import com.epam.pipeline.entity.pipeline.GcpArtifactRegistryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GCPParsingUtils {
    public static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GCP_IMAGE_DIGEST_FORMAT = "([^/]+\\.pkg\\.dev)/([^/]+)/([^/]+)/([^@]+)@(.+)";
    private static final String GCP_ARTIFACT_REGISTRY_HOST_FORMAT = "^([a-z0-9]+-[a-z0-9]+)-docker\\.pkg\\.dev$";

    public static GCPImageDetails parseGcpEvent(final GcpArtifactRegistryEvent event) {
        final Pattern pattern = Pattern.compile(GCP_IMAGE_DIGEST_FORMAT);
        final Matcher matcher = pattern.matcher(event.getDigest());

        if (matcher.matches()) {
            return GCPImageDetails.builder()
                    .registry(matcher.group(1))
                    .project(matcher.group(2))
                    .repository(matcher.group(3))
                    .image(matcher.group(4))
                    .digest(matcher.group(5))
                    .region(extractRegionFromRegistry(matcher.group(1)))
                    .tag(extractTag(event.getTag()))
                    .build();
        } else {
            throw new IllegalArgumentException("Invalid GCP Artifact Registry Digest format: " + event.getDigest());
        }
    }

    public static String extractRegionFromRegistry(final String registry) {
        if (registry == null || registry.isEmpty()) {
            return null;
        }

        final Pattern pattern = Pattern.compile(GCP_ARTIFACT_REGISTRY_HOST_FORMAT);
        final Matcher matcher = pattern.matcher(registry);

        if (matcher.matches()) {
            return matcher.group(1);
        }else {
            throw new IllegalArgumentException("Invalid hostname for GCP Artifact Registry: " + registry);
        }
    }

    public static String extractTag(final String input) {
        if (input == null || !input.contains(":")) {
            return null;
        }
        return input.substring(input.lastIndexOf(':') + 1);
    }

    private GCPParsingUtils() {}
}
