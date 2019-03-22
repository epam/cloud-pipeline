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

package com.epam.pipeline.jwt.generator.app;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.epam.pipeline.jwt.generator.model.JWTGenerator;
import com.epam.pipeline.jwt.generator.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
    private static final long DEFAULT_EXPIRATION_PERIOD = (long)14 * 24 * 60 * 60;
    public static final int ILLEGAL_INPUT_RETURN_CODE = 1;

    @Parameter(names = "--help", help = true)
    private boolean help = false;

    @Parameter(names = {"-p", "--private"},
            description = "[required] private key file", required = true)
    public String privateKey;

    @Parameter(names = {"-c", "--claim"}, description = "claim in the form [key=value], "
            + "supported claims: user_id, user_name, org_unit_id, role(multiple)")
    private List<String> claims = new ArrayList<>();

    @Parameter(names = {"-e", "--expires"},
            description = "expiration period in seconds from current time, default value is two weeks")
    public Long expiration = DEFAULT_EXPIRATION_PERIOD;

    public static JCommander parseAndValidateArguments(Object object, String[] args) {
        JCommander jcom;
        try {
            jcom = new JCommander(object, args);
        } catch (ParameterException e) {
            LOGGER.error(e.getLocalizedMessage() + "\nPass --help to see details");
            return null;
        }
        return jcom;
    }

    public static void main(String[] args) {
        Application app = new Application();
        JCommander jcom = parseAndValidateArguments(app, args);
        if (jcom == null) {
            System.exit(ILLEGAL_INPUT_RETURN_CODE);
        }
        if (app.help) {
            jcom.usage();
            return;
        }
        try {
            app.run();
        } catch (IllegalArgumentException e) {
            LOGGER.error(e.getLocalizedMessage());
            System.exit(ILLEGAL_INPUT_RETURN_CODE);
        }
    }

    public void run() {
        verifyArguments();
        String key = new JWTGenerator(privateKey).createToken(new User(claims), expiration);
        System.out.println(key);
    }

    private void verifyArguments() {
        if (expiration <= 0) {
            throw new IllegalArgumentException("Expiration period must be positive value.");
        }
    }
}
