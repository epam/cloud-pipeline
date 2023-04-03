package com.epam.pipeline.entity.cloud;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder(toBuilder = true)
@RequiredArgsConstructor
public class InstanceDNSRecord {

    public static final InstanceDNSRecord EMPTY =
            new InstanceDNSRecord(StringUtils.EMPTY, StringUtils.EMPTY,
                    InstanceDNSRecordFormat.ABSOLUTE, InstanceDNSRecordStatus.NOOP);

    private final String dnsRecord;
    private final String target;
    private final InstanceDNSRecordFormat format;
    private final InstanceDNSRecordStatus status;
}
