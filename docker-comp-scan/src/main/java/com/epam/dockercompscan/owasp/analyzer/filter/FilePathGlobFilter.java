package com.epam.dockercompscan.owasp.analyzer.filter;

import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.springframework.util.AntPathMatcher;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;

public class FilePathGlobFilter extends AbstractFileFilter {

    private final AntPathMatcher antPathMatcher;
    private final String[] globs;

    public FilePathGlobFilter(final String... globs) {
        this.globs = globs;
        antPathMatcher = new AntPathMatcher();
    }

    @Override
    public boolean accept(final File file) {
        return Arrays.stream(globs).anyMatch(glob -> antPathMatcher.match(glob, file.getAbsolutePath()));
    }

    @Override
    public boolean accept(final File dir, final String name) {
        return Arrays.stream(globs)
                .anyMatch(glob -> antPathMatcher.match(
                        glob,
                        Paths.get(dir.getAbsolutePath(), name).toAbsolutePath().toString())
                );
    }
}
