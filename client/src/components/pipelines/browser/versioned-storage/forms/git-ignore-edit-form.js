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
    modifiedContent: (this.props.gitIgnore.content || []).map((v, i) => ({id: i, value: v})),
    currentID: null,
    currentSection: null,
    currentValue: null
  }

  get rulesMenu () {
    return [{
      title: 'Ignore entire folder',
      section: 'folder'
    }, {
      title: 'Ignore file',
      section: 'file'
    }, {
      title: 'Ignore files with specific extension',
      section: 'extension'
    }];
  }

  get rules () {
    return this.state.modifiedContent.map((item) => {
      if (!this.isCurrentItem(item.id)) {
        if (this.isFolderIgnoreRule(item.value) || (item.section === 'folder' && !item.value)) {
          return ({...item, section: 'folder'});
        } else if (this.isExtensionRule(item.value) || (item.section === 'extension' && !item.value)) {
          return ({...item, section: 'extension'});
        } else if (this.isFileIgnoreRule(item.value) || (item.section === 'file' && !item.value)) {
          return ({...item, section: 'file'});
        } else {
          return item;
        }
      } else {
        const {currentValue, currentSection} = this.state;
        return ({
          ...item,
          value: currentValue,
          section: currentSection
        });
      }
    });
  }

  get groupedRules () {
    return this.rules.reduce((rules, item) => {
      if (item.section && item.section === 'folder') {
        rules.folders.push(item);
      } else if (item.section && item.section === 'extension') {
        rules.extension.push(item);
      } else if (item.section && item.section === 'file') {
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

  isCurrentItem (id) {
    return this.state.currentID === id;
  }

  isFileIgnoreRule (contentItem) {
    return /[-\w\d]*\.\w+/.test(contentItem);
  }

  isFolderIgnoreRule (contentItem) {
    return /^\/?([-\w\d_+*]+\/?)+$/.test(contentItem);
  }

  isExtensionRule (contentItem) {
    return /\/?\*\.\w+/.test(contentItem);
  }

  onEditRule = (item, input) => {
    const {id, section} = item;
    const modifiedValue = input.value;
    this.setState({
      currentID: id,
      currentValue: modifiedValue,
      currentSection: section
    }, () => this.props.onChange(this.newTextContent));
  }

  onDeleteRule = (id) => {
    const modifiedContent = this.state.modifiedContent.filter(v => v.id !== id);
    this.setState({
      modifiedContent
    }, () => this.props.onChange(this.newTextContent));
  }

  onAddRule = ({key}) => {
    this.setState({
      currentID: `${key}${GitIgnoreEditForm.getRuleUID()}`,
      currentValue: '',
      currentSection: key,
      modifiedContent: [...this.state.modifiedContent, {
        section: key,
        value: '',
        id: `${key}${GitIgnoreEditForm.getRuleUID()}`,
        changed: true
      }]
    });
  }

  onResetCurrentItemAndUpdate () {
    const {currentID, currentValue, modifiedContent} = this.state;
    if (currentID !== null) {
      this.setState({
        currentID: null,
        currentSection: null,
        currentValue: null,
        modifiedContent: modifiedContent
          .map(v => v.id === currentID ? ({...v, value: currentValue}) : v)
      });
    }
  }

  renderUneditableView = (item) => {
    return (
      <li
        className={classNames(styles.rule, 'cp-gitignore-rule')}
        key={item.id}
      >
        {item.value}
      </li>
    );
  }

  renderInputItem ({
    id,
    value,
    section,
    changed
  }) {
    return (
      <div
        key={id}
        className={classNames(
          styles.inputContainer
        )}>
        <Input
          value={value}
          className={classNames(
            styles.ruleInput,
            {'cp-gitignore-rule-changed': changed}
          )}
          onChange={(e) => this.onEditRule({id, section}, e.target)}
          onBlur={() => this.onResetCurrentItemAndUpdate()}
          placeholder={section ? `Enter rule for ${section} to ignore it` : ''}
          autoFocus={!value}
        />
        <Button
          type="danger"
          icon="delete"
          onClick={() => this.onDeleteRule(id)}
        />
      </div>
    );
  }

  renderRulesMenu () {
    return (
      <Menu onClick={this.onAddRule}>
        {
          this.rulesMenu.map(rule => (
            <Menu.Item
              key={rule.section}
            >
              {rule.title}
            </Menu.Item>))
        }
      </Menu>);
  }

  render () {
    const {editMode} = this.props;
    return (
      <div className={styles.rulesContainer}>
        <div className={styles.buttonContainer}>
          <Dropdown.Button
            onClick={this.onAddRule}
            overlay={this.renderRulesMenu()}
          >
            ADD A RULE
          </Dropdown.Button>
        </div>
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
                  <h3 className={styles.title}>{`Ignore ${type}(s)`}</h3>
                  {
                    items.map(item => editMode
                      ? this.renderInputItem(item)
                      : this.renderUneditableView(item))
                  }
                </div>
              );
            })
        }
      </div>);
  }
}

export default GitIgnoreEditForm;
