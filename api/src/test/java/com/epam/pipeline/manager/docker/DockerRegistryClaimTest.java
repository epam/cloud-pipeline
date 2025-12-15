package com.epam.pipeline.manager.docker;

import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DockerRegistryClaimTest {

    public static final String PULL = "pull";
    public static final String PUSH = "push";
    public static final String IMAGE_1 = "image1";
    public static final String IMAGE_2 = "image2";

    @Test
    public void parseClaimCanMergeClaims() {

        final List<DockerRegistryClaim> dockerRegistryClaims =
                DockerRegistryClaim.parseClaims(
                        "repository:image1:pull,repository:image1:push,repository:image2:push"
                );

        dockerRegistryClaims.sort(Comparator.comparing(DockerRegistryClaim::getImageName));
        assertEquals(2, dockerRegistryClaims.size());

        assertEquals(IMAGE_1, dockerRegistryClaims.get(0).getImageName());
        assertEquals(2, dockerRegistryClaims.get(0).getActions().length);
        assertTrue(
                Arrays.stream(dockerRegistryClaims.get(0).getActions())
                        .allMatch(a -> a.equals(PULL) || a.equals(PUSH))
        );

        assertEquals(IMAGE_2, dockerRegistryClaims.get(1).getImageName());
        assertEquals(1, dockerRegistryClaims.get(1).getActions().length);
        assertTrue(
                Arrays.stream(dockerRegistryClaims.get(1).getActions())
                        .allMatch(a -> a.equals(PUSH))
        );
    }

    @Test
    public void parseClaimCanMergeClaimsWithoutDuplicates() {

        final List<DockerRegistryClaim> dockerRegistryClaims =
                DockerRegistryClaim.parseClaims(
                        "repository:image1:pull,repository:image1:pull"
                );

        dockerRegistryClaims.sort(Comparator.comparing(DockerRegistryClaim::getImageName));
        assertEquals(1, dockerRegistryClaims.size());

        assertEquals(IMAGE_1, dockerRegistryClaims.get(0).getImageName());
        assertEquals(1, dockerRegistryClaims.get(0).getActions().length);
        assertTrue(
                Arrays.stream(dockerRegistryClaims.get(0).getActions()).allMatch(a -> a.equals(PULL)));
    }

}