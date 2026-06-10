import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './run-detail.module.css';

function RunDetail(props) {
  const {className, style, run, inline, children} = props;
  if (!run) {
    return null;
  }
  return (
    <div
      className={classNames(className, styles.runDetail, {[styles.inline]: inline})}
      style={style}
    >
      {children}
    </div>
  );
}

export const RunDetailProps = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
  inline: PropTypes.bool,
};

RunDetail.propTypes = {...RunDetailProps, children: PropTypes.node.isRequired};

export default RunDetail;
