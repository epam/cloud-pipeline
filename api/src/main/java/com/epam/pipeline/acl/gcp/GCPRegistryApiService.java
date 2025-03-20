package com.epam.pipeline.acl.gcp;

import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEvent;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.GcpArtifactRegistryEvent;
import com.epam.pipeline.entity.pipeline.GcpArtifactRegistryEventBody;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.manager.docker.DockerRegistryAction;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.gcp.GCPImageDetails;
import com.epam.pipeline.manager.gcp.GCPParsingUtils;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.jwt.GCPJwtTokenVerifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.Objects;

import static com.epam.pipeline.entity.region.CloudProvider.GCP;
import static com.epam.pipeline.manager.gcp.GCPParsingUtils.MAPPER;
import static com.epam.pipeline.manager.gcp.GCPRegistryAction.INSERT;

@Service
@RequiredArgsConstructor
public class GCPRegistryApiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GCPRegistryApiService.class);
    private final CloudRegionDao cloudRegionDao;
    private final DockerRegistryManager dockerRegistryManager;
    private final GCPJwtTokenVerifier gcpJwtTokenVerifier;
    private final AuthManager authManager;

    public Tool notifyGcpRegistryEvent(final String jwtToken, final GcpArtifactRegistryEventBody eventBody) {
        final JwtRawToken  jwtRawToken = JwtRawToken.fromHeader(jwtToken);
        gcpJwtTokenVerifier.validateToken(jwtRawToken.getToken());
        authManager.setAdminContext();

        final GcpArtifactRegistryEventBody.Message message = eventBody.getMessage();
        final String decodedData = new String(Base64.getDecoder().decode(message.getData()));

        try {
            final GcpArtifactRegistryEvent event = MAPPER.readValue(decodedData, GcpArtifactRegistryEvent.class);
            //ignore other actions beside INSERT
            if (INSERT.getAction().equals(event.getAction()) &&
                    Objects.nonNull(event.getDigest()) && Objects.nonNull(event.getTag())) {

                final GCPImageDetails imageDetails = GCPParsingUtils.parseGcpEvent(event);
                final DockerRegistry gcpRegistry = dockerRegistryManager.loadByNameOrId(imageDetails.getRegistry());

                //ignore requests made by different provider
                if (Objects.nonNull(gcpRegistry) && GCP == gcpRegistry.getProvider()) {
                    final GCPRegion region = (GCPRegion) cloudRegionDao.loadByRegionName(imageDetails.getRegion())
                            .filter(r -> GCP == r.getProvider())
                            .orElseThrow(() -> new ObjectNotFoundException(
                                    String.format("No GCP Region with region name: %s", imageDetails.getRegion())));

                    //ignore requests made by different project
                    if (Objects.isNull(region.getProject()) || !region.getProject().equals(imageDetails.getProject())) {
                        LOGGER.info(String.format("Region - %s: Project missing or mismatched.", region.getName()));
                        return null;
                    }

                    final DockerRegistryEvent dockerRegistryEvent = mapGcpEventToDockerRegistryEvent(imageDetails);
                    final DockerRegistryEventEnvelope events = new DockerRegistryEventEnvelope();
                    events.setEvents(Collections.singletonList(dockerRegistryEvent));
                    return dockerRegistryManager.notifyDockerRegistryEvents(gcpRegistry.getPath(), events).get(0);
                }
            }
            LOGGER.info("Event ignored: Action is not INSERT or provider is not GCP.");
            return null;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private DockerRegistryEvent mapGcpEventToDockerRegistryEvent(final GCPImageDetails gcpImageDetails) {
        final DockerRegistryEvent dockerRegistryEvent = new DockerRegistryEvent();
        dockerRegistryEvent.setAction(DockerRegistryAction.PUSH.getAction());

        final DockerRegistryEvent.Target target = new DockerRegistryEvent.Target();
        target.setDigest(gcpImageDetails.getDigest());
        target.setTag(gcpImageDetails.getTag());
        target.setRepository(String.format("%s/%s/%s",
                gcpImageDetails.getProject(), gcpImageDetails.getRepository(), gcpImageDetails.getImage()));
        dockerRegistryEvent.setTarget(target);

        final DockerRegistryEvent.Actor actor = new DockerRegistryEvent.Actor();
        actor.setName("PIPE_ADMIN");
        dockerRegistryEvent.setActor(actor);

        return dockerRegistryEvent;
    }
}
