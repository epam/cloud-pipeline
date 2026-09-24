package com.epam.dockercompscan.owasp.analyzer.filter;

import com.epam.dockercompscan.owasp.analyzer.OSVersionAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilePathGlobFilterTest {
    private FilePathGlobFilter filePathGlobFilter;

    @BeforeEach
    public void setUp() {
        filePathGlobFilter = new OSVersionAnalyzer().buildFilter("/**/");
    }

    @Test
    public void analyzeDependencyTestPositiveOsRelease() {
        final File resource = Paths.get(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/positive/os/os-case1/etc/os-release").getPath()).toFile();
        assertTrue(filePathGlobFilter.accept(resource));
    }

    @Test
    public void analyzeDependencyTestPositiveOsRelease2() {
        final File resource = Paths.get(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/positive/os/os-case2/etc/os-release").getPath()).toFile();
        assertTrue(filePathGlobFilter.accept(resource));
    }

    @Test
    public void analyzeDependencyTestPositiveCentosRelease() {
        final File resource = Paths.get(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/positive/os/etc/centos-release").getPath()).toFile();
        assertTrue(filePathGlobFilter.accept(resource));
    }

    @Test
    public void analyzeDependencyTestNegativeOsRelease() {
        final File resource = Paths.get(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/negative/os-release").getPath()).toFile();
        assertFalse(filePathGlobFilter.accept(resource));
    }
}
