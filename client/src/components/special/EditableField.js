/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Icon, Input, message, Tooltip} from 'antd';
import classNames from 'classnames';
import styles from './EditableField.css';

export default class EditableField extends React.Component {

  static propTypes = {
    text: PropTypes.string,
    displayText: PropTypes.oneOfType([PropTypes.string, PropTypes.object]),
    allowEpmty: PropTypes.bool,
    readOnly: PropTypes.bool,
    className: PropTypes.string,
    editClassName: PropTypes.string,
    style: PropTypes.object,
    editStyle: PropTypes.object,
    onSave: PropTypes.func,
    inputId: PropTypes.string,
    displayId: PropTypes.string
  };

  state = {
    edit: false,
    text: null,
    saving: false
  };

  onEnterEditMode = (e) => {
    if (e) {
      e.stopPropagation();
    }
    this.setState({
      text: this.props.text,
      edit: true
    });
  };

  onLeaveEditMode = (save) => (e) => {
    if (e) {
      e.stopPropagation();
    }
    if (save && this.state.text !== this.props.text) {
      if (this.props.allowEpmty !== undefined &&
        this.props.allowEpmty !== null &&
        !this.props.allowEpmty &&
        (!this.state.text || this.state.text.length === 0 || this.state.text.trim().length === 0)) {
        message.error('Field should not be empty', 5);
        return;
      }
      this.setState({
        saving: true
      }, async () => {
        if (this.props.onSave) {
          await this.props.onSave(this.state.text);
        }
        this.setState({
          text: this.props.text,
          edit: false,
          saving: false
        }, () => {
          this.control = null;
        });
      });
    } else {
      this.setState({
        text: this.props.text,
        edit: false,
        saving: false
      }, () => {
        this.control = null;
      });
    }
  };

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

  control;

  renderEditMode = () => {
    const options = {
    };
    const style = {
      border: '1px solid transparent',
      height: 31
    };
    if (this.props.editClassName) {
      options.className = this.props.editClassName;
    }
    if (this.props.editStyle) {
      options.style = Object.assign(style, this.props.editStyle);
    } else {
      options.style = style;
    }
    const ref = (control) => {
      if (!this.control && control && control.refs && control.refs.input) {
        this.control = control;
        control.refs.input.focus();
        this.moveCursorToEnd(control.refs.input);
      }
    };
    return (
      <div {...options}>
        <Input
          id={this.props.inputId}
          disabled={this.state.saving}
          ref={ref}
          value={this.state.text}
          onChange={(e) => this.setState({text: e.target.value})}
          onBlur={this.onLeaveEditMode(true)}
          onPressEnter={this.onLeaveEditMode(true)}
          onKeyDown={(e) => {
            if (e.key && e.key === 'Escape') {
              this.onLeaveEditMode(false)(e);
            }
          }} />
      </div>
    );
  };

  renderDisplayMode = () => {
    const options = {};
    const style = {};
    if (!this.props.readOnly) {
      options.onClick = this.onEnterEditMode;
    } else {
      style.cursor = 'default';
    }
    if (this.props.style) {
      options.style = Object.assign(style, this.props.style);
    } else {
      options.style = style;
    }
    options.className = classNames(
      this.props.className,
      styles.editableMode,
      {[styles.editableModeHovered]: !this.props.readOnly},
      {'cp-library-breadcrumbs-editable-field': !this.props.readOnly}
    );
    return (
      <Tooltip
        mouseEnterDelay={1.5}
        overlay={this.props.displayText || this.props.text}>
        <div {...options} >
          <span
            id={this.props.displayId}
            className={styles.editableModeOptions}
          >
            {this.props.displayText || this.props.text}
          </span>
          <span
            className={classNames(
              styles.editableFieldIcon,
              {'cp-library-breadcrumbs-editable-field-icon': !this.props.readOnly},
              {[styles.notDisplayed]: this.props.readOnly}
            )}
          >
            <Icon type="edit" style={{marginRight: 10}} />
          </span>
        </div>
      </Tooltip>
    );
  };

  render () {
    if (this.state.edit) {
      return this.renderEditMode();
    } else {
      return this.renderDisplayMode();
    }
  }

}
