import React from 'react';
import PropTypes from 'prop-types';
import styles from './custom-tags.css';
import {Icon} from "antd";
import classNames from "classnames";
import RunTags from "../../../../../runs/run-tags";


function CustomTagsButton (props) {
  const {
    className,
    style,
    disabled,
    tags,
    onClick
  } = props;

  let component = (
    <span>
      <Icon type="setting"/>
      <span className={styles.configure}>Configure custom tags</span>
    </span>
  );

  if (Object.values(tags ?? {}).length > 0) {
    component = (
      <RunTags
        run={{tags}}
        interactive={false}
        showOnlyCustomUserTags
        style={{display: 'inline-flex', flexWrap: 'wrap'}}
        small={false}
      />
    );
  }

  if (disabled) {
    return (
      <span className={classNames(className, styles.link, 'cp-text')} style={style}>
        {component}
      </span>
    )
  }
  return (
    <a className={classNames(className, styles.link, 'cp-text')} style={style} onClick={onClick}>
      {component}
    </a>
  );
}

CustomTagsButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  tags: PropTypes.object,
  onClick: PropTypes.func
};

export default CustomTagsButton;
