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

export const Quarters: Record<number, string> = {
  1: 'I',
  2: 'II',
  3: 'III',
  4: 'IV',
};

interface QuarterPickerProps {
  title?: string;
  value?: Dayjs;
  minimum?: Dayjs;
  maximum?: Dayjs;
  onChange?: (date?: Dayjs) => void;
  style?: React.CSSProperties;
}

export default function QuarterPicker({
  title,
  value,
  minimum,
  maximum,
  onChange,
  style,
}: QuarterPickerProps) {
  const [year, setYear] = useState<number | undefined>();
  const [quarter, setQuarter] = useState<number | undefined>();
  const [selectedYear, setSelectedYear] = useState<number | undefined>();
  const [opened, setOpened] = useState(false);

  useEffect(() => {
    if (value) {
      const date = dayjs.utc(value);
      const y = date.year();
      setYear(y);
      setQuarter(date.quarter());
      setSelectedYear(y);
    } else {
      setYear(undefined);
      setQuarter(undefined);
      setSelectedYear(dayjs.utc().year());
    }
  }, [value]);

  const minimumValue = minimum ? dayjs.utc(minimum) : dayjs.utc('1900-01-01');
  const maximumValue = maximum ? dayjs.utc(maximum) : dayjs.utc();
  const currentDate = value ? dayjs.utc(value) : dayjs.utc();

  const canNavigateBack = currentDate.valueOf() > minimumValue.endOf('quarter').valueOf();
  const canNavigateForward = currentDate.valueOf() < maximumValue.startOf('quarter').valueOf();

  const handleVisibility = (visible: boolean) => {
    if (!visible) setSelectedYear(year ?? dayjs.utc().year());
    setOpened(visible);
  };

  const displayName = (() => {
    if (!value) return title;
    const y = dayjs.utc(value).year();
    const q = dayjs.utc(value).quarter();
    return `${Quarters[q]} quarter, ${y}`;
  })();

  const handleSelect = (y: number, q: number) => {
    if (!onChange) return;
    onChange(
      dayjs
        .utc()
        .year(y)
        .month((q - 1) * 3 + 1)
        .date(1)
        .startOf('day'),
    );
  };

  const renderOverlay = () => {
    const sy = selectedYear ?? dayjs.utc().year();
    const canLeft = sy > minimumValue.year();
    const canRight = sy < maximumValue.year();

    const navClass = (enabled: boolean) =>
      [styles.navigation, 'cp-billing-calendar-navigation', !enabled && 'disabled']
        .filter(Boolean)
        .join(' ');

    const renderQuarter = (q: number) => {
      const date = dayjs.utc(`${sy}-${(q - 1) * 3 + 1}-01`, 'YYYY-MM-DD');
      const disabled =
        date.valueOf() < minimumValue.valueOf() || date.valueOf() > maximumValue.valueOf();
      const cls = [
        styles.item,
        'cp-billing-calendar-row-item',
        year === sy && quarter === q ? 'selected' : undefined,
        disabled ? 'disabled' : undefined,
      ]
        .filter(Boolean)
        .join(' ');
      return (
        <div
          key={q}
          role="button"
          className={cls}
          onClick={() => !disabled && handleSelect(sy, q)}
          style={{width: '50%'}}
        >
          {Quarters[q]}
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
          <div className={classNames(styles.row, 'cp-billing-calendar-row')}>
            {renderQuarter(1)}
            {renderQuarter(2)}
          </div>
          <div className={classNames(styles.row, 'cp-billing-calendar-row')}>
            {renderQuarter(3)}
            {renderQuarter(4)}
          </div>
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
        onNavigateBack={() => onChange?.(currentDate.add(-1, 'quarter'))}
        onNavigateForward={() => onChange?.(currentDate.add(1, 'quarter'))}
        onClick={() => setOpened((o) => !o)}
      >
        {displayName}
      </PickerButton>
    </Dropdown>
  );
}
