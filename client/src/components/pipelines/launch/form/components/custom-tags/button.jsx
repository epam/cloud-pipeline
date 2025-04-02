import React from 'react';
import PropTypes from 'prop-types';
import {Icon} from 'antd';
import classNames from 'classnames';
import RunTags from '../../../../../runs/run-tags';
import styles from './custom-tags.css';

function CustomTagsButton (props) {
  const {
    className,
    style,
    disabled,
    tags,
    onClick,
    buttonText = 'Configure tags'
  } = props;

  let component = (
    <span>
      <Icon type="setting" />
      <span className={styles.configure}>{buttonText}</span>
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
    );
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
  onClick: PropTypes.func,
  buttonText: PropTypes.node
};

export default CustomTagsButton;
