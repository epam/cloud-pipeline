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

package com.epam.pipeline.common;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class MessageHelper {

    private MessageSource messageSource;

    public MessageHelper(final MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public MessageSource getMessageSource() {
        return messageSource;
    }

    /**
     * Tries to resolve the message. Returns the resolved and formatted message or the given message code, when
     * no appropriate message was found in available resources.
     *
     * @param code {@code String} represents the code to look up
     * @param args represents a reference on array of {@code Object} or varargs; both provide arguments that will
     *             be filled in for params within message, or <tt>null</tt> if no arguments; args look like "{0}",
     *             "{1, date}", "{2, time}" within message (also see e.g. Spring documentation for more details)
     * @return {@code String} represents the resolved message if the lookup was successful; otherwise the given
     * keycode
     * @see java.text.MessageFormat
     * @see org.springframework.context.support.AbstractMessageSource
     */
    public String getMessage(final String code, final Object... args) {
        return getMessageSource().getMessage(code, args, code, Locale.getDefault());
    }

}
