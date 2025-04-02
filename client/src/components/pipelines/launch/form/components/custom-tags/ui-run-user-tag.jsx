import React from 'react';
import PropTypes from 'prop-types';
import styles from './custom-tags.css';
import classNames from "classnames";
import {Input} from "antd";

function UIRunUserTag (props) {
  const {
    className,
    style,
    tagConfiguration,
    tagValue,
    onChange,
  } = props;
  const onTagValueChange = (event) => {
    if (typeof onChange === 'function') {
      onChange(event.target.value);
    }
  };
  return (
    <div
      className={classNames(
        className,
        styles.uiRunTag
      )}
      style={style}
    >
      <span className={styles.title}>
        {tagConfiguration.display ?? tagConfiguration.tag}
      </span>
      <Input
        className={styles.value} value={tagValue || ''}
        onChange={onTagValueChange}
      />
    </div>
  )
}

UIRunUserTag.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  tagConfiguration: PropTypes.object,
  tagValue: PropTypes.string,
  onChange: PropTypes.func
};

export default UIRunUserTag;
