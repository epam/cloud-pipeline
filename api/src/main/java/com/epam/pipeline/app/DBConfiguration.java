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

package com.epam.pipeline.app;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

import javax.sql.DataSource;
import java.beans.PropertyVetoException;

@Configuration
@ImportResource({"classpath*:dao/*.xml"})
public class DBConfiguration {

    @Value("${database.url}")
    private String jdbcUrl;

    @Value("${database.username}")
    private String jdbcUsername;

    @Value("${database.password}")
    private String jdbcPassword;

    @Value("${database.driverClass}")
    private String driverClass;

    @Value("${database.max.pool.size}")
    private int maxPoolSize;

    @Value("${database.initial.pool.size}")
    private int initialPoolSize;

    @Value("${datasource.pool.connection.timeout:0}")
    private Integer connectionTimeout;

    @Value("${datasource.pool.debug.unreturned:false}")
    private boolean debugUnreturnedConnections;

    @Value("${datasource.pool.unreturned.timeout:0}")
    private Integer unreturnedTimeout;

    @Value("${datasource.pool.helper.threads:3}")
    private Integer helperThreads;

    @Bean(destroyMethod = "close")
    public DataSource dataSource() throws PropertyVetoException {
        ComboPooledDataSource dataSource = new ComboPooledDataSource();
        dataSource.setDriverClass(driverClass);
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUser(jdbcUsername);
        dataSource.setPassword(jdbcPassword);
        dataSource.setMaxPoolSize(maxPoolSize);
        dataSource.setInitialPoolSize(initialPoolSize);
        dataSource.setCheckoutTimeout(connectionTimeout);
        dataSource.setDebugUnreturnedConnectionStackTraces(debugUnreturnedConnections);
        dataSource.setUnreturnedConnectionTimeout(unreturnedTimeout);
        dataSource.setNumHelperThreads(helperThreads);
        return dataSource;
    }
}
