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

interface YearPickerProps {
  title?: string;
  value?: Dayjs;
  minimum?: Dayjs;
  maximum?: Dayjs;
  onChange?: (date?: Dayjs) => void;
  style?: React.CSSProperties;
}

export default function YearPicker({
  title,
  value,
  minimum,
  maximum,
  onChange,
  style,
}: YearPickerProps) {
  const [year, setYear] = useState<number | undefined>();
  // selectedYear is the start of the visible 9-year range in the overlay
  const [selectedYear, setSelectedYear] = useState<number | undefined>();
  const [opened, setOpened] = useState(false);

  useEffect(() => {
    const y = value ? value.year() : dayjs().year();
    setYear(y);
    setSelectedYear(Math.round(y / 9) * 9);
  }, [value]);

  const minimumValue = minimum ?? dayjs('1900-01-01');
  const maximumValue = maximum ?? dayjs();
  const currentDate = value ?? dayjs();

  const canNavigateBack = currentDate.valueOf() > minimumValue.endOf('year').valueOf();
  const canNavigateForward = currentDate.valueOf() < maximumValue.startOf('year').valueOf();

  const handleVisibility = (visible: boolean) => {
    if (!visible) {
      const y = year ?? dayjs().year();
      setSelectedYear(Math.round(y / 9) * 9);
    }
    setOpened(visible);
  };

  const displayName = value ? `${value.year()} year` : title;

  const handleSelect = (y: number) => {
    if (!onChange) return;
    onChange(dayjs().year(y).month(0).date(1).startOf('day'));
  };

  const renderOverlay = () => {
    const sy = selectedYear ?? Math.round(dayjs().year() / 9) * 9;
    const canLeft = sy > minimumValue.year();
    const canRight = sy + 9 < maximumValue.year();

    const navClass = (enabled: boolean) =>
      [styles.navigation, 'cp-billing-calendar-navigation', !enabled && 'disabled']
        .filter(Boolean)
        .join(' ');

    const renderYear = (shift: number) => {
      const y = sy + shift;
      const date = dayjs().year(y).month(0).date(1).startOf('day');
      const disabled =
        date.valueOf() < minimumValue.valueOf() || date.valueOf() > maximumValue.valueOf();
      const cls = [
        styles.item,
        'cp-billing-calendar-row-item',
        year === y ? 'selected' : undefined,
        disabled ? 'disabled' : undefined,
      ]
        .filter(Boolean)
        .join(' ');
      return (
        <div
          key={shift}
          role="button"
          className={cls}
          onClick={() => !disabled && handleSelect(y)}
          style={{width: '33%', fontSize: 'medium'}}
        >
          {y}
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
              setSelectedYear((y) => (y ?? 0) - 9);
            }}
          />
          <span role="button">
            {sy} – {sy + 9}
          </span>
          <DoubleRightOutlined
            role="button"
            className={navClass(canRight)}
            onClick={(e) => {
              if (!canRight) return;
              e.stopPropagation();
              e.preventDefault();
              setSelectedYear((y) => (y ?? 0) + 9);
            }}
          />
        </div>
        <div>
          {[0, 1, 2].map((row) => (
            <div key={row} className={classNames(styles.row, 'cp-billing-calendar-row')}>
              {renderYear(row * 3)}
              {renderYear(row * 3 + 1)}
              {renderYear(row * 3 + 2)}
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
        onNavigateBack={() => onChange?.(currentDate.add(-1, 'year'))}
        onNavigateForward={() => onChange?.(currentDate.add(1, 'year'))}
        onClick={() => setOpened((o) => !o)}
      >
        {displayName}
      </PickerButton>
    </Dropdown>
  );
}
