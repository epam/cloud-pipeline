/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import type {Dayjs} from 'dayjs';
import {DatePicker} from 'antd';

interface DayPickerProps {
  value?: Dayjs;
  onChange?: (date: Dayjs) => void;
  style?: React.CSSProperties;
}

export default function DayPicker({value, onChange, style}: DayPickerProps) {
  const handleChange = (date: Dayjs | null) => {
    if (onChange && date) onChange(date);
  };

  return (
    <div style={{position: 'relative'}}>
      <DatePicker
        format="D MMM YYYY"
        value={value}
        onChange={handleChange}
        style={{marginRight: 15, ...style}}
        getPopupContainer={(trigger) => trigger.parentNode as HTMLElement}
      />
    </div>
  );
}
