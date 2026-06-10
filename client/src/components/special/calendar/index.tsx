/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import {Button, Calendar as AntdCalendar} from 'antd';
import type {CalendarMode, CalendarProps} from 'antd';
import {LeftOutlined, RightOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import type {Dayjs} from 'dayjs';
import styles from './calendar.module.css';

type CalendarHeaderRenderConfig = Parameters<NonNullable<CalendarProps<Dayjs>['headerRender']>>[0];

interface CalendarHeaderProps extends CalendarHeaderRenderConfig {
  fullscreen: boolean;
  validRange?: CalendarProps<Dayjs>['validRange'];
}

function isPrevDisabled(
  value: Dayjs,
  panelMode: CalendarMode,
  validRange?: CalendarProps<Dayjs>['validRange'],
): boolean {
  if (!validRange?.[0]) {
    return false;
  }
  const unit = panelMode === 'year' ? 'year' : 'month';
  return value.startOf(unit).subtract(1, unit).isBefore(validRange[0], unit);
}

function isNextDisabled(
  value: Dayjs,
  panelMode: CalendarMode,
  validRange?: CalendarProps<Dayjs>['validRange'],
): boolean {
  if (!validRange?.[1]) {
    return false;
  }
  const unit = panelMode === 'year' ? 'year' : 'month';
  return value.startOf(unit).add(1, unit).isAfter(validRange[1], unit);
}

function CalendarHeader({
  value,
  type,
  onChange,
  onTypeChange,
  fullscreen,
  validRange,
}: CalendarHeaderProps) {
  const onPrev = () => {
    onChange(type === 'year' ? value.subtract(1, 'year') : value.subtract(1, 'month'));
  };
  const onNext = () => {
    onChange(type === 'year' ? value.add(1, 'year') : value.add(1, 'month'));
  };
  const onTitleClick = () => {
    onTypeChange(type === 'month' ? 'year' : 'month');
  };
  const title = type === 'year' ? value.format('YYYY') : value.format('MMMM YYYY');
  const buttonSize = fullscreen ? 'middle' : 'small';

  return (
    <div className={classNames(styles.header, 'cp-calendar-header')}>
      <Button
        type="text"
        size={buttonSize}
        className={styles.navButton}
        icon={<LeftOutlined />}
        disabled={isPrevDisabled(value, type, validRange)}
        aria-label="Previous"
        onClick={onPrev}
      />
      <button type="button" className={styles.title} onClick={onTitleClick}>
        {title}
      </button>
      <Button
        type="text"
        size={buttonSize}
        className={styles.navButton}
        icon={<RightOutlined />}
        disabled={isNextDisabled(value, type, validRange)}
        aria-label="Next"
        onClick={onNext}
      />
    </div>
  );
}

function Calendar(props: CalendarProps<Dayjs>) {
  const {
    headerRender,
    fullscreen = true,
    validRange,
    mode: modeProp,
    onPanelChange,
    onSelect,
    ...rest
  } = props;
  const [mode, setMode] = React.useState<CalendarMode>(modeProp ?? 'month');

  React.useEffect(() => {
    if (modeProp !== undefined) {
      setMode(modeProp);
    }
  }, [modeProp]);

  const changeMode = React.useCallback(
    (date: Dayjs, newMode: CalendarMode) => {
      if (modeProp === undefined) {
        setMode(newMode);
      }
      onPanelChange?.(date, newMode);
    },
    [modeProp, onPanelChange],
  );

  const handleSelect = React.useCallback<NonNullable<CalendarProps<Dayjs>['onSelect']>>(
    (date, selectInfo) => {
      onSelect?.(date, selectInfo);
      if (mode === 'year') {
        changeMode(date, 'month');
      }
    },
    [mode, onSelect, changeMode],
  );

  return (
    <AntdCalendar
      {...rest}
      mode={mode}
      fullscreen={fullscreen}
      validRange={validRange}
      onPanelChange={changeMode}
      onSelect={handleSelect}
      headerRender={
        headerRender ||
        ((config) => <CalendarHeader {...config} fullscreen={fullscreen} validRange={validRange} />)
      }
    />
  );
}

export default Calendar;
