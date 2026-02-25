import React from 'react';
import PropTypes from 'prop-types';
import {TeamOutlined} from '@ant-design/icons';

export function getGroupName (group, removePrefix = false) {
  let groupName = group;
  if (removePrefix && groupName.toLowerCase().startsWith('role_')) {
    groupName = groupName.slice('role_'.length);
  }
  return groupName;
}

function GroupName (props) {
  const {
    className,
    style,
    group,
    showIcon,
    removePrefix = true
  } = props;
  if (!group) {
    return null;
  }
  const groupName = getGroupName(group, removePrefix);
  return (
    <span className={className} style={style}>
      {
        showIcon && <TeamOutlined />
      }
      <span>{groupName}</span>
    </span>
  );
}

GroupName.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  group: PropTypes.string,
  showIcon: PropTypes.bool,
  removePrefix: PropTypes.bool
};

export default GroupName;
