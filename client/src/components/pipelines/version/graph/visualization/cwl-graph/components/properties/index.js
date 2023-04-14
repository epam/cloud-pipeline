/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {Icon} from 'antd';
import styles from './cwl-properties.css';

class CWLProperties extends React.PureComponent {
  state = {
    visible: undefined
  };

  openPropertiesPanel = (aKey) => (event) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    this.setState({visible: aKey});
  }

  closePropertiesPanel = () => this.setState({visible: false});

  renderContent = () => {
    const {
      visible
    } = this.state;
    const {
      properties = []
    } = this.props;
    if (properties.length === 0) {
      return null;
    }
    const propertiesConfig = properties
      .find((aProperties) => visible && aProperties.key === visible);
    if (!propertiesConfig) {
      return properties.map((config) => (
        <div
          key={config.key}
          className={
            classNames(
              styles.propertiesButton,
              'cp-panel-card'
            )
          }
          onClick={this.openPropertiesPanel(config.key)}
        >
          {config.buttonTitle || config.title}
        </div>
      ));
    }
    return (
      <div
        className={
          classNames(
            'cp-panel',
            styles.propertiesPanel
          )
        }
      >
        <div
          className={
            classNames(
              'cp-divider',
              'bottom',
              styles.propertiesPanelHeader
            )
          }
        >
          <span>{propertiesConfig.title}</span>
          <Icon
            type="close"
            className={styles.propertiesPanelHeaderCloseButton}
            onClick={this.closePropertiesPanel}
          />
        </div>
        <div
          className={styles.propertiesPanelContent}
        >
          {propertiesConfig.component}
        </div>
      </div>
    );
  };

  render () {
    const {
      className,
      style
    } = this.props;
    return (
      <div
        className={
          classNames(
            className,
            styles.propertiesContainer
          )
        }
        style={style}
      >
        {this.renderContent()}
      </div>
    );
  }
}

CWLProperties.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  title: PropTypes.string.isRequired,
  buttonTitle: PropTypes.string,
  properties: PropTypes.arrayOf(PropTypes.shape({
    key: PropTypes.string,
    title: PropTypes.string,
    buttonTitle: PropTypes.string,
    component: PropTypes.node
  }))
};

export default CWLProperties;
