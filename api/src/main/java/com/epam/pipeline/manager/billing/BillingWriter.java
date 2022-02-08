package com.epam.pipeline.manager.billing;

import java.io.Flushable;

public interface BillingWriter<B> extends Flushable {

    void writeHeader();
    void write(B billing);
}
