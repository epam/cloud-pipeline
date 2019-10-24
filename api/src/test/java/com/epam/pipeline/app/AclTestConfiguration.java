package com.epam.pipeline.app;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.event.EntityEventServiceManager;
import com.epam.pipeline.manager.filter.FilterManager;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.issue.IssueManager;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.pipeline.DocumentGenerationPropertyManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.pipeline.PipelineFileGenerationManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.pipeline.ToolApiService;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationProviderManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.manager.utils.UtilsManager;
import com.epam.pipeline.security.acl.AclPermissionFactory;
import com.epam.pipeline.security.jwt.JwtTokenGenerator;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.acls.domain.PermissionFactory;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@SpringBootConfiguration
@Import({AclSecurityConfiguration.class, DBConfiguration.class, MappersConfiguration.class})
@ComponentScan(basePackages = {"com.epam.pipeline.manager.security"})
@TestPropertySource(value={"classpath:test-application.properties"})
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
@EnableAspectJAutoProxy
public class AclTestConfiguration {

    @MockBean
    protected MessageHelper messageHelper;

    @MockBean
    protected PipelineRunManager pipelineRunManager;

    @MockBean
    protected PipelineManager pipelineManager;

    @MockBean
    protected PipelineVersionManager pipelineVersionManager;

    @MockBean
    protected InstanceOfferManager instanceOfferManager;

    @MockBean
    protected GitManager gitManager;

    @MockBean
    protected PipelineFileGenerationManager fileGenerationManager;

    @MockBean
    protected DocumentGenerationPropertyManager documentGenerationPropertyManager;

    @MockBean
    protected ToolManager toolManager;

    @MockBean
    protected ToolGroupManager toolGroupManager;

    @MockBean
    protected DockerRegistryManager dockerRegistryManager;

    @MockBean
    protected EntityManager entityManager;

    @MockBean
    protected JwtTokenGenerator jwtTokenGenerator;

    @MockBean
    protected NodesManager nodesManager;

    @MockBean
    protected UserManager userManager;

    @MockBean
    protected MetadataEntityManager metadataEntityManager;

    @MockBean
    protected IssueManager issueManager;

    @MockBean
    protected FolderManager folderManager;

    @MockBean
    protected ConfigurationProviderManager configurationProviderManager;

    @MockBean
    protected RunConfigurationManager runConfigurationManager;

    @MockBean
    protected EntityEventServiceManager entityEventServiceManager;

    @MockBean
    protected ToolApiService toolApiService;

    @MockBean
    protected FilterManager filterManager;

    @MockBean
    protected RunLogManager runLogManager;

    @MockBean
    protected UtilsManager utilsManager;

    @MockBean
    protected ConfigurationRunner configurationRunner;

    @MockBean
    protected CloudRegionDao cloudRegionDao;

    @MockBean
    protected ContextualPreferenceManager contextualPreferenceManager;

    @Bean
    public PermissionFactory permissionFactory() {
        return new AclPermissionFactory();
    }

    //TODO: replace with auto configuration?
    @Bean
    public PlatformTransactionManager platformTransactionManager(final DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
