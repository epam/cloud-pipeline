import React from 'react';
import {Popover} from 'antd';
import {MAINTENANCE_MODE_DISCLAIMER} from '../../../models/preferences/PreferencesLoad';

const getMaintenanceDisabledButton = (label, buttonId = undefined) => (
  <Popover content={MAINTENANCE_MODE_DISCLAIMER}>
    <span
      className="cp-disabled"
      id={buttonId}
      onClick={(e) => e.stopPropagation()}
      style={{cursor: 'not-allowed'}}
    >
      {label}
    </span>
  </Popover>
);

export default getMaintenanceDisabledButton;
