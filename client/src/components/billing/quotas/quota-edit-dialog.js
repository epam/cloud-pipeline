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
  InputNumber,
  Modal,
  Row,
  Select
} from 'antd';
import {inject, observer} from 'mobx-react';
import QuotaThreshold from './quotas-threshold';
import * as billing from '../../../models/billing';
import styles from './quotas.css';

const ValidationFields = {
  actions: 'actions',
  target: 'target',
  value: 'value'
};

@inject('quotaTemplates')
@observer
class EditQuotaDialog extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    isNew: PropTypes.bool,
    quota: PropTypes.object,
    target: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    targets: PropTypes.array,
    template: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    type: PropTypes.string,
    onRemove: PropTypes.func,
    onCancel: PropTypes.func,
    onSave: PropTypes.func
  };

  state = {
    actions: [],
    target: null,
    type: null,
    value: 0,
    template: null,

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

  get templates () {
    const {quotaTemplates} = this.props;
    if (quotaTemplates.loaded) {
      return (quotaTemplates.value || []).map(t => t);
    }
    return [];
  }

  get template () {
    const {template} = this.state;
    if (template) {
      const [t] = this.templates.filter(t => +(t.id) === +template);
      return t;
    }
    return null;
  }

  createInitialState = async (props) => {
    const {
      quota = {},
      target,
      type,
      template
    } = props;
    let {actions = [], value = 0} = quota || {};
    if (template) {
      await this.props.quotaTemplates.fetchIfNeededOrWait();
      const [t] = this.templates.filter(t => +(t.id) === +template);
      if (t) {
        actions = t.actions.map(a => a);
        value = t.value;
      }
    }
    this.setState({actions, target, template, type, value, modified: false}, this.validate);
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
    const {actions, target, template, type, value} = this.state;
    const errors = {};
    if (!template) {
      if (isNaN(value)) {
        errors[ValidationFields.value] = 'Value should be a positive number';
      } else if (value === 0) {
        errors[ValidationFields.value] = 'Value should be great then zero';
      }
      if (type !== billing.quotas.keys.global && !target) {
        errors[ValidationFields.target] = 'Quota subject is required';
      }
      if (!actions || actions.length === 0) {
        errors[ValidationFields.actions] = 'At least 1 action must be set';
      } else {
        const actionErrors = actions.map(this.validateAction);
        if (actionErrors.filter(Boolean).length > 0) {
          errors[ValidationFields.actions] = actionErrors;
        }
      }
    }
    this.setState({errors});
    return Object.keys(errors).length === 0;
  };

  onSaveClicked = () => {
    const {onSave} = this.props;
    const {actions, target, template, type, value} = this.state;
    const valid = this.validate();
    if (valid && onSave) {
      this.setState({disabled: true}, async () => {
        await onSave({quota: !template ? {actions, value} : undefined, target, type, template});
        this.setState({disabled: false});
      });
    }
  };

  renderTargets = () => {
    const {
      target: initialTarget,
      targets
    } = this.props;
    if (!targets || !targets.length) {
      return null;
    }
    const {disabled, errors, target, type} = this.state;
    const error = errors[ValidationFields.target];
    return (
      <div className={styles.targetSelectContainer}>
        <span className={styles.label}>
          {billing.quotas.getQuotaTypeTargetName(type)}:
        </span>
        <Select
          disabled={disabled || !!initialTarget}
          className={`${styles.targetSelect} ${error ? styles.error : ''}`}
          value={target}
          onChange={t => this.setState({target: t, modified: true}, this.validate)}
          placeholder={`Select ${billing.quotas.getQuotaTypeTargetName(type).toLowerCase()}`}
        >
          {targets.map(target => (
            <Select.Option key={`${target.obj.id}`} value={`${target.obj.id}`}>
              {target.name}
            </Select.Option>
          ))}
        </Select>
      </div>
    );
  };

  renderTemplateSelect = () => {
    if (!this.templates || !this.templates.length) {
      return null;
    }
    const {disabled, template} = this.state;
    const onSelect = (t) => {
      this.setState({
        template: +t,
        modified: true
      }, () => {
        if (this.template) {
          this.setState({actions: this.template.actions.map(a => a), value: this.template.value}, this.validate);
        } else {
          this.validate();
        }
      });
    };
    return [
      <div key="template" className={styles.targetSelectContainer}>
        <span className={styles.label}>
          Template:
        </span>
        <Select
          disabled={disabled}
          className={styles.targetSelect}
          value={template ? `${template}` : null}
          onChange={onSelect}
          placeholder="Select template"
        >
          {this.templates.map(t => (
            <Select.Option key={`${t.id}`} value={`${t.id}`}>
              {t.name}
            </Select.Option>
          ))}
        </Select>
      </div>,
      <div
        key="or"
        style={{
          display: 'flex',
          flexDirection: 'row',
          marginTop: 5,
          alignItems: 'center',
          width: '100%'
        }}
      >
        <div style={{flex: 1, height: 1, borderBottom: '1px solid #ccc'}}>{'\u00A0'}</div>
        <span style={{marginLeft: 5, marginRight: 5, fontSize: 'smaller'}}>OR</span>
        <div style={{flex: 1, height: 1, borderBottom: '1px solid #ccc'}}>{'\u00A0'}</div>
      </div>
    ];
  };

  renderQuotaInput = () => {
    let {disabled, errors, value} = this.state;
    const error = errors[ValidationFields.value];
    if (this.template) {
      value = this.template.value;
    }
    return (
      <div className={styles.quotaInputContainer}>
        <span className={styles.label}>
          Quota:
        </span>
        <InputNumber
          disabled={disabled}
          className={`${styles.quotaInput} ${error ? styles.error : ''}`}
          value={value}
          onChange={e => this.setState({value: e, template: null, modified: true}, this.validate)}
          min={0}
        />
      </div>
    );
  };

  renderActions = () => {
    let {
      actions,
      disabled,
      errors,
      value
    } = this.state;
    const error = errors[ValidationFields.actions];
    if (this.template) {
      actions = this.template.actions.map(a => a);
    }
    const onRemove = (index) => () => {
      actions.splice(index, 1);
      this.setState({actions, template: null, modified: true}, this.validate);
    };
    const onActionChange = (index) => (action) => {
      actions.splice(index, 1, action);
      this.setState({actions, template: null, modified: true}, this.validate);
    };
    const onAddAction = () => {
      actions.push({});
      this.setState({actions, template: null, modified: true}, this.validate);
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
      title: 'Are you sure you want to remove quota?',
      onOk: onRemove
    });
  };

  render () {
    const {
      isNew,
      visible,
      onCancel
    } = this.props;
    const {disabled, modified, type, errors} = this.state;
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
        title={`${isNew ? 'Add' : 'Edit'} ${billing.quotas.getQuotaTypeName(type).toLowerCase()}`}
      >
        {this.renderTargets()}
        {this.renderTemplateSelect()}
        {this.renderQuotaInput()}
        {this.renderActions()}
      </Modal>
    );
  }
}

export default EditQuotaDialog;
