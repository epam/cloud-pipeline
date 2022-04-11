package com.epam.pipeline.manager.dts;

import lombok.Value;

@Value
public class DtsTunnelAddress {

    String host;
    Integer inputPort;
    Integer outputPort;

}
