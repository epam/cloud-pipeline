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

import React, {useState, useEffect} from 'react';
import type {Dayjs} from 'dayjs';
import dayjs from '../../../../utils/dayjs';
import {DatePicker, Button, Popover} from 'antd';
import {Range, Period} from '../../periods';
import styles from './range-picker.module.css';
import PickerButton from './picker-button';
import pickerStyles from './pickers.module.css';

type ParsedRange = {start: Dayjs | null; end: Dayjs | null};

function parseRange(range: string | undefined): ParsedRange {
  // eslint-disable-next-line @typescript-eslint/no-unsafe-call
  return Range.parse(range, Period.custom) as ParsedRange;
}

function checkDateInRange(date: Dayjs, start?: Dayjs | null, end?: Dayjs | null): boolean {
  const dateToCheck = dayjs.utc(date).startOf('day').add(1, 'day');
  if (start && dayjs.utc(start).startOf('day').valueOf() > dateToCheck.valueOf()) return true;
  if (end && dayjs.utc(end).endOf('day').valueOf() < dateToCheck.valueOf()) return true;
  return dayjs.utc().endOf('day').valueOf() < dateToCheck.valueOf();
}

interface RangePickerProps {
  disabled?: boolean;
  range?: string;
  onChange?: (start: Dayjs | null, end: Dayjs | null) => void;
}

export default function RangePicker({disabled, range, onChange}: RangePickerProps) {
  const [visible, setVisible] = useState(false);
  const [fromOpen, setFromOpen] = useState(false);
  const [toOpen, setToOpen] = useState(false);
  const [startValue, setStartValue] = useState<Dayjs | null>(null);
  const [endValue, setEndValue] = useState<Dayjs | null>(null);

  useEffect(() => {
    const {start, end} = parseRange(range);
    setStartValue(start);
    setEndValue(end);
  }, [range]);

  // Re-sync with latest range when popover opens
  useEffect(() => {
    if (!visible) return;
    const {start, end} = parseRange(range);
    setStartValue(start);
    setEndValue(end);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);

  const handleVisibility = (v: boolean) => {
    if (v || (!fromOpen && !toOpen)) setVisible(v);
  };

  const handleApply = () => {
    onChange?.(startValue, endValue);
    handleVisibility(false);
  };

  const {start, end} = parseRange(range);

  const getRangePeriodString = () => {
    if (!start || !end) return undefined;
    const from = start.format('D MMM YYYY');
    const to = end.format('D MMM YYYY');
    return from === to ? end.format('D MMMM YYYY') : `${from} to ${to}`;
  };

  const menu = (
    <div className={styles.menuContainer}>
      <div className={styles.datesContainer}>
        <DatePicker
          disabledDate={(date) => checkDateInRange(date, undefined, endValue)}
          format="D MMM YYYY"
          value={startValue}
          placeholder="From"
          onChange={(v) => setStartValue(v)}
          style={{marginRight: 15}}
          onOpenChange={setFromOpen}
        />
        <DatePicker
          disabledDate={(date) => checkDateInRange(date, startValue)}
          format="D MMM YYYY"
          value={endValue}
          placeholder="To"
          onChange={(v) => setEndValue(v)}
          onOpenChange={setToOpen}
        />
      </div>
      <div className={styles.btnContainer}>
        <Button className={styles.filterBtn} onClick={() => handleVisibility(false)}>
          Cancel
        </Button>
        <Button
          className={styles.filterBtn}
          type="primary"
          onClick={handleApply}
          disabled={!startValue || !endValue}
        >
          Apply
        </Button>
      </div>
    </div>
  );

  return (
    <div style={{position: 'relative'}}>
      <Popover
        placement="bottom"
        content={menu}
        open={visible && !disabled}
        getPopupContainer={(triggerNode) => triggerNode.parentNode as HTMLElement}
        onOpenChange={handleVisibility}
        trigger={['click']}
      >
        <PickerButton
          className={pickerStyles.buttonContainer}
          valueIsSet={!!start && !!end}
          navigationEnabled={false}
        >
          {getRangePeriodString()}
        </PickerButton>
      </Popover>
    </div>
  );
}
