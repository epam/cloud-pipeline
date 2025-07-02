import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Input} from 'antd';
import styles from './custom-tags.css';

function UIRunUserTag (props) {
  const {
    className,
    style,
    tagConfiguration,
    tagValue,
    onChange,
    validation
  } = props;
  const {error} = validation || {};
  const onTagValueChange = (event) => {
    if (typeof onChange === 'function') {
      onChange(event.target.value);
    }
  };
  return (
    <div
      className={classNames(className, styles.uiRunTag)}
      style={style}
    >
      <span className={styles.title}>
        {tagConfiguration.display ?? tagConfiguration.tag}
      </span>
      <div
        className={classNames(
          styles.value
        )}
      >
        <Input
          className={classNames(styles.valueInput, {'cp-error': Boolean(error)})}
          value={tagValue || ''}
          onChange={onTagValueChange}
        />
        {
          error && (
            <div className={classNames('cp-error', styles.validationError)}>
              {error}
            </div>
          )
        }
      </div>
    </div>
  );
}

UIRunUserTag.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  tagConfiguration: PropTypes.object,
  tagValue: PropTypes.string,
  onChange: PropTypes.func,
  validation: PropTypes.object
};

export default UIRunUserTag;
