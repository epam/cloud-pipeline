import React from 'react';
import PropTypes from 'prop-types';
import {Icon} from 'antd';

export function getGroupName (group) {
  let groupName = group;
  if (groupName.toLowerCase().startsWith('role_')) {
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
    showWithPrefix = false
  } = props;
  if (!group) {
    return null;
  }
  const groupName = showWithPrefix ? group : getGroupName(group);
  return (
    <span className={className} style={style}>
      {
        showIcon && <Icon type="team" />
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
  showWithPrefix: PropTypes.bool
};

export default GroupName;
