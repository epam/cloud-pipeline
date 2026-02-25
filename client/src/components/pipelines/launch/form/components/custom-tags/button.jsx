import React from 'react';
import PropTypes from 'prop-types';
import {SettingOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import RunTags from '../../../../../runs/run-tags';
import styles from './custom-tags.css';
import {
  filterVisibleTagsSync
} from '../../../../../runs/run-tags/utilities';

function CustomTagsButton (props) {
  const {
    className,
    style,
    disabled,
    tags,
    onClick,
    buttonText = 'Configure tags',
    validation = [],
    visibleTags = []
  } = props;

  let component = (
    <span>
      <SettingOutlined />
      <span className={styles.configure}>{buttonText}</span>
    </span>
  );

  const filteredTags = filterVisibleTagsSync(tags, visibleTags);

  if (Object.values(filteredTags ?? {}).length > 0) {
    component = (
      <RunTags
        run={{tags: filteredTags}}
        interactive={false}
        showOnlyCustomUserTags
        style={{display: 'inline-flex', flexWrap: 'wrap'}}
        small={false}
      />
    );
  }

  const valid = !validation || validation.length === 0;

  const validationComponent = (() => {
    if (validation && validation.length > 0) {
      const count = validation.length;
      return (<span className={classNames(styles.uiRunTagsValidation, 'cp-error')}>
        {count} tag{count === 1 ? ' is' : 's are'} required
      </span>);
    }
    return null;
  })();

  if (disabled) {
    return (
      <span
        className={classNames(className, styles.link, {'cp-text': valid, 'cp-error': !valid})}
        style={style}>
        {component}
        {validationComponent}
      </span>
    );
  }
  return (
    <a
      className={classNames(className, styles.link, {'cp-text': valid, 'cp-error': !valid})}
      style={style}
      onClick={onClick}>
      {component}
      {validationComponent}
    </a>
  );
}

CustomTagsButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  tags: PropTypes.object,
  validation: PropTypes.oneOfType(PropTypes.object, PropTypes.array),
  visibleTags: PropTypes.oneOfType(PropTypes.object, PropTypes.array),
  payload: PropTypes.object,
  onClick: PropTypes.func,
  buttonText: PropTypes.node
};

export default CustomTagsButton;
