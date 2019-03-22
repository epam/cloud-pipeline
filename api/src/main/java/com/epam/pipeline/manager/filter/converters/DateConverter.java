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

package com.epam.pipeline.manager.filter.converters;


import com.amazonaws.util.StringUtils;
import com.epam.pipeline.manager.filter.FilterOperandType;
import com.epam.pipeline.manager.filter.WrongFilterException;
import lombok.extern.slf4j.Slf4j;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

@Slf4j
public class DateConverter extends AbstractFilterValueConverter {

    public static final String TIMEZONE_OFFSET_PARAMETER = "Timezone offset";

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
    private Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private static final int END_OF_DAY_HOURS = 23;
    private static final int END_OF_DAY_MINUTES = 59;
    private static final int END_OF_DAY_SECONDS = 59;
    private static final int END_OF_DAY_MILLISECONDS = 999;

    @Override
    public Object convert(String field,
                          String value,
                          FilterOperandType operandType,
                          Map<String, Object> params)
            throws WrongFilterException {
        Date result = null;
        String dateValue = this.clearQuotes(value);
        int timezoneOffset = 0;
        if (params != null && params.containsKey(TIMEZONE_OFFSET_PARAMETER)) {
            timezoneOffset = Integer.parseInt(params.get(TIMEZONE_OFFSET_PARAMETER).toString());
        }
        if (!StringUtils.isNullOrEmpty(dateValue)) {
            try {
                result = dateFormat.parse(dateValue);
                if (!dateValue.equals(dateFormat.format(result))) {
                    result = null;
                } else {
                    this.calendar.setTime(result);
                    switch (operandType) {
                        case LESS_OR_EQUALS:
                        case MORE:
                            this.calendar.set(Calendar.HOUR, END_OF_DAY_HOURS);
                            this.calendar.set(Calendar.MINUTE, END_OF_DAY_MINUTES);
                            this.calendar.set(Calendar.SECOND, END_OF_DAY_SECONDS);
                            this.calendar.set(Calendar.MILLISECOND, END_OF_DAY_MILLISECONDS);
                            break;
                        default:
                            this.calendar.set(Calendar.HOUR, 0);
                            this.calendar.set(Calendar.MINUTE, 0);
                            this.calendar.set(Calendar.SECOND, 0);
                            this.calendar.set(Calendar.MILLISECOND, 0);
                            break;
                    }
                    this.calendar.add(Calendar.MINUTE, -timezoneOffset);
                    result = this.calendar.getTime();
                }
            } catch (ParseException e) {
                log.error(e.getMessage(), e);
            }
            if (result != null) {
                return result;
            }
        }
        throw new WrongFilterException(String.format(
                "Wrong date format (%s). Allowed format is: %s",
                value,
                DATE_FORMAT));
    }
}
