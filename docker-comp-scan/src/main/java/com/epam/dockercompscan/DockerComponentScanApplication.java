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

package com.epam.dockercompscan;

import com.epam.dockercompscan.config.WEBMVCConfiguration;
import com.epam.dockercompscan.util.LayerScanCache;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Semaphore;

@SpringBootApplication
@ComponentScan(basePackages = {"com.epam.dockercompscan"})
@Import({WEBMVCConfiguration.class})
public class DockerComponentScanApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerComponentScanApplication.class);

    @Value("${worker.threads.count}")
    private int numberOfScanningThreads;

    @Value("${expire.cached.scan.time:24}")
    private int expireCacheTime;

    @Value("${number.cached.scans:50}")
    private int numberOfCachedScans;

    @Value("${ssl.insecure.enable}")
    private boolean sslInsecureEnable;

    public static void main(String[] args) {
        SpringApplication.run(DockerComponentScanApplication.class, args);
    }

    @Bean
    public OkHttpClient dockerRegistryClient() {
        OkHttpClient client;
        if (sslInsecureEnable) {
            try {
                client = getUnsafeOkHttpClient();
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                LOGGER.error("Cannot allow insecure server connections when using SSL. Cause: ", e);
                client = new OkHttpClient();
            }
        }else {
            client = new OkHttpClient();
        }
        return client;
    }

    private static OkHttpClient getUnsafeOkHttpClient() throws NoSuchAlgorithmException, KeyManagementException {
        final TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }
        };

        // Install the all-trusting trust manager
        final SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        // Create an ssl socket factory with our all-trusting manager
        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.sslSocketFactory(sslSocketFactory,  (X509TrustManager)trustAllCerts[0]);
        builder.hostnameVerifier((hostname, session) -> true);
        return builder.build();
    }

    @Bean
    public LayerScanCache layerScanCache() {
        return new LayerScanCache(expireCacheTime, numberOfCachedScans);
    }

    @Bean
    public Semaphore scanSlots() {
        return new Semaphore(numberOfScanningThreads);
    }

}
