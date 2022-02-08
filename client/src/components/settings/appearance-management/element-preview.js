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
import {
  Alert,
  Button,
  Checkbox,
  Icon,
  Input,
  Select,
  Spin,
  Table,
  Tabs
} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import classNames from 'classnames';
import {sectionNames} from './utilities/variable-sections';
import StatusIcon from '../../special/run-status-icon';
import AWSRegionTag from '../../special/AWSRegionTag';
import styles from './element-preview.css';

const Divider = () => (
  <div
    className={
      classNames(
        'cp-divider',
        'horizontal',
        styles.divider
      )
    }
  >
    {'\u00A0'}
  </div>
);

const KeyValueTag = ({keyName, value}) => (
  <div
    className={
      classNames(
        styles.keyValue
      )
    }
  >
    <div
      className="cp-library-metadata-item-key"
    >
      {keyName}
    </div>
    <div
      className="cp-library-metadata-item-value"
    >
      {value}
    </div>
  </div>
);

const TextPreview = ({className, style}) => (
  <div
    style={style}
    className={classNames(styles.textPreview, className)}
  >
    <span>
      Default text
    </span>
    <span className="cp-primary">
      link or action
    </span>
    <span className="cp-accent">
      accented
    </span>
    <span className="cp-text-not-important">
      faded
    </span>
    <span className="cp-disabled">
      disabled
    </span>
  </div>
);

const CardPreview = (
  {
    className,
    header,
    actions,
    title
  }
) => (
  <div
    className={
      classNames(
        className,
        styles.card
      )
    }
    style={{
      margin: 10,
      position: 'relative'
    }}
  >
    <div className={styles.cardContent}>
      <div className={styles.content}>
        {
          header && (
            <div
              className="cp-dashboard-panel-card-header"
              style={{padding: '5px 10px'}}
            >
              {
                title ? `${title} header` : 'Header'
              }
            </div>
          )
        }
        {
          !header && title && (
            <div style={{padding: '5px 10px'}}>
              {title}
            </div>
          )
        }
        <TextPreview
          className={styles.content}
          style={{margin: 10}}
        />
      </div>
      {
        actions && (
          <div
            className={
              classNames(
                'cp-panel-card-actions',
                'hovered',
                styles.cardActions
              )
            }
          >
            <div
              className={
                classNames(
                  'cp-panel-card-actions-background',
                  styles.cardActionsBackground
                )
              }
            >
              {'\u00A0'}
            </div>
            <div
              className={styles.cardActionsButtons}
            >
              <span className="cp-primary">Hovered</span>
              <span className="cp-danger">Actions</span>
            </div>
          </div>
        )
      }
    </div>
  </div>
);

const NavigationPanel = ({impersonated}) => (
  <div
    className={
      classNames(
        'ant-layout-sider',
        styles.navigationPanelPreview
      )
    }
  >
    <div
      className={
        classNames(
          'cp-navigation-panel',
          {impersonated}
        )
      }
    >
      <div
        className="cp-navigation-menu-item"
      >
        <div className="cp-navigation-item-logo">
          {'\u00A0'}
        </div>
      </div>
      <div
        className={
          classNames(
            'cp-navigation-menu-item',
            'selected'
          )
        }
      >
        <Icon type="home" />
      </div>
      <div
        className={
          classNames(
            'cp-navigation-menu-item',
            'cp-runs-menu-item',
            'active'
          )
        }
      >
        <Icon type="play-circle" />
      </div>
      <div
        className={classNames('cp-navigation-menu-item')}
      >
        <Icon type="search" />
      </div>
    </div>
  </div>
);

const PreviewContainer = ({children}) => (
  <div
    className={styles.container}
  >
    <div
      className={
        classNames(
          'ant-layout',
          'theme-preview-bordered'
        )
      }
      style={{
        flex: '0 0 auto'
      }}
    >
      <div
        className={
          classNames(
            'cp-panel',
            'cp-panel-borderless',
            styles.previewContainer
          )
        }
      >
        {children}
      </div>
    </div>
  </div>
);

class ElementPreview extends React.Component {
  state = {
    navigationSelected: {
      home: false,
      run: false,
      search: false
    }
  };

  renderButtons = () => (
    <PreviewContainer>
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
    </PreviewContainer>
  );

  renderTables = () => {
    const dataSource = [
      {
        key: '1',
        column1: 'run-odd',
        column2: 'library/centos'
      },
      {
        key: '2',
        column1: 'run-even',
        column2: 'library/ubuntu'
      },
      {
        key: '3',
        column1: 'run-odd',
        column2: 'library/centos'
      },
      {
        key: '4',
        column1: 'run-even',
        column2: 'library/centos',
        selected: true
      }
    ];

    const columns = [
      {
        title: 'Run',
        dataIndex: 'column1',
        key: 'column1',
        render: (title, record) => (
          <div>
            <Checkbox checked={record.selected}>
              {title}
            </Checkbox>
          </div>
        )
      },
      {
        title: 'Docker image',
        dataIndex: 'column2',
        key: 'column2'
      }
    ];

    return (
      <PreviewContainer>
        <div style={{marginBottom: 5}}>
          <Menu>
            <MenuItem style={{minWidth: 180}}>Pipeline</MenuItem>
            <MenuItem
              style={{minWidth: 180}}
              className="rc-menu-item-active"
            >
              Storage (hovered)
            </MenuItem>
            <MenuItem style={{minWidth: 180}}>Folder</MenuItem>
            <MenuItem
              style={{minWidth: 180}}
              className="ant-select-dropdown-menu-item-selected"
            >
              Configuration (selected)
            </MenuItem>
          </Menu>
        </div>
        <Table
          style={{width: '100%'}}
          bordered
          size="small"
          dataSource={dataSource}
          columns={columns}
          rowClassName={(record) => classNames(
            'cp-even-odd-element',
            {
              'cp-table-element-selected': record.selected
            },
            'cp-table-element-selected-no-bold'
          )}
          pagination={false}
        />
      </PreviewContainer>
    );
  };

  renderForms = () => (
    <PreviewContainer>
      <div className={styles.inputWrapper}>
        <Input
          value="Input text"
          onChange={() => {}}
          addonBefore={(<Icon type="export" />)}
        />
      </div>
      <Input
        value={undefined}
        onChange={() => {}}
        placeholder="placeholder text"
        className={styles.input}
      />
      <Select
        allowClear
        className={styles.input}
        placeholder="Select"
        value="option1"
        onChange={() => {}}
        getPopupContainer={node => node.parentNode}
      >
        <Select.Option value="option1">
          Option 1
        </Select.Option>
        <Select.Option value="option2">
          Option 2
        </Select.Option>
      </Select>
      <Input.Search
        value="Search..."
        onChange={() => {}}
        className={styles.input}
      />
      <Input
        placeholder="disabled"
        disabled
        className={styles.input}
      />
    </PreviewContainer>
  );

  renderAlerts = () => (
    <PreviewContainer>
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
    </PreviewContainer>
  );

  renderNavigation = () => (
    <div
      className={styles.container}
    >
      <div
        className={
          classNames(
            styles.horizontalContainer,
            'ant-layout',
            'theme-preview-bordered'
          )
        }
        style={{flex: '0 0 auto'}}
      >
        <NavigationPanel />
        <NavigationPanel impersonated />
      </div>
    </div>
  );

  renderMain = () => (
    <div className={styles.container}>
      <div
        className={
          classNames(
            'ant-layout',
            styles.mainLayout,
            'theme-preview-bordered'
          )
        }
        style={{flex: '0 0 auto'}}
      >
        <Tabs
          activeKey="tab1"
          style={{margin: '0 10px'}}
        >
          <Tabs.TabPane key="tab1" tab="Active tab" />
          <Tabs.TabPane key="tab2" tab="Another tab" />
        </Tabs>
        <div className={classNames('cp-panel', styles.mainPanel)}>
          <div className={styles.elementName}>Panel</div>
          <TextPreview style={{margin: 10}} />
          <CardPreview
            className="cp-panel-card"
            title="Card"
            actions
          />
          <CardPreview
            className="cp-panel-card"
            title="Card"
            header
          />
          <CardPreview
            className={classNames('cp-panel-card cp-card-service')}
            title="Service card"
            actions
          />
          <CardPreview
            className={classNames('cp-panel-card cp-card-service')}
            title="Service card"
            header
          />
        </div>
      </div>
    </div>
  );

  renderColors = () => (
    <PreviewContainer>
      <div className={styles.job}>
        <StatusIcon status="RUNNING" />
        <span>Running job</span>
      </div>
      <div className={styles.job}>
        <StatusIcon status="SUCCESS" />
        <span>Finished job</span>
      </div>
      <div className={styles.job}>
        <StatusIcon status="STOPPED" />
        <span>Stopped job</span>
      </div>
      <div className={styles.job}>
        <StatusIcon status="FAILURE" />
        <span>Failed job</span>
      </div>
      <Divider />
      <div className={styles.tags}>
        <div
          className={
            classNames(
              styles.tag,
              'cp-sensitive'
            )
          }
        >
          SENSITIVE
        </div>
        <div
          className={
            classNames(
              styles.tag,
              'cp-sensitive-tag'
            )
          }
        >
          SENSITIVE
        </div>
        <div
          className={
            classNames(
              styles.tag,
              'cp-nfs-storage-type'
            )
          }
        >
          NFS
        </div>
      </div>
      <Divider />
      <Spin>
        <div>
          Loading indicator...
        </div>
      </Spin>
      <Divider />
      <div>
        <span className="cp-search-highlight">...this is</span>
        <span className="cp-search-highlight-text" style={{margin: '0px 5px'}}>
          highlighted
        </span>
        <span className="cp-search-highlight">text...</span>
      </div>
      <Divider />
      <div>
        <KeyValueTag keyName="Type" value="Project" />
        <KeyValueTag keyName="Owner" value="User name" />
      </div>
    </PreviewContainer>
  );

  renderOther = () => (
    <PreviewContainer>
      <div>
        <div>
          Default icons:
        </div>
        <div
          className={
            classNames(
              'cp-panel-card',
              'borderless',
              styles.iconsContainer
            )
          }
          style={{
            margin: 0
          }}
        >
          <AWSRegionTag provider="AWS" className="cp-icon-larger" />
          <AWSRegionTag provider="GCP" className="cp-icon-larger" />
          <AWSRegionTag provider="AZURE" className="cp-icon-larger" />
        </div>
      </div>
      <div
        style={{marginTop: 5}}
        className={classNames('cp-divider', 'top')}
      >
        <div>
          Contrasted icons:
        </div>
        <div
          className={
            classNames(
              'ant-tooltip-inner',
              styles.iconsContainer
            )
          }
        >
          <AWSRegionTag provider="AWS" className="cp-icon-larger" />
          <AWSRegionTag provider="GCP" className="cp-icon-larger" />
          <AWSRegionTag provider="AZURE" className="cp-icon-larger" />
        </div>
      </div>
    </PreviewContainer>
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
      case sectionNames.navigation:
        return this.renderNavigation();
      case sectionNames.input:
        return this.renderForms();
      case sectionNames.main:
        return this.renderMain();
      case sectionNames.colors:
        return this.renderColors();
      case sectionNames.other:
        return this.renderOther();
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
