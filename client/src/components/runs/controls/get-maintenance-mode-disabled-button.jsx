import React from 'react';
import {Popover} from 'antd';
import classNames from 'classnames';
import {MAINTENANCE_MODE_DISCLAIMER} from '../../../models/preferences/PreferencesLoad';

const getMaintenanceDisabledButton = (label, buttonId = undefined, props = {}) => (
  <Popover content={MAINTENANCE_MODE_DISCLAIMER}>
    <span
      className={classNames('cp-disabled', props.className)}
      id={buttonId}
      onClick={(e) => e.stopPropagation()}
      style={{...(props.style || {}), cursor: 'not-allowed'}}
    >
      {label}
    </span>
  </Popover>
);

export default getMaintenanceDisabledButton;
