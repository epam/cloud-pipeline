package com.epam.release.notes.agent.service.github;

import com.epam.release.notes.agent.entity.github.Commit;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public class CommitDeserializer extends StdDeserializer<Commit> {

    protected CommitDeserializer() {
        this(null);
    }

    protected CommitDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Commit deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        return Commit.builder()
                .commitMessage(node.get("commit").get("message").asText())
                .commitSha(node.get("sha").asText())
                .build();
    }
}
