/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Button, Alert, Input} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import classNames from 'classnames';
import {sectionNames} from './utilities/variable-sections';
import styles from './element-preview.css';

class ElementPreview extends React.Component {
  renderButtons = () => {
    return (<div
      className={styles.container}>
      <Button
        type="primary"
        className={styles.button}
      >
        Primary
      </Button>
      <Button
        className={styles.button}
      >
        Default
      </Button>
      <Button
        type="danger"
        className={styles.button}
      >
        Danger
      </Button>
      <Button
        disabled
        className={styles.button}
      >
        Disabled
      </Button>
    </div>);
  };
  renderTables = () => {
    return null;
  }

  renderMenu = () => (
    <div className={styles.container}>
      <Menu>
        <MenuItem>MenuItem 1</MenuItem>
        <MenuItem>MenuItem 2</MenuItem>
        <MenuItem>MenuItem 3</MenuItem>
        <MenuItem>MenuItem 4</MenuItem>
      </Menu>
    </div>
  );

  renderCards = () => (
    <div className={styles.container}>
      <div
        className={classNames(styles.panel, 'cp-content-panel')}
      >
        <div
          className="cp-content-panel-header"
          style={{width: '100%'}}
        >
          <h4 style={{padding: 10}}>Header title</h4>
        </div>
        <div style={{padding: 10}}>
          <p>Card content</p>
          <p>Card content</p>
        </div>
      </div>
    </div>
  );

  renderForms = () => (
    <div className={styles.container}>
      <Input
        placeholder="placeholder text"
        className={styles.input}
      />
      <Input
        placeholder="disabled"
        disabled
        className={styles.input}
      />
    </div>
  );
  renderAlerts = () => (
    <div className={styles.container}>
      <Alert
        type="info"
        showIcon
        message="Info alert"
        className={styles.alert}
      />
      <Alert
        type="success"
        showIcon
        message="Success alert"
        className={styles.alert}
      />
      <Alert
        type="warning"
        showIcon
        message="Warning alert"
        className={styles.alert}
      />
      <Alert
        type="error"
        showIcon
        message="Error alert"
        className={styles.alert}
      />
    </div>
  );
  renderNavigation = () => (<div>navigation</div>);

  renderPreviews () {
    const {section} = this.props;
    switch (section) {
      case sectionNames.buttons:
        return this.renderButtons();
      case sectionNames.tables:
        return this.renderTables();
      case sectionNames.alerts:
        return this.renderAlerts();
      case sectionNames.menu:
        return this.renderMenu();
      case sectionNames.navigation:
        return this.renderNavigation();
      case sectionNames.cards:
        return this.renderCards();
      case sectionNames.input:
        return this.renderForms();
      default:
        return null;
    }
  }

  render () {
    const {className} = this.props;
    return (
      <div
        className={className}
      >
        {this.renderPreviews()}
      </div>
    );
  }
}

ElementPreview.propTypes = {
  className: PropTypes.string,
  section: PropTypes.string
};

export default ElementPreview;
