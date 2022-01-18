package com.epam.pipeline.billingreportagent.service;

import com.epam.pipeline.billingreportagent.exception.BillingException;
import com.epam.pipeline.billingreportagent.model.EntityDocument;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DocumentMapper {

    public XContentBuilder map(final EntityDocument document) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            jsonBuilder.startObject();
            document.getFields().forEach((key, value) -> {
                try {
                    jsonBuilder.field(key, value);
                } catch (IOException e) {
                    throw new BillingException(String.format("Converting existing document %s field " +
                            "to a new document has failed.", key), e);
                }
            });
            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new BillingException("Converting existing document to a new document has failed.", e);
        }
    }
}
