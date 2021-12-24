package com.epam.pipeline.billingreportagent.service.impl.synchronizer.merging;

import com.epam.pipeline.billingreportagent.model.EntityDocument;

import java.time.LocalDate;
import java.util.stream.Stream;

public interface EntityDocumentLoader {

    Stream<EntityDocument> documents(LocalDate from, LocalDate to,
                                     String[] indices);
}
