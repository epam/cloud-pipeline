/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import {observer} from 'mobx-react';
import PropTypes from 'prop-types';
import {Row, Breadcrumb, Input} from 'antd';
import styles from './Navigation.css';

@observer
export default class DataStorageNavigation extends React.Component {
  static propTypes = {
    path: PropTypes.string,
    storage: PropTypes.object,
    navigate: PropTypes.func,
    navigateFull: PropTypes.func
  };

  state = {editable: false};

  navigate = (event, path) => {
    event.stopPropagation();
    if (this.props.navigate && this.props.storage) {
      this.props.navigate(this.props.storage.id, path);
    }
    this.setState({editable: false});
  };

  navigateFull = (event, path) => {
    event.stopPropagation();
    if (this.props.navigateFull) {
      this.props.navigateFull(path);
    }
    this.setState({editable: false});
  };

  getRootPath = () => {
    if (this.props.storage) {
      return `${this.props.storage.type.toLowerCase()}://${this.props.storage.path}`;
    }
    return '';
  };

  getCurrentFullPath = () => {
    if (this.props.path && this.props.path.length > 0) {
      return `${this.getRootPath()}/${this.props.path}`;
    } else {
      return this.getRootPath();
    }
  };

  getComponents = () => {
    const components = [];
    components.push({
      key: 'root',
      title: this.getRootPath(),
      canNavigate: this.props.path && this.props.path.length > 0,
      url: undefined
    });
    if (this.props.path && this.props.path.length > 0) {
      const parts = this.props.path.split('/');
      let path;
      for (let index = 0; index < parts.length; index++) {
        if (index === 0) {
          path = parts[index];
        } else {
          path += `/${parts[index]}`;
        }
        components.push({
          key: `path_${index}`,
          title: parts[index],
          canNavigate: parts.length - 1 > index,
          url: path
        });
      }
    }
    return components;
  };

  setEditableMode = (mode) => () => {
    if (!mode) {
      this.control = false;
    }
    this.setState({editable: mode});
  };

  initializeInput = (input) => {
    if (!this.control && input && input.refs.input) {
      input.refs.input.focus();
      this.control = input;
      this.moveCursorToEnd(input.refs.input);
    }
  };

  control;

  moveCursorToEnd = (el) => {
    if (typeof el.selectionStart === 'number') {
      el.selectionStart = el.selectionEnd = el.value.length;
    } else if (typeof el.createTextRange !== 'undefined') {
      el.focus();
      const range = el.createTextRange();
      range.collapse(false);
      range.select();
    }
  };

  render () {
    return (
      <Row
        className={styles.pathComponentsContainer}
        onClick={this.setEditableMode(true)}>
        {!this.state.editable &&
          <Breadcrumb style={{padding: 5}}>
            {this.getComponents().map(part => {
              if (part.canNavigate) {
                return (
                  <Breadcrumb.Item
                    className={styles.breadcrumbItem}
                    key={part.key}>
                    <a onClick={(event) => this.navigate(event, part.url)}>
                      {decodeURIComponent(part.title)}
                    </a>
                  </Breadcrumb.Item>
                );
              } else {
                return (
                  <Breadcrumb.Item
                    className={styles.breadcrumbItem}
                    key={part.key}>{decodeURIComponent(part.title)}</Breadcrumb.Item>
                );
              }
            })}
          </Breadcrumb>
        }
        {this.state.editable &&
          <Input
            ref={this.initializeInput}
            style={{width: '100%'}}
            onBlur={this.setEditableMode(false)}
            defaultValue={this.getCurrentFullPath()}
            onPressEnter={(event) => this.navigateFull(event, event.target.value)} />
        }
      </Row>
    );
  }
}
