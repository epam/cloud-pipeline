import React from 'react';
import PropTypes from 'prop-types';
import UserName from '../../../../shared/user-name';

function RunOwner(props) {
  const {className, style, run} = props;
  const {owner} = run || {};
  if (!owner) {
    return null;
  }
  return <UserName className={className} style={style} userName={owner} showIcon />;
}

RunOwner.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
};

export default RunOwner;
