package com.epam.pipeline.entity.cloud;

import lombok.Data;

@Data
public class InstanceDNSRecord {
    private final String dnsRecord;
    private final String target;
    private final DNSRecordStatus status;

    public enum DNSRecordStatus {
        PENDING, INSYNC, NO_OP
    }

}
