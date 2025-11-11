/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.pipeline.manager.keypair;

import com.epam.pipeline.exception.PipelineException;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;

@Slf4j
@Component
public class JSchSshKeyPairManager implements SshKeyPairManager {

    @Override
    public SshKeyPair generate() {
        final StringWriter privateKeyWriter = new StringWriter();
        final StringWriter publicKeyWriter = new StringWriter();
        try(OutputStream privateKeyOutputStream = new WriterOutputStream(privateKeyWriter);
            OutputStream publicKeyOutputStream = new WriterOutputStream(publicKeyWriter)) {
            final KeyPair keyPair = KeyPair.genKeyPair(new JSch(), KeyPair.RSA, 4096);
            keyPair.writePrivateKey(privateKeyOutputStream);
            keyPair.writePublicKey(publicKeyOutputStream, null);
            keyPair.dispose();
        } catch (JSchException | IOException e) {
            log.error("SSH keys generation has failed.", e);
            throw new PipelineException(e);
        }
        final String privateKey = StringUtils.strip(privateKeyWriter.toString());
        final String publicKey = StringUtils.strip(publicKeyWriter.toString());
        return new SshKeyPair(privateKey, publicKey);
    }
}
