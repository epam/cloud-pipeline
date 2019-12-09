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
import {
  Button,
  Icon,
  Input,
  InputNumber,
  Modal,
  Row
} from 'antd';
import QuotaThreshold from './quotas-threshold';
import styles from './quotas.css';

const ValidationFields = {
  actions: 'actions',
  name: 'name',
  value: 'value'
};

class QuotaTemplateEditDialog extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    isNew: PropTypes.bool,
    template: PropTypes.object,
    onRemove: PropTypes.func,
    onCancel: PropTypes.func,
    onSave: PropTypes.func
  };

  state = {
    actions: [],
    value: 0,

    modified: false,
    errors: {},
    disabled: false
  };

  componentDidMount () {
    this.createInitialState(this.props);
  }

  componentWillReceiveProps (nextProps, nextContext) {
    if (nextProps.visible && nextProps.visible !== this.props.visible) {
      this.createInitialState(nextProps);
    }
  }

  createInitialState = (props) => {
    const {template = {}} = props;
    const {actions = [], id, name, value = 0} = template || {};
    this.setState({actions, id, name, value, modified: false}, this.validate);
  };

  validateAction = (action) => {
    if (!action) {
      return null;
    }
    const {action: act, threshold} = action;
    if (isNaN(threshold) || threshold === 0) {
      return 'Threshold must be positive number';
    }
    if (!act) {
      return 'You should specify an action';
    }
    return null;
  };

  validate = () => {
    const {actions, name, value} = this.state;
    const errors = {};
    if (!name) {
      errors[ValidationFields.name] = 'Name must be set';
    }
    if (isNaN(value)) {
      errors[ValidationFields.value] = 'Value should be a positive number';
    } else if (value === 0) {
      errors[ValidationFields.value] = 'Value should be great then zero';
    }
    if (!actions || actions.length === 0) {
      errors[ValidationFields.actions] = 'At least 1 action must be set';
    } else {
      const actionErrors = actions.map(this.validateAction);
      if (actionErrors.filter(Boolean).length > 0) {
        errors[ValidationFields.actions] = actionErrors;
      }
    }
    this.setState({errors});
    return Object.keys(errors).length === 0;
  };

  onSaveClicked = () => {
    const {onSave} = this.props;
    const {actions, id, name, value} = this.state;
    const valid = this.validate();
    if (valid && onSave) {
      this.setState({disabled: true}, async () => {
        await onSave({actions, id, name, value});
        this.setState({disabled: false});
      });
    }
  };

  renderTemplateNameInput = () => {
    const {disabled, errors, name} = this.state;
    const error = errors[ValidationFields.name];
    return (
      <div className={styles.quotaInputContainer}>
        <span className={styles.label}>
          Name:
        </span>
        <Input
          disabled={disabled}
          className={`${styles.quotaInput} ${error ? styles.error : ''}`}
          value={name}
          onChange={e => this.setState({name: e.target.value, modified: true}, this.validate)}
        />
      </div>
    );
  };

  renderQuotaInput = () => {
    const {disabled, errors, value} = this.state;
    const error = errors[ValidationFields.value];
    return (
      <div className={styles.quotaInputContainer}>
        <span className={styles.label}>
          Quota:
        </span>
        <InputNumber
          disabled={disabled}
          className={`${styles.quotaInput} ${error ? styles.error : ''}`}
          value={value}
          onChange={e => this.setState({value: e, modified: true}, this.validate)}
          min={0}
        />
      </div>
    );
  };

  renderActions = () => {
    const {
      actions,
      disabled,
      errors,
      value
    } = this.state;
    const error = errors[ValidationFields.actions];
    const onRemove = (index) => () => {
      actions.splice(index, 1);
      this.setState({actions, modified: true}, this.validate);
    };
    const onActionChange = (index) => (action) => {
      actions.splice(index, 1, action);
      this.setState({actions, modified: true}, this.validate);
    };
    const onAddAction = () => {
      actions.push({});
      this.setState({actions, modified: true}, this.validate);
    };
    return (
      <div className={styles.quotaActionsContainer}>
        <span className={styles.label}>
          Actions:
        </span>
        <div className={styles.actionsColumn}>
          {
            (actions || []).map((a, index) => (
              <QuotaThreshold
                key={index}
                disabled={disabled}
                action={a.action}
                value={a.threshold}
                quota={value}
                error={error && Array.isArray(error) && !!error[index]}
                onChange={onActionChange(index)}
                onRemove={onRemove(index)}
              />
            ))
          }
          <div className={styles.add}>
            <Button disabled={disabled} size="small" onClick={onAddAction}>
              <Icon type="plus" /> Add action
            </Button>
          </div>
          {
            error && !Array.isArray(error) && (<span className={styles.actionsError}>{error}</span>)
          }
        </div>
      </div>
    );
  };

  onRemove = () => {
    const {onRemove} = this.props;
    Modal.confirm({
      title: 'Are you sure you want to remove quota template?',
      onOk: onRemove
    });
  };

  render () {
    const {
      isNew,
      visible,
      onCancel
    } = this.props;
    const {disabled, modified, errors} = this.state;
    return (
      <Modal
        width="50%"
        visible={visible}
        onCancel={onCancel}
        footer={(
          <Row type="flex" justify="space-between">
            <Button
              type="danger"
              disabled={disabled || isNew}
              onClick={this.onRemove}
            >
              REMOVE
            </Button>
            <div>
              <Button
                disabled={disabled}
                onClick={onCancel}
                style={{marginRight: 5}}
              >
                CANCEL
              </Button>
              <Button
                type="primary"
                disabled={disabled || !modified || Object.keys(errors).length > 0}
                onClick={this.onSaveClicked}>
                SAVE
              </Button>
            </div>
          </Row>
        )}
        title={`${isNew ? 'Add' : 'Edit'} quota template`}
      >
        {this.renderTemplateNameInput()}
        {this.renderQuotaInput()}
        {this.renderActions()}
      </Modal>
    );
  }
}

export default QuotaTemplateEditDialog;
