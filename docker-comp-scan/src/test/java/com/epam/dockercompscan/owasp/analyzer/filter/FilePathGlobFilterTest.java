package com.epam.dockercompscan.owasp.analyzer.filter;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Paths;

import static com.epam.dockercompscan.owasp.analyzer.OSVersionAnalyzer.CENTOS_RELEASE;
import static com.epam.dockercompscan.owasp.analyzer.OSVersionAnalyzer.OS_RELEASE;
import static com.epam.dockercompscan.owasp.analyzer.OSVersionAnalyzer.REDHAT_RELEASE;
import static com.epam.dockercompscan.owasp.analyzer.OSVersionAnalyzer.SYSTEM_RELEASE;

public class FilePathGlobFilterTest {
    private FilePathGlobFilter filePathGlobFilter;

    @Before
    public void setUp() {
        filePathGlobFilter = new FilePathGlobFilter(
                OS_RELEASE, REDHAT_RELEASE, SYSTEM_RELEASE, CENTOS_RELEASE
        );
    }

    @Test
    public void analyzeDependencyTestPositiveOsRelease() {
        final File resource = Paths.get(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/positive/os/os-case1/etc/os-release").getPath()).toFile();
        Assert.assertTrue(filePathGlobFilter.accept(resource));
    }

    @Test
    public void analyzeDependencyTestPositiveOsRelease2() {
        final File resource = Paths.get(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/positive/os/os-case2/etc/os-release").getPath()).toFile();
        Assert.assertTrue(filePathGlobFilter.accept(resource));
    }

    @Test
    public void analyzeDependencyTestPositiveCentosRelease() {
        final File resource = Paths.get(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/positive/os/etc/centos-release").getPath()).toFile();
        Assert.assertTrue(filePathGlobFilter.accept(resource));
    }

    @Test
    public void analyzeDependencyTestNegativeOsRelease() {
        final File resource = Paths.get(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/negative/os-release").getPath()).toFile();
        Assert.assertFalse(filePathGlobFilter.accept(resource));
    }
}
