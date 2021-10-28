package com.epam.release.notes.agent.service.github;

import com.epam.release.notes.agent.entity.github.Commit;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.Optional;

public class GitHubCommitDeserializer extends StdDeserializer<Commit> {

    private static final String EMPTY_VALUE = "";
    private static final String COMMIT = "commit";
    private static final String MESSAGE = "message";
    private static final String SHA = "sha";

    protected GitHubCommitDeserializer() {
        this(null);
    }

    protected GitHubCommitDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Commit deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        return Commit.builder()
                .commitMessage(Optional.ofNullable(node.get(COMMIT).get(MESSAGE).asText()).orElse(EMPTY_VALUE))
                .commitSha(Optional.ofNullable(node.get(SHA).asText()).orElse(EMPTY_VALUE))
                .build();
    }
}
