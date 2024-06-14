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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Button,
  Icon,
  InputNumber,
  Modal,
  Row,
  Select
} from 'antd';
import classNames from 'classnames';
import QuotaThreshold from './quotas-threshold';
import {quotaGroupNames} from './utilities/quota-groups';
import quotaTypes, {quotaSubjectName} from './utilities/quota-types';
import quotaPeriods from './utilities/quota-periods';
import quotaActions from './utilities/quota-actions';
import {splitRoleName} from '../../settings/user-management/utilities';
import GroupFind from '../../../models/user/GroupFind';
import UserName from '../../special/UserName';
import UsersRolesSelect from '../../special/users-roles-select';
import styles from './quotas.css';

const MINIMUM_SEARCH_LENGTH = 2;

@inject('users', 'roles', 'billingCenters')
@observer
class EditQuotaDialog extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    visible: PropTypes.bool,
    quota: PropTypes.object,
    onRemove: PropTypes.func,
    onCancel: PropTypes.func,
    onSave: PropTypes.func
  };

  state = {
    actions: [{}],
    recipients: [],
    subject: undefined,
    value: 0,
    period: undefined,
    modified: false,
    errors: {},
    filter: undefined,
    adGroups: []
  };

  @computed
  get users () {
    const {users} = this.props;
    if (users && users.loaded) {
      return (users.value || []).slice();
    }
    return [];
  }

  @computed
  get groups () {
    const {roles} = this.props;
    const {adGroups = []} = this.state;
    if (roles && roles.loaded) {
      const uniqueNames = new Set((roles.value || []).map(o => o.name));
      return (roles.value || [])
        .slice()
        .concat(adGroups.filter(ad => !uniqueNames.has(ad.name)));
    }
    return adGroups;
  }

  @computed
  get billingCenters () {
    const {billingCenters} = this.props;
    if (billingCenters && billingCenters.loaded) {
      return (billingCenters.value || []).slice();
    }
    return [];
  }

  get isNewQuota () {
    return this.props.quota && !this.props.quota.id;
  }

  get isNotifyActionAdded () {
    const {actions} = this.state;
    return actions
      .filter(Boolean)
      .some((action) => (
        action.actions &&
          action.actions.includes(quotaActions.notify)
      ));
  }

  componentDidMount () {
    this.createInitialState();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (this.props.visible && this.props.visible !== prevProps.visible) {
      this.createInitialState();
    }
  }

  createInitialState = () => {
    const {quota = {}} = this.props;
    const {
      actions = [{}],
      value = 0,
      recipients = [],
      subject,
      period = quotaPeriods.month
    } = quota || {};
    this.setState({
      actions,
      recipients,
      subject,
      value,
      period,
      modified: false,
      filter: undefined
    }, this.validate);
  };

  validateAction = (action) => {
    if (!action) {
      return null;
    }
    const {actions = [], threshold} = action;
    if (isNaN(threshold) || threshold === 0) {
      return 'Threshold must be positive number';
    }
    if (actions.length === 0) {
      return 'You should specify an action';
    }
    return null;
  };

  validate = () => {
    const {quota} = this.props;
    if (!quota) {
      return true;
    }
    const {type} = quota;
    const {
      actions,
      subject,
      value,
      filter,
      recipients
    } = this.state;
    const errors = {};
    if (isNaN(value)) {
      errors.value = 'Value should be a positive number';
    } else if (value === 0) {
      errors.value = 'Value should be great then zero';
    }
    if (type !== quotaTypes.overall && !subject && !filter) {
      errors.subject = 'Quota subject is required';
    }
    if (actions.length === 0) {
      errors.actions = 'Quota action is required';
    }
    const actionErrors = actions.map(this.validateAction);
    if (actionErrors.filter(Boolean).length > 0) {
      errors.actions = actionErrors;
    }
    if (this.isNotifyActionAdded && !recipients.length) {
      errors.recipients = 'Quota recipients is required';
    }
    this.setState({errors});
    return Object.keys(errors).length === 0;
  };

  onSaveClicked = () => {
    const {
      onSave,
      quota
    } = this.props;
    const {
      actions,
      recipients,
      subject,
      value,
      period
    } = this.state;
    const valid = this.validate();
    if (valid && onSave) {
      onSave({
        ...quota,
        actions,
        recipients,
        subject,
        value,
        period
      });
    }
  };

  onChangeSubject = (subject) => {
    this.setState({
      subject,
      filter: undefined,
      modified: true
    }, this.validate);
  };

  renderUsersSelection = () => {
    const {
      errors = {},
      subject,
      filter
    } = this.state;
    const {
      disabled
    } = this.props;
    const onChangeSearchString = e => this.setState({filter: e}, this.validate);
    const error = errors.subject;
    const filtered = this.users
      .filter(user => filter &&
        filter.length >= MINIMUM_SEARCH_LENGTH &&
        (
          user.userName.toLowerCase().includes(filter.toLowerCase()) ||
          (
            user.attributes && Object.entries(user.attributes)
              .some(([_attribute, value]) => value.toLowerCase().includes(filter.toLowerCase()))
          )
        ));
    const current = this.users.find(user => user.userName === subject);
    if (current && !filtered.find(o => o.userName === current.userName)) {
      filtered.push(current);
    }
    filtered.sort((a, b) => a.userName.localeCompare(b.userName));
    return (
      <Select
        showSearch
        disabled={disabled || !this.isNewQuota}
        className={
          classNames(
            styles.targetSelect,
            {'cp-error': error}
          )
        }
        value={subject}
        onChange={this.onChangeSubject}
        filterOption={false}
        getPopupContainer={o => o.parentNode}
        onSearch={onChangeSearchString}
        onBlur={() => onChangeSearchString()}
        notFoundContent={
          filter && filter.length >= MINIMUM_SEARCH_LENGTH
            ? `Nothing found for "${filter}"`
            : 'Specify user name'
        }
      >
        {
          filtered.map(user => (
            <Select.Option
              key={user.userName}
              value={user.userName}
            >
              <UserName
                userName={user.userName}
                showIcon={false}
              />
            </Select.Option>
          ))
        }
      </Select>
    );
  };

  renderGroupsSelection = () => {
    const {
      errors = {},
      subject,
      filter
    } = this.state;
    const {
      disabled
    } = this.props;
    const onChangeSearchString = e => {
      this.setState({filter: e}, () => {
        GroupFind.findGroups(e)
          .then((groups = []) => {
            if (this.state.filter === e) {
              this.setState({
                adGroups: groups.map(group => ({
                  name: group,
                  predefined: true
                }))
              });
            }
          });
        this.validate();
      });
    };
    const error = errors.subject;
    const filtered = this.groups
      .filter(role => filter &&
        filter.length >= MINIMUM_SEARCH_LENGTH &&
        role.name.toLowerCase().includes(filter.toLowerCase())
      );
    const current = this.groups.find(role => role.name === subject);
    if (current && !filtered.find(o => o.name === current.name)) {
      filtered.push(current);
    }
    filtered.sort((a, b) => a.name.localeCompare(b.name));
    return (
      <Select
        showSearch
        disabled={disabled || !this.isNewQuota}
        className={
          classNames(
            styles.targetSelect,
            {'cp-error': error}
          )
        }
        value={subject}
        onChange={this.onChangeSubject}
        filterOption={false}
        getPopupContainer={o => o.parentNode}
        onSearch={onChangeSearchString}
        onBlur={() => onChangeSearchString()}
        notFoundContent={
          filter && filter.length >= MINIMUM_SEARCH_LENGTH
            ? `Nothing found for "${filter}"`
            : 'Specify group name'
        }
      >
        {
          filtered.map(group => (
            <Select.Option
              key={group.name}
              value={group.name}
            >
              {splitRoleName(group)}
            </Select.Option>
          ))
        }
      </Select>
    );
  };

  renderBillingCentersSelection = () => {
    const {
      errors = {},
      subject
    } = this.state;
    const {
      disabled
    } = this.props;
    const error = errors.subject;
    return (
      <Select
        showSearch
        disabled={disabled || !this.isNewQuota}
        className={
          classNames(
            styles.targetSelect,
            {'cp-error': error}
          )
        }
        value={subject}
        onChange={t => this.setState({subject: t, modified: true}, this.validate)}
        filterOption={(input, option) =>
          option.props.value.toLowerCase().indexOf((input || '').toLowerCase()) >= 0
        }
        placeholder="Select billing center"
        notFoundContent="Nothing found"
        getPopupContainer={o => o.parentNode}
      >
        {
          this.billingCenters.map(billingCenter => (
            <Select.Option
              key={billingCenter}
              value={billingCenter}
            >
              {billingCenter}
            </Select.Option>
          ))
        }
      </Select>
    );
  };

  renderSubject = () => {
    const {
      quota
    } = this.props;
    if (!quota) {
      return null;
    }
    const {type} = quota;
    if (type === quotaTypes.overall) {
      return null;
    }
    let selector;
    switch (type) {
      case quotaTypes.user: selector = this.renderUsersSelection(); break;
      case quotaTypes.group: selector = this.renderGroupsSelection(); break;
      case quotaTypes.billingCenter: selector = this.renderBillingCentersSelection(); break;
      default:
        return null;
    }
    return (
      <div className={styles.targetSelectContainer}>
        <span className={styles.label}>
          {quotaSubjectName[type]}:
        </span>
        {selector}
      </div>
    );
  };

  renderQuotaInput = () => {
    const {
      disabled
    } = this.props;
    const {
      errors,
      value,
      period
    } = this.state;
    const error = errors.value;
    return (
      <div className={styles.quotaInputContainer}>
        <span className={styles.label}>
          Quota:
        </span>
        <InputNumber
          disabled={disabled || !this.isNewQuota}
          className={classNames(
            styles.quotaInput,
            {
              'cp-error': error
            }
          )}
          value={value}
          onChange={e => this.setState({value: e, modified: true}, this.validate)}
          min={0}
        />
        <span style={{margin: '0px 5px'}}>$</span>
        <Select
          disabled={disabled || !this.isNewQuota}
          style={{width: 200}}
          getPopupContainer={node => node.parentNode}
          value={period}
          onChange={e => this.setState({period: e, modified: true}, this.validate)}
        >
          <Select.Option
            key={quotaPeriods.month}
            value={quotaPeriods.month}>
            per month
          </Select.Option>
          <Select.Option
            key={quotaPeriods.quarter}
            value={quotaPeriods.quarter}>
            per quarter
          </Select.Option>
          <Select.Option
            key={quotaPeriods.year}
            value={quotaPeriods.year}>
            per year
          </Select.Option>
        </Select>
      </div>
    );
  };

  renderActions = () => {
    const {
      quota,
      disabled
    } = this.props;
    if (!quota) {
      return null;
    }
    const {
      actions,
      errors
    } = this.state;
    const {
      type,
      quotaGroup
    } = quota;
    const error = errors.actions;
    const onRemove = (index) => () => {
      const modifiedActions = actions.slice();
      modifiedActions.splice(index, 1);
      this.setState({actions: modifiedActions, modified: true}, this.validate);
    };
    const onActionChange = (index) => (action) => {
      const modifiedActions = actions.slice();
      modifiedActions.splice(index, 1, action);
      this.setState({actions: modifiedActions, modified: true}, this.validate);
    };
    const onAddAction = () => {
      const modifiedActions = actions.slice();
      modifiedActions.push({});
      this.setState({actions: modifiedActions, modified: true}, this.validate);
    };
    const showErrorStyles = (index) => (
      error &&
      Array.isArray(error) &&
      !!error[index] &&
      !disabled &&
      this.isNewQuota
    );
    return (
      <div className={styles.quotaActionsContainer}>
        <span className={styles.label}>
          Actions:
        </span>
        <div className={styles.actionsColumn}>
          {
            (actions || []).map((action, index) => (
              <QuotaThreshold
                key={`action-${index}`}
                disabled={disabled || !this.isNewQuota}
                action={action}
                quotaGroup={quotaGroup}
                quotaType={type}
                error={showErrorStyles(index)}
                onChange={onActionChange(index)}
                onRemove={onRemove(index)}
                single={actions.length === 1}
              />
            ))
          }
          {
            this.isNewQuota && (
              <div className={styles.add}>
                <Button
                  disabled={disabled || !this.isNewQuota}
                  onClick={onAddAction}
                >
                  <Icon type="plus" /> Add action
                </Button>
              </div>)
          }
          {
            error &&
            !Array.isArray(error) && (
              <span className={classNames(
                styles.actionsError,
                'cp-error'
              )}>
                {error}
              </span>
            )
          }
        </div>
      </div>
    );
  };

  renderRecipients = () => {
    const {
      errors,
      recipients
    } = this.state;
    const {
      disabled
    } = this.props;
    const error = errors.recipients;
    return (
      <div className={styles.quotaInputContainer}>
        <span className={styles.label}>
          Recipients:
        </span>
        <UsersRolesSelect
          value={recipients}
          onChange={newRecipients => this.setState(
            {recipients: newRecipients, modified: true},
            this.validate
          )}
          disabled={disabled || !this.isNewQuota}
          className={
            classNames(
              {
                'cp-error': error
              }
            )
          }
          style={{flex: 1}}
        />
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
      visible,
      onCancel,
      quota,
      disabled
    } = this.props;
    const {quotaGroup} = quota || {};
    const {modified, errors} = this.state;
    const groupName = quotaGroup ? (quotaGroupNames[quotaGroup] || quotaGroup) : '';
    const title = [
      this.isNewQuota ? 'Create' : false,
      this.isNewQuota ? groupName.toLowerCase() : groupName
    ].filter(Boolean).join(' ');
    return (
      <Modal
        width="50%"
        visible={visible}
        onCancel={onCancel}
        footer={(
          <Row
            type="flex"
            justify={this.isNewQuota ? 'end' : 'space-between'}
          >
            {
              !this.isNewQuota && (
                <Button
                  type="danger"
                  disabled={disabled}
                  onClick={this.onRemove}
                  id="quota-edit-modal-remove-btn"
                >
                  REMOVE
                </Button>
              )
            }
            <div>
              <Button
                onClick={onCancel}
                disabled={disabled}
                id="quota-edit-modal-cancel-btn"
              >
                {this.isNewQuota ? 'CANCEL' : 'CLOSE'}
              </Button>
              {
                this.isNewQuota && (
                  <Button
                    type="primary"
                    disabled={disabled || !modified || Object.keys(errors).length > 0}
                    onClick={this.onSaveClicked}
                    style={{marginLeft: 5}}
                    id="quota-edit-modal-save-btn"
                  >
                    SAVE
                  </Button>
                )
              }
            </div>
          </Row>
        )}
        title={title}
      >
        {this.renderSubject()}
        {this.renderQuotaInput()}
        {this.renderActions()}
        {this.isNotifyActionAdded && this.renderRecipients()}
      </Modal>
    );
  }
}

export default EditQuotaDialog;
