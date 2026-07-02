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
import {Button, Space} from 'antd';
import {CalendarOutlined, CloseCircleFilled, LeftOutlined, RightOutlined} from '@ant-design/icons';
import styles from './pickers.module.css';
import '../../../../staticStyles/billing-calendar.css';

interface PickerButtonProps {
  className?: string;
  children?: React.ReactNode;
  onClick?: () => void;
  onRemove?: () => void;
  valueIsSet?: boolean;
  style?: React.CSSProperties;
  navigationEnabled?: boolean;
  canNavigateBack?: boolean;
  canNavigateForward?: boolean;
  onNavigateBack?: () => void;
  onNavigateForward?: () => void;
}

// antd v6 Dropdown (rc-trigger) calls getDOM(ref) which accepts HTMLElement or {nativeElement}.
// A forwardRef component resolves its ref to the span DOM node directly, satisfying isDOM().
const PickerButton = React.forwardRef<HTMLSpanElement, PickerButtonProps>(function PickerButton(
  {
    className,
    children,
    onClick,
    onRemove,
    valueIsSet = false,
    style,
    navigationEnabled = false,
    canNavigateBack = false,
    canNavigateForward = false,
    onNavigateBack,
    onNavigateForward,
  },
  ref,
) {
  const [hovered, setHovered] = React.useState(false);
  const hasRemove = !!onRemove;

  const handleRemove = (e: React.MouseEvent) => {
    e.stopPropagation();
    e.preventDefault();
    onRemove?.();
  };

  const contentClass = valueIsSet ? 'cp-billing-calendar-set-value' : undefined;

  return (
    <span ref={ref} style={{display: 'inline-block'}}>
      <Space.Compact className={className} style={style} onClick={(e) => e.stopPropagation()}>
        {navigationEnabled && (
          <Button style={{paddingLeft: 8}} disabled={!canNavigateBack} onClick={onNavigateBack}>
            <LeftOutlined />
          </Button>
        )}
        <Button
          className={styles.button}
          onClick={onClick}
          onMouseOver={() => setHovered(true)}
          onMouseOut={() => setHovered(false)}
        >
          <div className={contentClass}>
            {children ?? 'Calendar'}
            {valueIsSet && hasRemove && hovered ? (
              <CloseCircleFilled
                className="cp-billing-calendar-set-value-close cp-billing-calendar-icon"
                onClick={handleRemove}
              />
            ) : (
              <CalendarOutlined className="cp-billing-calendar-icon" />
            )}
          </div>
        </Button>
        {navigationEnabled && (
          <Button
            style={{paddingRight: 8}}
            disabled={!canNavigateForward}
            onClick={onNavigateForward}
          >
            <RightOutlined />
          </Button>
        )}
      </Space.Compact>
    </span>
  );
});

export default PickerButton;
