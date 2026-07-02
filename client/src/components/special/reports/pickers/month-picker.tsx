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
import classNames from 'classnames';
import type {Dayjs} from 'dayjs';
import dayjs from '../../../../utils/dayjs';
import {Dropdown} from 'antd';
import {DoubleLeftOutlined, DoubleRightOutlined} from '@ant-design/icons';
import styles from './pickers.module.css';
import PickerButton from './picker-button';

interface MonthPickerProps {
  title?: string;
  value?: Dayjs;
  minimum?: Dayjs;
  maximum?: Dayjs;
  onChange?: (date?: Dayjs) => void;
  style?: React.CSSProperties;
}

export default function MonthPicker({
  title,
  value,
  minimum,
  maximum,
  onChange,
  style,
}: MonthPickerProps) {
  const [year, setYear] = useState<number | undefined>();
  const [selectedYear, setSelectedYear] = useState<number | undefined>();
  const [month, setMonth] = useState<number | undefined>();
  const [opened, setOpened] = useState(false);

  useEffect(() => {
    const date = value ? dayjs.utc(value) : dayjs.utc();
    setYear(date.year());
    setSelectedYear(date.year());
    setMonth(date.month());
  }, [value]);

  const minimumValue = minimum ? dayjs.utc(minimum) : dayjs.utc('1900-01-01');
  const maximumValue = maximum ? dayjs.utc(maximum) : dayjs.utc();
  const currentDate = value ? dayjs.utc(value) : dayjs.utc();

  const canNavigateBack = currentDate.valueOf() > minimumValue.endOf('month').valueOf();
  const canNavigateForward = currentDate.valueOf() < maximumValue.startOf('month').valueOf();

  const handleVisibility = (visible: boolean) => {
    if (!visible) {
      const now = dayjs.utc();
      if (month === undefined) setMonth(now.month());
      setSelectedYear(year ?? now.year());
    }
    setOpened(visible);
  };

  const displayName = value ? dayjs.utc(value).format('MMM YYYY') : title;

  const handleSelect = (m: number) => {
    if (!onChange || selectedYear === undefined) return;
    onChange(dayjs.utc().year(selectedYear).month(m).date(1).startOf('day'));
  };

  const renderOverlay = () => {
    const sy = selectedYear ?? dayjs.utc().year();
    const canLeft = sy > minimumValue.year();
    const canRight = sy < maximumValue.year();

    const navClass = (enabled: boolean) =>
      [styles.navigation, 'cp-billing-calendar-navigation', !enabled && 'disabled']
        .filter(Boolean)
        .join(' ');

    const renderMonth = (m: number) => {
      const date = dayjs.utc().year(sy).month(m).date(1).startOf('day');
      const disabled =
        date.valueOf() < minimumValue.valueOf() || date.valueOf() > maximumValue.valueOf();
      const cls = [
        styles.item,
        'cp-billing-calendar-row-item',
        year === sy && month === m ? 'selected' : undefined,
        disabled ? 'disabled' : undefined,
      ]
        .filter(Boolean)
        .join(' ');
      return (
        <div
          key={m}
          role="button"
          className={cls}
          onClick={() => !disabled && handleSelect(m)}
          style={{width: '33%', fontSize: 'medium'}}
        >
          {date.format('MMM')}
        </div>
      );
    };

    return (
      <div className={classNames(styles.overlay, 'cp-billing-calendar-container')}>
        <div className={classNames(styles.yearsContainer, 'cp-billing-calendar-years-container')}>
          <DoubleLeftOutlined
            role="button"
            className={navClass(canLeft)}
            onClick={(e) => {
              if (!canLeft) return;
              e.stopPropagation();
              e.preventDefault();
              setSelectedYear((y) => (y ?? 0) - 1);
            }}
          />
          <span role="button">{sy}</span>
          <DoubleRightOutlined
            role="button"
            className={navClass(canRight)}
            onClick={(e) => {
              if (!canRight) return;
              e.stopPropagation();
              e.preventDefault();
              setSelectedYear((y) => (y ?? 0) + 1);
            }}
          />
        </div>
        <div>
          {[0, 1, 2, 3].map((row) => (
            <div key={row} className={classNames(styles.row, 'cp-billing-calendar-row')}>
              {renderMonth(row * 3)}
              {renderMonth(row * 3 + 1)}
              {renderMonth(row * 3 + 2)}
            </div>
          ))}
        </div>
      </div>
    );
  };

  return (
    <Dropdown
      open={opened}
      onOpenChange={handleVisibility}
      placement="bottomLeft"
      popupRender={renderOverlay}
    >
      <PickerButton
        className={styles.buttonContainer}
        style={style}
        valueIsSet={!!value}
        onRemove={() => onChange?.()}
        navigationEnabled
        canNavigateBack={canNavigateBack}
        canNavigateForward={canNavigateForward}
        onNavigateBack={() => onChange?.(currentDate.add(-1, 'month'))}
        onNavigateForward={() => onChange?.(currentDate.add(1, 'month'))}
        onClick={() => setOpened((o) => !o)}
      >
        {displayName}
      </PickerButton>
    </Dropdown>
  );
}
