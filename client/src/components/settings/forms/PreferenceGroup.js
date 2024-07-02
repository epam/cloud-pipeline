/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {
  Button,
  Checkbox,
  Form,
  Icon,
  Input,
  Row,
  Select
} from 'antd';
import CodeEditor from '../../special/CodeEditor';
import highlightText from '../../special/highlightText';
import {MANAGEMENT_SECTION} from '../user-profile/appearance';
import {ThemesPreferenceModes, ThemesPreferenceName} from '../../../themes';
import {sshThemesList} from '../../special/metadata/special/ssh-theme-select';
import styles from './PreferenceGroup.css';

const formatJson = (string, presentation = true, catchError = true) => {
  if (!string) {
    return string;
  }
  try {
    return JSON.stringify(JSON.parse(string), null, presentation ? ' ' : undefined);
  } catch (e) {
    if (!catchError) {
      throw e;
    }
  }
  return string;
};

@Form.create()
@observer
export default class PreferenceGroup extends React.Component {
  static propTypes = {
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    group: PropTypes.string,
    preferences: PropTypes.array,
    search: PropTypes.string,
    router: PropTypes.object
  };

  checkPreferenceModified = (name) => {
    const [preference] = this.props.preferences.filter(p => p.name === name);
    const formPreference = this.props.form.getFieldValue(name.replace(/\./g, '-'));
    if (!preference && !formPreference) {
      return false;
    }
    if (!preference && formPreference) {
      return true;
    }
    if (preference && !formPreference) {
      return true;
    }
    const initialValue = preference.value || '';
    const value = formPreference.value || '';
    return initialValue !== value || preference.visible !== formPreference.visible;
  };

  @computed
  get modified () {
    if (!this.props.preferences || this.props.preferences.length === 0) {
      return false;
    }
    for (let i = 0; i < this.props.preferences.length; i++) {
      if (this.checkPreferenceModified(this.props.preferences[i].name)) {
        return true;
      }
    }
    return false;
  }

  handleSubmit = (e) => {
    e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        const payload = [];
        for (let property in values) {
          if (values.hasOwnProperty(property) &&
            this.checkPreferenceModified(values[property].name)) {
            payload.push(values[property]);
          }
        }
        this.props.onSubmit && this.props.onSubmit(payload);
      }
    });
  };

  getPreferenceRules = (type) => {
    const rules = [];
    switch (type) {
      case 'INTEGER':
      case 'LONG':
        rules.push({
          validator: (rule, {value}, callback) => {
            if (isNaN(value)) {
              callback('Please enter a valid number');
            }
            callback();
          }
        });
        break;
      case 'OBJECT':
        rules.push({
          validator: (rule, {value}, callback) => {
            try {
              formatJson(value, false, false);
            } catch (e) {
              callback(e.toString());
              return;
            }
            callback();
          }
        });
        break;
    }
    return rules;
  };

  render () {
    if (!this.props.group) {
      return null;
    }
    const {resetFields, getFieldDecorator} = this.props.form;
    return (
      <div style={{width: '100%', flex: 1, display: 'flex', flexDirection: 'column'}}>
        <div style={{flex: 1, width: '100%', overflowY: 'auto'}}>
          <Form className="edit-preferences-form" layout="horizontal">
            {
              (this.props.preferences || []).map((preference, index, array) => {
                return (
                  <Form.Item
                    key={preference.name}
                    style={{
                      marginBottom: 0
                    }}
                    className={`edit-preferences-${preference.name}-container`}>
                    {getFieldDecorator(preference.name.replace(/\./g, '-'), {
                      initialValue: preference,
                      rules: this.getPreferenceRules(preference.type)
                    })(
                      <PreferenceInput
                        search={this.props.search}
                        isEven={index % 2 === 0}
                        isLast={index === array.length - 1}
                        disabled={this.props.pending}
                        router={this.props.router}
                      />
                    )}
                  </Form.Item>
                );
              })
            }
          </Form>
        </div>
        <Row className={styles.actions} type="flex" justify="end">
          <Button
            id="edit-preference-form-cancel-button"
            disabled={!this.modified}
            size="small"
            onClick={() => resetFields()}>Revert</Button>
          <Button
            id="edit-preference-form-ok-button"
            disabled={!this.modified}
            type="primary"
            size="small"
            onClick={this.handleSubmit}>Save</Button>
        </Row>
      </div>
    );
  }

  resetFormFields = () => {
    this.props.form && this.props.form.resetFields();
  };

  componentDidUpdate (prevProps) {
    if (prevProps.group !== this.props.group) {
      this.resetFormFields();
    }
  }
}

@inject('themes')
@observer
class PreferenceInput extends React.Component {
  static propTypes = {
    value: PropTypes.shape({
      name: PropTypes.string,
      description: PropTypes.string,
      value: PropTypes.oneOfType([PropTypes.string, PropTypes.number, PropTypes.bool]),
      type: PropTypes.oneOf(['STRING', 'INTEGER', 'LONG', 'BOOLEAN', 'OBJECT', 'FLOAT']),
      visible: PropTypes.bool,
      preferenceGroup: PropTypes.string,
      createdDate: PropTypes.string
    }),
    onChange: PropTypes.func,
    disabled: PropTypes.bool,
    isEven: PropTypes.bool,
    isLast: PropTypes.bool,
    search: PropTypes.string,
    router: PropTypes.object
  };

  state = {
    value: null,
    visible: true
  };

  onChange = () => {
    this.props.onChange && this.props.onChange({
      name: this.props.value.name,
      type: this.props.value.type,
      preferenceGroup: this.props.value.preferenceGroup,
      createdDate: this.props.value.createdDate,
      value: this.state.value,
      visible: this.state.visible,
      description: this.props.value.description
    });
  };

  onValueChange = (value) => {
    this.setState({
      value: value
    }, this.onChange);
  };

  onVisibilityValueChanged = (visibility) => {
    this.setState({
      visible: visibility
    }, this.onChange);
  };

  editor;

  initializeEditor = (editor) => {
    this.editor = editor;
  };

  renderInput = () => {
    const {
      router,
      themes,
      value
    } = this.props;
    const themesManagementPreference = router &&
      ThemesPreferenceName === value.name &&
      themes &&
      themes.mode === ThemesPreferenceModes.payload &&
      value.value;
    let showThemesManagementRedirection = themesManagementPreference;
    if (themesManagementPreference) {
      try {
        const o = JSON.parse(value.value);
        showThemesManagementRedirection = o && Array.isArray(o);
      } catch (e) {
        showThemesManagementRedirection = false;
      }
    }
    if (
      showThemesManagementRedirection
    ) {
      return (
        <div>
          <span>You can manage UI themes at</span>
          <a
            onClick={() => router.push(`/settings/profile/appearance/${MANAGEMENT_SECTION}`)}
            style={{
              marginLeft: 5,
              marginRight: 5,
              fontWeight: 'bold'
            }}
          >
            <span>
              My Profile
            </span>
            <Icon type="caret-right" />
            <span>
              Appearance
            </span>
          </a>
          <span>section</span>
        </div>
      );
    }
    if (value.name === 'ui.ssh.theme') {
      return (
        <Select
          value={this.state.value}
          onChange={this.onValueChange}
          disabled={this.props.disabled}
          size="small"
        >
          {Object.entries(sshThemesList)
            .map(([value, text]) => (
              <Select.Option
                key={value}
                value={value}
              >
                {text}
              </Select.Option>
            ))}
        </Select>
      );
    } else if (this.props.value.type === 'BOOLEAN') {
      return (
        <Checkbox
          style={{lineHeight: 1, marginLeft: 2}}
          checked={`${this.state.value}` === 'true'}
          onChange={e => this.onValueChange(e.target.checked.toString())}
          disabled={this.props.disabled}
        >
          Enabled
        </Checkbox>
      );
    } else if (this.props.value.type === 'OBJECT') {
      return (
        <CodeEditor
          ref={this.initializeEditor}
          className={styles.codeEditor}
          language="application/json"
          onChange={code => this.onValueChange(code)}
          lineWrapping
          defaultCode={this.props.value.value}
        />
      );
    }
    return (
      <Input
        disabled={this.props.disabled}
        size="small"
        value={this.state.value}
        onChange={e => this.onValueChange(e.target.value)} />
    );
  };

  render () {
    if (!this.props.value) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            styles.preferenceRow,
            {
              'cp-even-row': this.props.isEven
            }
          )
        }
        style={{width: '100%'}}>
        <Row type="flex" align="middle">
          <Icon
            className={
              classNames(
                {
                  'cp-text-not-important': !this.state.visible
                }
              )
            }
            type={this.state.visible ? 'eye' : 'eye-o'}
            onClick={() => !this.props.disabled && this.onVisibilityValueChanged(!this.state.visible)}
            style={{
              cursor: 'pointer',
              fontSize: 'large',
              marginRight: 5,
              marginBottom: 2
            }} />
          <span>
            {highlightText(this.props.value.name, this.props.search)}
          </span>
        </Row>
        <Row type="flex">
          {this.renderInput()}
        </Row>
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if ('value' in nextProps &&
      (nextProps.value.value !== this.state.value || nextProps.value.visible !== this.state.visible)) {
      const value = nextProps.value;
      this.setState({
        value: value.value,
        visible: value.visible
      }, () => {
        this.editor && this.editor.setValue(this.state.value);
      });
    }
  }

  componentDidMount () {
    this.setState({
      value: this.props.value ? this.props.value.value : null,
      visible: this.props.value ? this.props.value.visible : true
    });
  }
}
