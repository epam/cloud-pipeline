/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.docker.DockerRegistryVO;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.docker.DockerRegistryList;
import com.epam.pipeline.entity.docker.DockerRegistrySecret;
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ManifestV2;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEvent;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.docker.DockerAuthorizationException;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.AclPermission;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.acls.model.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
@Service
@AclSync
public class DockerRegistryManager implements SecuredEntityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRegistryManager.class);

    /**
     * Specifies path to template script for generation docket client login script
     */
    @Value("${docker.registry.login.script:}")
    private String scriptTemplate;

    @Autowired
    private DockerRegistryDao dockerRegistryDao;

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private KubernetesManager kubernetesManager;

    @Autowired
    private DockerClientFactory dockerClientFactory;

    @Autowired
    private MetadataManager metadataManager;

    @Autowired
    private ToolGroupManager toolGroupManager;

    @Autowired
    private GrantPermissionManager permissionManager;

    @Autowired
    private DockerAuthService dockerAuthService;

    @Autowired
    private DockerScriptBuilder dockerScriptBuilder;

    @Autowired
    private ToolVersionManager toolVersionManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public DockerRegistry create(DockerRegistryVO dockerRegistryVO) {
        DockerRegistry loadedDockerRegistry = loadByNameOrId(dockerRegistryVO.getPath());
        Assert.isNull(loadedDockerRegistry,
                messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_ALREADY_EXISTS, dockerRegistryVO.getPath())
        );
        DockerRegistry dockerRegistry = dockerRegistryVO.convertToDockerRegistry();
        normalizeCert(dockerRegistry);
        validateAuthentication(dockerRegistry);
        if (StringUtils.isNotBlank(dockerRegistryVO.getUserName())) {
            DockerRegistrySecret secret = dockerRegistryVO.convertToSecret();
            dockerRegistry.setSecretName(kubernetesManager.createDockerRegistrySecret(secret));
        }
        dockerRegistry.setOwner(authManager.getAuthorizedUser());
        dockerRegistryDao.createDockerRegistry(dockerRegistry);
        LOGGER.debug("Repository '{}' was saved.", dockerRegistry.getPath());
        return dockerRegistry;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DockerRegistry updateDockerRegistry(DockerRegistry dockerRegistry) {
        DockerRegistry loadedDockerRegistry = loadByIdOrName(dockerRegistry);
        Assert.notNull(loadedDockerRegistry,
                messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_NOT_FOUND, dockerRegistry.getPath())
        );
        loadedDockerRegistry.setDescription(dockerRegistry.getDescription());
        dockerRegistryDao.updateDockerRegistry(loadedDockerRegistry);
        return loadedDockerRegistry;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DockerRegistry updateDockerRegistryCredentials(DockerRegistryVO dockerRegistryVO) {
        DockerRegistry dockerRegistry = dockerRegistryVO.convertToDockerRegistry();
        DockerRegistry loadedDockerRegistry = loadByIdOrName(dockerRegistry);
        Assert.notNull(loadedDockerRegistry,
                messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_NOT_FOUND, dockerRegistry.getPath())
        );
        loadedDockerRegistry.setExternalUrl(dockerRegistry.getExternalUrl());
        loadedDockerRegistry.setPipelineAuth(dockerRegistry.isPipelineAuth());
        loadedDockerRegistry.setUserName(dockerRegistry.getUserName());
        loadedDockerRegistry.setPassword(dockerRegistry.getPassword());
        loadedDockerRegistry.setCaCert(dockerRegistry.getCaCert());
        normalizeCert(loadedDockerRegistry);
        validateAuthentication(loadedDockerRegistry);
        kubernetesManager.deleteSecret(loadedDockerRegistry.getSecretName());
        if (StringUtils.isNotBlank(loadedDockerRegistry.getUserName())) {
            loadedDockerRegistry.setSecretName(
                    kubernetesManager.createDockerRegistrySecret(
                            DockerRegistrySecret.builder()
                                    .registryUrl(loadedDockerRegistry.getPath())
                                    .userName(loadedDockerRegistry.getUserName())
                                    .password(loadedDockerRegistry.getPassword())
                                    .build()));
        }
        dockerRegistryDao.updateDockerRegistry(loadedDockerRegistry);
        return loadedDockerRegistry;
    }

    @Override
    public DockerRegistry load(Long id) {
        DockerRegistry dockerRegistry = dockerRegistryDao.loadDockerRegistry(id);
        if (dockerRegistry != null) {
            dockerRegistry.setHasMetadata(this.metadataManager.hasMetadata(new EntityVO(id, AclClass.DOCKER_REGISTRY)));
        }
        return dockerRegistry;
    }

    @Override
    public AbstractSecuredEntity changeOwner(Long id, String owner) {
        final DockerRegistry registry = dockerRegistryDao.loadDockerRegistry(id);
        registry.setOwner(owner);
        dockerRegistryDao.updateDockerRegistry(registry);
        return registry;
    }

    @Override public AclClass getSupportedClass() {
        return AclClass.DOCKER_REGISTRY;
    }

    public DockerRegistry loadByNameOrId(String registryPath) {
        DockerRegistry dockerRegistry = dockerRegistryDao.loadDockerRegistry(registryPath);
        if (dockerRegistry != null) {
            dockerRegistry.setHasMetadata(
                    this.metadataManager.hasMetadata(new EntityVO(dockerRegistry.getId(), AclClass.DOCKER_REGISTRY)));
        }
        return dockerRegistry;
    }

    @Override
    public Integer loadTotalCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends AbstractSecuredEntity> loadAllWithParents(Integer page, Integer pageSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DockerRegistry loadWithParents(final Long id) {
        return load(id);
    }

    /**
     * Loads {@link DockerRegistry} by external registry path
     * @param externalUrl external docker registry url
     * @return loaded {@link DockerRegistry}
     * */
    public DockerRegistry loadByExternalUrl(String externalUrl) {
        DockerRegistry dockerRegistry = dockerRegistryDao.loadDockerRegistryByExternalUrl(externalUrl);
        if (dockerRegistry != null) {
            dockerRegistry.setHasMetadata(
                    this.metadataManager.hasMetadata(new EntityVO(dockerRegistry.getId(), AclClass.DOCKER_REGISTRY)));
        }
        return dockerRegistry;
    }

    /**
     * Loads all available {@link DockerRegistry} from database
     * @return all available {@link DockerRegistry}
     * */
    public List<DockerRegistry> loadAllDockerRegistry() {
        return dockerRegistryDao.loadAllDockerRegistry();
    }

    public DockerRegistryList listAllDockerRegistriesWithCerts() {
        List<DockerRegistry> registries = dockerRegistryDao.listAllDockerRegistriesWithCerts();
        registries.forEach(registry -> registry.setCaCert(normalizeCert(registry.getCaCert())));
        return new DockerRegistryList(registries);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DockerRegistry delete(Long id, boolean force) {
        DockerRegistry registry = dockerRegistryDao.loadDockerRegistry(id);
        if (force) {
            //remove all tools from registry to avoid DataIntegrityViolationException
            // But do not delete actual tools from registry
            toolGroupManager.loadByRegistryId(id).forEach(g -> toolGroupManager.delete(g.getId().toString(), force));
        }
        if (StringUtils.isNotBlank(registry.getSecretName())) {
            kubernetesManager.deleteSecret(registry.getSecretName());
        }
        dockerRegistryDao.deleteDockerRegistry(id);
        return registry;
    }

    /**
     * Enable all tools from push events received from docker registry notification service.
     * Only events with action push and provided actors info will be processed.
     * @param events Envelope for collection of docker notification events {@link DockerRegistryEvent}
     *               see https://docs.docker.com/v17.09/registry/notifications/ for more details of a structure
     * @param registry for tool to be enabled
     * @return list of {@link Tool} that was enabled from events
     * */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<Tool> notifyDockerRegistryEvents(String registry, DockerRegistryEventEnvelope events) {
        return events.getEvents()
                .stream()
                .filter(registryEvent ->  !registryEvent.getAction().equals(DockerRegistryAction.PULL.getAction())
                        && StringUtils.isNotBlank(registryEvent.getActor().getName()))
                .map(registryEvent -> {
                    LOGGER.debug(messageHelper.getMessage(MessageConstants.DEBUG_DOCKER_REGISTRY_AUTO_ENABLE,
                            registryEvent.getTarget().getRepository()));
                    Optional<Tool> createdTool = createToolFromEvent(registry, registryEvent);
                    createdTool.ifPresent(tool -> updateToolVersionIfToolPresents(registry, registryEvent, tool));
                    return createdTool;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public Set<String> getRegistryEntries(DockerRegistry registry) {
        String token = getToken(registry, DockerRegistryClaim.LISTING_CLAIM);
        return dockerClientFactory.getDockerClient(registry, token).getRegistryEntries();
    }

    public void setDockerClientFactory(DockerClientFactory dockerClientFactory) {
        this.dockerClientFactory = dockerClientFactory;
    }

    public ImageDescription getImageDescription(DockerRegistry registry, String imageName, String tag) {
        String token = getImageToken(registry, imageName);
        return dockerClientFactory.getDockerClient(registry, token)
                .getImageDescription(registry, imageName, tag);
    }

    public List<String> loadImageTags(DockerRegistry registry, Tool tool) {
        return loadImageTags(registry, tool.getImage());
    }

    public List<String> loadImageTags(DockerRegistry registry, String image) {
        String token = getImageToken(registry, image);
        return dockerClientFactory.getDockerClient(registry, token)
                .getImageTags(registry.getPath(), image);
    }

    public Optional<ManifestV2> deleteImage(DockerRegistry registry, String image, String tag) {
        String token = getImageToken(registry, image);
        return dockerClientFactory.getDockerClient(registry, token).deleteImage(registry, image, tag);
    }

    public void deleteLayer(DockerRegistry registry, String image, String digest) {
        String token = getImageToken(registry, image);
        dockerClientFactory.getDockerClient(registry, token).deleteLayer(registry, image, digest);
    }

    /**
     * @param id of a registry to look for certificate
     * @return byte representation of docker registry if it is available, otherwise returns empty array.
     */
    public byte[] getCertificateContent(Long id) {
        DockerRegistry registry = load(id);
        Assert.notNull(registry, messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_NOT_FOUND, id));
        return StringUtils.isBlank(registry.getCaCert()) ? new byte[0] :
                registry.getCaCert().getBytes(Charset.defaultCharset());
    }

    /**
     * Checks permissions for a requested docker registry and issues a valid JWT token,
     * if action is allowed. Otherwise 401 code will be returned to registry. See documentation
     * for details https://docs.docker.com/registry/spec/auth/token/#requesting-a-token
     * @param userName  requesting permission
     * @param token     provided by docker client, should be a valid Cloud Pipeline token
     * @param dockerRegistryHost    id of docker registry
     * @param scope     requested action in format
     *                  'scope=repository:samalba/my-app:push,repository:samalba/my-test:push'
     * @return
     */
    public JwtRawToken issueTokenForDockerRegistry(String userName, String token,
            String dockerRegistryHost, String scope) {
        LOGGER.debug("Processing authorization request from registry {} for user {} and scope {}",
                dockerRegistryHost, userName, scope);
        UserContext user = dockerAuthService.verifyTokenForDocker(userName, token, dockerRegistryHost);
        DockerRegistry dockerRegistry = loadByNameOrId(dockerRegistryHost);
        if (dockerRegistry == null) {
            throw new DockerAuthorizationException(dockerRegistryHost,
                    messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_NOT_FOUND, dockerRegistryHost));
        }
        try {
            List<DockerRegistryClaim> claims = parseAndValidateScope(userName, dockerRegistry, scope);
            JwtRawToken jwtRawToken =
                    dockerAuthService.issueDockerToken(user, dockerRegistryHost, claims);
            LOGGER.debug("Successfully issued JWT token for registry {} user {} and scope {}",
                    dockerRegistry, userName, scope);
            return jwtRawToken;
        } catch (IllegalArgumentException e) {
            throw new DockerAuthorizationException(dockerRegistryHost, e.getMessage());
        }
    }

    /**
     * Loads template script for configuring docker client authentication, fills in parameters in
     * this template according to the registry, specifies by id and returns result as bytes
     * @param id of registry to generate script
     * @return filled script content
     */
    public byte[] getConfigScript(Long id) {
        DockerRegistry registry = load(id);
        Assert.isTrue(registry.isPipelineAuth(), "Docker login script may be generated only "
                + "for registries using Cloud Pipeline authentication.");
        Assert.notNull(registry, messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_NOT_FOUND, id));
        Assert.isTrue(StringUtils.isNotBlank(scriptTemplate),
                messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_SCRIPT_TEMPLATE_UNAVAILABLE));
        File templateFile = new File(scriptTemplate);
        Assert.isTrue(templateFile.exists() && templateFile.isFile(),
                messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_SCRIPT_TEMPLATE_UNAVAILABLE));
        try(FileReader reader = new FileReader(templateFile)) {
            String content = IOUtils.readLines(reader).stream().collect(Collectors.joining("\n"));
            return dockerScriptBuilder.replaceTemplateParameters(registry, content)
                    .getBytes(Charset.defaultCharset());
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * @return full list of docker registry hierarchy with all child groups and tools wrapped into
     * pseudo root {@link DockerRegistryList} object
     */
    public DockerRegistryList loadAllRegistriesContent() {
        List<DockerRegistry> registries = dockerRegistryDao.loadAllRegistriesContent();
        registries.stream()
                .flatMap(registry -> registry.getGroups().stream())
                .forEach(group -> group.setPrivateGroup(toolGroupManager.isGroupPrivate(group)));
        return new DockerRegistryList(registries);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void checkDockerSecrets() {
        final List<DockerRegistry> dockerRegistries = ListUtils.emptyIfNull(loadAllDockerRegistry());
        if (CollectionUtils.isEmpty(dockerRegistries)) {
            return;
        }
        final Set<String> secrets = kubernetesManager.listAllSecrets();
        dockerRegistries
                .stream()
                .filter(registry -> registry.isPipelineAuth()
                        && StringUtils.isNotBlank(registry.getPassword())
                        && StringUtils.isNotBlank(registry.getUserName()))
                .forEach(registry -> {
                    final String secretName = kubernetesManager.getValidSecretName(registry.getPath());
                    if (!secrets.contains(secretName)) {
                        final DockerRegistrySecret secret = DockerRegistrySecret
                                .builder()
                                .userName(registry.getUserName())
                                .password(registry.getPassword())
                                .registryUrl(registry.getPath())
                                .build();
                        registry.setSecretName(kubernetesManager.createDockerRegistrySecret(secret));
                        dockerRegistryDao.updateDockerRegistry(registry);
                    }
                });
    }

    private void validateAuthentication(DockerRegistry dockerRegistry) {
        if (StringUtils.isNotBlank(dockerRegistry.getUserName())) {
            Assert.notNull(dockerRegistry.getPassword(),
                    "Docker registry password is required, if user name is provided.");
        }
        dockerClientFactory.getDockerClient(dockerRegistry, getToken(dockerRegistry, null))
                .checkAvailability();
    }

    private void normalizeCert(DockerRegistry registry) {
        if (StringUtils.isNotBlank(registry.getCaCert())) {
            registry.setCaCert(normalizeCert(registry.getCaCert()));
        }
    }

    private String normalizeCert(String caCert) {
        String fullCertificate = caCert.replaceAll("^\\s+|\\s+$", "");
        if (!caCert.contains(Constants.X509_BEGIN_CERTIFICATE)) {
            fullCertificate = Constants.X509_BEGIN_CERTIFICATE + fullCertificate;
        }
        if (!caCert.contains(Constants.X509_END_CERTIFICATE)) {
            fullCertificate += Constants.X509_END_CERTIFICATE;
        }
        return fullCertificate.replaceAll("\r\n", "\n");
    }

    private DockerRegistry loadByIdOrName(DockerRegistry registry) {
        if (registry.getId() != null) {
            return load(registry.getId());
        } else {
            return loadByNameOrId(registry.getPath());
        }
    }

    private Optional<Tool> createToolFromEvent(String registry, DockerRegistryEvent registryEvent) {
        DockerRegistry dockerRegistry = fetchDockerRegistry(registry, registryEvent);
        String fullToolName = registryEvent.getTarget().getRepository();
        ImmutablePair<String, String> groupAndTool = toolGroupManager.getGroupAndTool(fullToolName);
        ToolGroup toolGroup = fetchToolGroup(registryEvent, dockerRegistry, groupAndTool.getLeft());
        return enableToolIfNeeded(registryEvent, dockerRegistry, fullToolName, toolGroup);
    }

    private Optional<Tool> enableToolIfNeeded(final DockerRegistryEvent event, final DockerRegistry registry,
                                              final String toolName, final ToolGroup toolGroup) {
        final String actor = event.getActor().getName();
        final String pushTag = event.getTarget().getTag();
        final String pushDigest = event.getTarget().getDigest();
        final Tool tool = buildTool(registry, toolGroup, toolName, actor);
        // check that this tool isn't registered yet.
        final Optional<Tool> toolInGroup = toolManager.loadToolInGroup(tool.getImage(), tool.getToolGroupId());
        if (toolInGroup.isPresent()) {
            LOGGER.warn(messageHelper.getMessage(MessageConstants.ERROR_TOOL_ALREADY_EXIST, tool.getImage(),
                    toolGroup.getName()));
            final ToolVersion toolVersion = toolVersionManager.loadToolVersion(tool.getId(), pushTag);
            if (!toolVersion.getDigest().equals(pushDigest)) {
                toolManager.updateToolVersionScanStatus(toolInGroup.get().getId(),
                        ToolScanStatus.NOT_SCANNED, DateUtils.now(), pushTag,
                        null, pushDigest);
            }
            return toolInGroup;
        }
        if (!permissionManager.isActionAllowedForUser(toolGroup, actor, AclPermission.WRITE)) {
            LOGGER.warn(messageHelper.getMessage(MessageConstants.ERROR_PERMISSION_IS_NOT_GRANTED,
                    registry.getPath(), AclPermission.WRITE_PERMISSION));
            return Optional.empty();
        }
        return Optional.of(toolManager.create(tool, false));
    }

    private DockerRegistry fetchDockerRegistry(String registry, DockerRegistryEvent registryEvent) {
        String registryName = !StringUtils.isEmpty(registry) ? registry : registryEvent.getRequest().getHost();
        DockerRegistry dockerRegistry = loadByNameOrId(registryName);
        if (dockerRegistry == null){
            dockerRegistry = loadByExternalUrl(registryName);
        }
        Assert.notNull(dockerRegistry,
                messageHelper.getMessage(MessageConstants.ERROR_REGISTRY_NOT_FOUND, registryName));
        return dockerRegistry;
    }

    private ToolGroup fetchToolGroup(DockerRegistryEvent event,
                                     DockerRegistry registry, String group) {
        ToolGroup toolGroup;
        if (!toolGroupManager.doesToolGroupExist(registry.getPath(), group)) {
            String actor = event.getActor().getName();
            Assert.isTrue(
                    permissionManager.isActionAllowedForUser(registry, actor, AclPermission.WRITE),
                    messageHelper.getMessage(MessageConstants.ERROR_PERMISSION_IS_NOT_GRANTED,
                            registry.getPath(), AclPermission.WRITE_PERMISSION));
            toolGroup = new ToolGroup();
            toolGroup.setName(group);
            toolGroup.setRegistryId(registry.getId());
            toolGroup.setOwner(event.getActor().getName());
            toolGroupManager.create(toolGroup);
        } else {
            toolGroup = toolGroupManager.loadByNameOrId(registry.getPath() + Constants.PATH_DELIMITER + group);
        }
        return toolGroup;
    }

    private Tool buildTool(DockerRegistry dockerRegistry, ToolGroup toolGroup, String toolName,
            String actor) {
        Tool tool = new Tool();
        tool.setToolGroup(toolGroup.getName());
        tool.setToolGroupId(toolGroup.getId());
        tool.setImage(toolName);
        tool.setCpu("0mi");
        tool.setRam("0Gi");
        tool.setRegistry(dockerRegistry.getPath());
        tool.setRegistryId(dockerRegistry.getId());
        tool.setOwner(actor);
        return tool;
    }

    //expected format: repository:group/image:push
    private List<DockerRegistryClaim> parseAndValidateScope(String userName, DockerRegistry registry, String scope) {
        if (StringUtils.isBlank(scope)) {
            //read permission for at least one child in the registry is required
            if (!permissionManager.isActionAllowedForUser(registry, userName, AclPermission.READ)) {
                DockerRegistry fullTree = getDockerRegistryTree(registry.getId());
                permissionManager.filterTree(userName, fullTree, AclPermission.READ);
                if (CollectionUtils.isEmpty(fullTree.getChildren())) {
                    throw new DockerAuthorizationException(registry.getPath(),
                            messageHelper.getMessage(
                                    MessageConstants.ERROR_REGISTRY_IS_NOT_ALLOWED, userName, registry.getPath()));
                }
            }
            return Collections.emptyList();
        }

        List<DockerRegistryClaim> claims = DockerRegistryClaim.parseClaims(scope);
        claims.forEach(claim -> {
            AbstractSecuredEntity entity = registry;
            List<Permission> permissions = claim.getRequestedPermissions();
            boolean toolRequired = !permissions.contains(AclPermission.WRITE);
            try {
                ToolGroup toolGroup = toolGroupManager.loadToolGroupByImage(registry.getPath(), claim.getImageName());
                entity = toolGroup;
                Optional<Tool> tool =
                                toolManager.loadToolInGroup(claim.getImageName(), toolGroup.getId());
                entity = tool.orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_TOOL_IMAGE_UNAVAILABLE,
                                claim.getImageName())));
            } catch (IllegalArgumentException e) {
                LOGGER.trace(e.getMessage(), e);
                if (toolRequired) {
                    throw new IllegalArgumentException(messageHelper
                                    .getMessage(MessageConstants.ERROR_TOOL_IMAGE_UNAVAILABLE,
                                            claim.getImageName()));
                }
            }

            if (!permissionManager.isActionAllowedForUser(entity, userName, permissions)) {
                throw new DockerAuthorizationException(registry.getPath(), messageHelper
                                .getMessage(MessageConstants.ERROR_REGISTRY_ACTION_IS_NOT_ALLOWED, scope,
                                        userName, registry.getPath()));
            }
        });
        return claims;
    }

    public DockerRegistry getDockerRegistryTree(Long registryId) {
        return dockerRegistryDao.loadDockerRegistryTree(registryId);
    }

    private String getToken(DockerRegistry registry, DockerRegistryClaim claim) {
        String token = null;
        if (registry.isPipelineAuth()) {
            List<DockerRegistryClaim> claims = claim == null ? Collections.emptyList() :
                    Collections.singletonList(claim);
            token = dockerAuthService.issueDockerToken(
                    authManager.getUserContext(), registry.getPath(), claims).getToken();
        }
        return token;
    }

    public String getImageToken(DockerRegistry registry, String imageName) {
        DockerRegistryClaim claim = DockerRegistryClaim.imageClaim(imageName);
        return getToken(registry, claim);
    }

    private void updateToolVersionIfToolPresents(String registry, DockerRegistryEvent registryEvent, Tool tool) {
        LOGGER.debug(messageHelper.getMessage(
                MessageConstants.DEBUG_DOCKER_REGISTRY_AUTO_ENABLE_SUCCESS,
                registryEvent.getTarget().getRepository()));
        String version = registryEvent.getTarget().getTag();
        LOGGER.debug("Detected version {} for image {}", version, tool.getImage());
        DockerRegistry dockerRegistry = fetchDockerRegistry(registry, registryEvent);
        toolVersionManager.updateOrCreateToolVersion(tool.getId(), version,
                tool.getImage(), dockerRegistry, getDockerClient(dockerRegistry, tool.getImage()));
        LOGGER.debug("Tool version attributes for image {}:{} have been successfully updated",
                tool.getImage(), version);
    }


    public DockerClient getDockerClient(DockerRegistry registry, String image) {
        String token = getImageToken(registry, image);
        return dockerClientFactory.getDockerClient(registry, token);
    }
}
