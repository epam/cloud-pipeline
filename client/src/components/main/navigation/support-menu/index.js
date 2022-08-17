/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import {computed} from 'mobx';
import SupportMenuItem from './SupportMenuItem';
import styles from './SupportMenu.css';

const DEFAULT_MENU_ITEM = {
  icon: 'customer-service',
  entryName: 'defaultMenuItem'
};

@inject('uiNavigation')
@observer
class SupportMenu extends React.Component {
  static propTypes = {
    containerClassName: PropTypes.string,
    itemClassName: PropTypes.string,
    containerStyle: PropTypes.object,
    itemStyle: PropTypes.object
  };

  state = {
    supportModalVisible: null
  }

  @computed
  get template () {
    const {uiNavigation} = this.props;
    const {supportTemplate} = uiNavigation || {};
    let template = [];
    if (uiNavigation && uiNavigation.loaded) {
      if (
        typeof supportTemplate === 'object' &&
        supportTemplate !== null
      ) {
        template = Object.entries(supportTemplate)
          .map(([entryName, entryValue]) => {
            if (typeof entryValue === 'string') {
              return {
                entryName,
                icon: DEFAULT_MENU_ITEM.icon,
                content: entryValue
              };
            } else if (typeof entryValue === 'object') {
              const {
                icon = DEFAULT_MENU_ITEM.icon,
                content,
                url,
                target,
                action,
                hint = entryName
              } = entryValue;
              return {
                entryName,
                icon,
                content,
                url,
                target,
                action,
                hint
              };
            }
            return undefined;
          })
          .filter(Boolean)
          .filter(o => o.content || o.url || o.action);
      } else if (typeof supportTemplate === 'string') {
        template = [{
          entryName: DEFAULT_MENU_ITEM.entryName,
          icon: DEFAULT_MENU_ITEM.icon,
          content: uiNavigation.supportTemplate
        }]
          .filter(o => o.content || o.url || o.action);
      }
    }
    return template;
  }

  handleSupportModalVisible = entryName => visible => {
    this.setState({supportModalVisible: visible ? entryName : null});
  };

  renderMenuItem = (menuItem) => {
    const {
      content,
      icon,
      entryName,
      action,
      url,
      target,
      hint
    } = menuItem || {};
    const {
      itemClassName,
      itemStyle
    } = this.props;
    const {supportModalVisible} = this.state;
    if ((!content && !url && !action) || !icon) {
      return null;
    }
    return (
      <div key={entryName}>
        <SupportMenuItem
          className={itemClassName}
          entryName={entryName}
          style={itemStyle}
          visible={supportModalVisible === entryName}
          onVisibilityChanged={this.handleSupportModalVisible(entryName)}
          content={content}
          icon={icon}
          action={action}
          target={target}
          url={url}
          hint={hint}
        />
      </div>
    );
  };

  render () {
    const {
      containerStyle,
      containerClassName
    } = this.props;
    if (!this.template) {
      return null;
    }
    return (
      <div
        style={containerStyle}
        className={classNames(
          styles.container,
          containerClassName
        )}
      >
        {this.template.map(this.renderMenuItem)}
      </div>
    );
  }
}

export default SupportMenu;
