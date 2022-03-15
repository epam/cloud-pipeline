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
import {inject, observer} from 'mobx-react';
import {Button, Input, Dropdown, Menu} from 'antd';
import classNames from 'classnames';

import GitIgnore from '../utils/git-ignore-utils';
import styles from './git-ignore-edit-form.css';

function saveCursorPosition (ctrl, pos) {
  if (ctrl.setSelectionRange) {
    ctrl.focus();
    ctrl.setSelectionRange(pos, pos);
  } else if (ctrl.createTextRange) {
    const range = ctrl.createTextRange();
    range.collapse(true);
    range.moveEnd('character', pos);
    range.moveStart('character', pos);
    range.select();
  }
}

@inject((stores, props) => {
  return {
    gitIgnore: GitIgnore.getGitIgnore({content: props.content})
  };
})

@observer
class GitIgnoreEditForm extends React.Component {

  static ruleUID = 0;
  static getRuleUID = () => {
    GitIgnoreEditForm.ruleUID += 1;
    return GitIgnoreEditForm.ruleUID;
  };

  state = {
    originalContent: (this.props.gitIgnore.content || []).map(v => ({key: v, value: v})),
    modifiedContent: (this.props.gitIgnore.content || []).map(v => ({key: v, value: v}))
  }

  isSelectedInputRefExists = () => {
    return (
      this.selectedInputRef &&
      this.selectedInputRef.refs &&
      this.selectedInputRef.refs.input &&
      this.selectedInputRef.refs.input.value
    );
  }
  componentDidMount () {
    if (this.isSelectedInputRefExists() && this.props.editMode) {
      saveCursorPosition(
        this.selectedInputRef.refs.input,
        this.selectedInputRef.refs.input.value.length
      );
    }
  }

  componentDidUpdate () {
    if (this.isSelectedInputRefExists() && this.props.editMode) {
      saveCursorPosition(
        this.selectedInputRef.refs.input,
        this.selectedInputRef.refs.input.value.length
      );
    }
  }

  get rules () {
    return [{
      title: 'Ignore entire folder',
      type: 'folder(s)'
    }, {
      title: 'Ignore file',
      type: 'file(s)'
    }, {
      title: 'Ignore files with specific extension',
      type: 'extension'
    }];
  }

  get groupedRules () {
    return this.state.modifiedContent.reduce((rules, item) => {
      if (this.isFolderIgnoreRule(item.value) || (item.type === 'folder(s)' && !item.value)) {
        rules.folders.push(item);
      } else if (this.isExtensionRule(item.value) || (item.type === 'extension' && !item.value)) {
        rules.extension.push(item);
      } else if (this.isFileIgnoreRule(item.value) || (item.type === 'file(s)' && !item.value)) {
        rules.files.push(item);
      } else {
        rules.other.push(item);
      }
      return rules;
    }, {folders: [], files: [], extension: [], other: []});
  }

  get newTextContent () {
    return this.state.modifiedContent.map(v => v.value).join('\n');
  }

  renderRulesMenu () {
    return (
      <Menu onClick={this.addRule}>
        {
          this.rules.map(rule => (
            <Menu.Item
              key={rule.type}
            >
              {rule.title}
            </Menu.Item>))
        }
      </Menu>);
  }

  isFileIgnoreRule (contentItem) {
    return /[-\w\d]*\.\w+/.test(contentItem);
  }

  isFolderIgnoreRule (contentItem) {
    return /^\/?([-\w\d_+*]+\/?)+$/.test(contentItem);
  }

  isExtensionRule (contentItem) {
    return /^\*\.\w+/.test(contentItem);
  }

  renderInputItem = (value) => {
    return (
      <div
        key={value.key}
        className={classNames(
          styles.inputContainer
        )}>
        <Input
          ref={(element) => {
            if (value.editing) {
              this.selectedInputRef = element;
            }
          }}
          value={value.value}
          className={classNames(
            styles.ruleInput,
            {'cp-gitignore-rule-changed': value.changed}
          )}
          onChange={(e) => this.onEditRule(value.key, e.target)}
          placeholder={value.type ? `Enter rule for ${value.type} to ignore it` : ''}
          autoFocus={!value.value}
        />
        <Button
          type="danger"
          icon="delete"
          onClick={() => this.onDeleteRule(value.key)}
        />
      </div>
    );
  }

  onEditRule = (key, input) => {
    const {originalContent} = this.state;
    const modifiedInputIndex = this.state.modifiedContent.findIndex(item => item.key === key);
    const modifiedContent = this.state.modifiedContent.map(o => {
      o.editing = false;
      return o;
    });
    if (modifiedInputIndex > -1) {
      modifiedContent[modifiedInputIndex].value = input.value;
      modifiedContent[modifiedInputIndex].changed = !originalContent[modifiedInputIndex] ||
      originalContent[modifiedInputIndex].value !== input.value;
      modifiedContent[modifiedInputIndex].editing = true;
      this.setState({
        modifiedContent
      });
    }
    this.props.onChange && this.props.onChange(this.newTextContent);
  }

  onDeleteRule = (key) => {
    const modifiedContent = this.state.modifiedContent.filter(v => v.key !== key);
    this.setState({
      modifiedContent
    }, () => this.props.onChange(this.newTextContent));
  }

  addRule = ({key}) => {
    const modifiedContent = this.state.modifiedContent.map(o => {
      o.editing = false;
      return o;
    });
    this.setState({
      modifiedContent: [...modifiedContent, {
        type: key,
        value: '',
        key: `${key}${GitIgnoreEditForm.getRuleUID()}`,
        changed: true,
        editing: true
      }]
    });
  }

  renderUneditableView = (item) => {
    return (
      <li
        className={classNames(styles.rule, 'cp-gitignore-rule')}
        key={item.key}
      >
        {item.value}
      </li>
    );
  }

  render () {
    const {editMode} = this.props;
    return (
      <div className={styles.rulesContainer}>
        {
          Object.entries(this.groupedRules)
            .filter(([, items]) => items.length)
            .map(([type, items]) => {
              return (
                <div
                  key={type}
                  className={classNames(
                    styles.ruleContainer,
                    'cp-gitignore-rule-container'
                  )}>
                  <h3 className={styles.title}>Ignore {type} below</h3>
                  {items.map(item => editMode
                    ? this.renderInputItem(item)
                    : this.renderUneditableView(item))
                  }
                </div>
              );
            })
        }
        <div className={styles.buttonContainer}>
          <Dropdown.Button
            onClick={this.addRule}
            overlay={this.renderRulesMenu()}
          >
            ADD A RULE
          </Dropdown.Button>
        </div>
      </div>);
  }
}

export default GitIgnoreEditForm;
