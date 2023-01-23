package com.epam.pipeline.entity.cloud;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class InstanceDNSRecord {

    public static InstanceDNSRecord EMPTY = new InstanceDNSRecord(StringUtils.EMPTY, StringUtils.EMPTY, DNSRecordStatus.NOOP);

    private final String dnsRecord;
    private final String target;
    private final DNSRecordStatus status;

    public enum DNSRecordStatus {
        PENDING, INSYNC, NOOP
    }

}
