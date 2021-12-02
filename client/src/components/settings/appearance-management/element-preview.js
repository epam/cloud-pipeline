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
import {Button, Alert, Input, Table, Select, Icon} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import classNames from 'classnames';
import {sectionNames} from './utilities/variable-sections';
import styles from './element-preview.css';

class ElementPreview extends React.Component {
  state = {
    navigationSelected: {
      home: false,
      run: false,
      search: false
    }
  };

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
    const dataSource = [{
      key: '1',
      column1: 'Row',
      column2: 1,
      column3: 'Row1'
    }, {
      key: '2',
      column1: 'Row',
      column2: 2,
      column3: 'Row2'
    }];

    const columns = [{
      title: 'Column1',
      dataIndex: 'column1',
      key: 'column1'
    }, {
      title: 'Column2',
      dataIndex: 'column2',
      key: 'column2'
    }, {
      title: 'Column3',
      dataIndex: 'column3',
      key: 'column3'
    }];

    return (
      <div className={styles.container}>
        <Table
          bordered
          size="small"
          dataSource={dataSource}
          columns={columns}
          pagination={false}
        />
      </div>
    );
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
      <div className={classNames(styles.panel, 'cp-panel')}>
        <div className={classNames('cp-panel-card', styles.card)}>
          <div className="cp-panel-card-title">
            Card title
          </div>
          <div className="cp-panel-card-sub-text">
            Card content
          </div>
        </div>
        <div className={classNames('cp-panel-card cp-card-service', styles.card)}>
          <div className="cp-panel-card-title">
            Service card title
          </div>
          <div className="cp-panel-card-sub-text">
            Service card content
          </div>
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
      <Select
        className={styles.input}
        placeholder="Select"
      >
        <Select.Option value="option1">
          Option 1
        </Select.Option>
        <Select.Option value="option2">
          Option 2
        </Select.Option>
      </Select>
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

  renderNavigation = () => (
    <div className={styles.container}>
      <div className={`cp-navigation-panel ${styles.navigationPanelPreview}`}>
        <div
          className={classNames(
            'cp-navigation-menu-item',
            {selected: this.state.navigationSelected.home}
          )}
          onClick={() => this.setState({
            navigationSelected: {
              home: true,
              run: false,
              search: false
            }
          })}
        >
          <Icon type="home" />
        </div>
        <div
          className={classNames(
            'cp-navigation-menu-item cp-runs-menu-item active',
            {selected: this.state.navigationSelected.run}
          )}
          onClick={() => this.setState({
            navigationSelected: {
              home: false,
              run: true,
              search: false
            }
          })}
        >
          <Icon type="play-circle" />
        </div>
        <div
          className={classNames(
            'cp-navigation-menu-item',
            {selected: this.state.navigationSelected.search}
          )}
          onClick={() => this.setState({
            navigationSelected: {
              home: false,
              run: false,
              search: true
            }
          })}
        >
          <Icon type="search" />
        </div>
      </div>
    </div>
  );

  renderMain = () => (
    <div className={styles.container}>
      <div className={classNames('ant-layout', styles.mainLayout)}>
        <div className={classNames('cp-panel', styles.mainPanel)}>
          <div className={styles.mainContent}>Content</div>
        </div>
      </div>
    </div>
  )

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
      case sectionNames.main:
        return this.renderMain();
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
