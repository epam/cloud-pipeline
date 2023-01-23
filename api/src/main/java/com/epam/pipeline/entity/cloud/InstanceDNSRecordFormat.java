package com.epam.pipeline.entity.cloud;

public enum InstanceDNSRecordFormat {

    /**
     * Relative DNS records include only specific part of actual dns records, f.e:
     * - pipeline-12345-8080-0.eu-west-1
     * - pipeline-12345-8080-0
     * - prettyurl.us-east-1
     * - prettyurl
     */
    RELATIVE,

    /**
     * Absolute DNS records include both specific and common parts of actual dns records, f.e:
     * - pipeline-12345-8080-0.eu-west-1.dns.hosted.zone.domain.com
     * - pipeline-12345-8080-0.dns.hosted.zone.domain.com
     * - prettyurl.us-east-1.dns.hosted.zone.domain.com
     * - prettyurl.dns.hosted.zone.domain.com
     */
    ABSOLUTE
}
