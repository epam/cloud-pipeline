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
import {Alert, Button, Icon, message} from 'antd';
import QuotaDescription from './quota-description';
import EditQuotaDialog from './quota-edit-dialog';
import QuotaTemplatesDialog from './templates-dialog';
import * as billing from '../../../models/billing';
import GroupsFetch from '../../../models/user/Roles';
import UsersFetch from '../../../models/user/Users';
import styles from './quotas.css';

function getQuotaTypeSubjects (type) {
  let subjectsRequest;
  switch (type) {
    case billing.quotas.keys.group: subjectsRequest = new GroupsFetch(); break;
    case billing.quotas.keys.user: subjectsRequest = new UsersFetch(); break;
    case billing.quotas.keys.billingCenters:
      subjectsRequest = new billing.FetchBillingCenters();
      break;
  }
  subjectsRequest && subjectsRequest.fetchIfNeededOrWait && subjectsRequest.fetchIfNeededOrWait();
  return subjectsRequest;
}

@inject('quotaTemplates')
@inject((stores, params) => {
  const {quotaType, quotaTemplates} = params;
  return {
    subjects: getQuotaTypeSubjects(quotaType),
    quotas: new billing.quotas.List(quotaType),
    quotaTemplates
  };
})
@observer
class QuotasSection extends React.Component {
  static propTypes = {
    quotaType: PropTypes.string,
    title: PropTypes.string
  };

  state = {
    editableQuota: null,
    quotaTemplatesDialogVisible: false
  };

  componentDidMount () {
    this.updateQuotaType(this.props.quotaType);
  }

  componentWillReceiveProps (nextProps, nextContext) {
    this.updateQuotaType(nextProps.quotaType);
  }

  updateQuotaType = (type) => {
    if (type === billing.quotas.keys.global) {
      this.setState({quotas: [{quota: {actions: [{threshold: 100}]}}]});
    }
  };

  get quotas () {
    const {quotas} = this.props;
    if (quotas.loaded) {
      return (quotas.value || []).map(q => q);
    }
    return [];
  }

  get quotaTargets () {
    // users or groups
    const {subjects, quotaType} = this.props;
    if (subjects && subjects.loaded) {
      let mapper = e => e;
      switch (quotaType) {
        case billing.quotas.keys.group:
          const splitRoleName = (name) => {
            if (name && name.toLowerCase().indexOf('role_') === 0) {
              return name.substring('role_'.length);
            }
            return name;
          };
          mapper = g => ({name: splitRoleName(g.name), obj: g, group: true});
          break;
        case billing.quotas.keys.user:
          mapper = u => ({name: u.userName, obj: u, user: true});
          break;
        case billing.quotas.keys.billingCenters:
          mapper = b => ({name: b, obj: {id: b, name: b}, center: true});
          break;
      }
      return (subjects.value || []).map(mapper);
    }
    return [];
  }

  addNewQuota = () => {
    const {quotaType} = this.props;
    this.setState({
      editableQuota: {draft: true, type: quotaType}
    });
  };

  cancelEditQuota = () => {
    this.setState({editableQuota: null});
  };

  onQuotaClick = (quota) => {
    this.setState({editableQuota: quota});
  };

  onEditQuota = async (quota) => {
    const hide = message.loading('Updating quota...', 0);
    const QuotaUpdate = billing.quotas.Update;
    const request = new QuotaUpdate();
    await request.send(quota);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.quotas.fetch();
      this.setState({editableQuota: null}, hide);
    }
  };

  onRemoveQuota = async () => {
    const hide = message.loading('Removing quota...', 0);
    const {editableQuota} = this.state;
    const QuotaRemove = billing.quotas.Remove;
    const request = new QuotaRemove();
    await request.send(editableQuota);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.quotas.fetch();
      hide();
      this.setState({editableQuota: null}, hide);
    }
  };

  openQuotaTemplatesDialog = () => {
    this.setState({quotaTemplatesDialogVisible: true});
  };

  closeQuotaTemplatesDialog = () => {
    this.setState({quotaTemplatesDialogVisible: false});
  };

  renderQuota = (quota, index) => {
    const renderTarget = () => {
      const {target, type} = quota;
      const [quotaTarget] = this.quotaTargets.filter(qt => `${qt.obj.id}` === `${target}`);
      if (!quotaTarget) {
        return null;
      }
      let icon;
      switch (type) {
        case billing.quotas.keys.billingCenters: icon = (<Icon type="solution" />); break;
        case billing.quotas.keys.user: icon = (<Icon type="user" />); break;
        case billing.quotas.keys.group: icon = (<Icon type="team" />); break;
      }
      const {name} = quotaTarget;
      return (
        <span className={styles.target}>
          {icon}
          {name}
        </span>
      );
    };
    let quotaDescription = quota.quota;
    const {quotaTemplates} = this.props;
    if (quota.template && quotaTemplates.loaded) {
      const [template] = (quotaTemplates.value || []).filter(t => +(t.id) === +quota.template);
      if (template) {
        quotaDescription = template;
      }
    }
    return (
      <div key={index} className={styles.quota} onClick={e => this.onQuotaClick(quota)}>
        {renderTarget()}
        <QuotaDescription quota={quotaDescription} />
      </div>
    );
  };

  render () {
    const {
      title,
      quotaType,
      subjectRequest
    } = this.props;
    const {
      editableQuota,
      quotaTemplatesDialogVisible
    } = this.state;
    const error = subjectRequest && subjectRequest.error;
    const pending = subjectRequest && !subjectRequest.loaded && subjectRequest.pending;
    const global = quotaType === billing.quotas.keys.global;
    return (
      <div className={styles.sectionContainer}>
        <div className={styles.section}>
          <div className={styles.header}>
            <div className={styles.title}>
              {title || ''}
            </div>
            <div className={styles.actions}>
              {
                (!global || this.quotas.length === 0) &&
                (
                  <Button
                    disabled={error || pending}
                    size="small"
                    onClick={this.addNewQuota}
                  >
                    <Icon type="plus" />
                  </Button>
                )
              }
              {
                global &&
                (
                  <Button
                    disabled={error || pending}
                    size="small"
                    onClick={this.openQuotaTemplatesDialog}
                  >
                    <Icon type="setting" />
                  </Button>
                )
              }
            </div>
          </div>
          <div>
            {
              error &&
              (
                <Alert type="error" message={error} />
              )
            }
          </div>
          {this.quotas.map(this.renderQuota)}
          <EditQuotaDialog
            isNew={editableQuota && editableQuota.draft}
            visible={!!editableQuota}
            onCancel={this.cancelEditQuota}
            onRemove={this.onRemoveQuota}
            onSave={this.onEditQuota}
            quota={editableQuota ? editableQuota.quota : null}
            target={editableQuota ? editableQuota.target : null}
            targets={this.quotaTargets}
            template={editableQuota ? editableQuota.template : null}
            type={editableQuota ? editableQuota.type : null}
          />
          <QuotaTemplatesDialog
            visible={quotaTemplatesDialogVisible}
            onClose={this.closeQuotaTemplatesDialog}
          />
        </div>
      </div>
    );
  }
}

export default QuotasSection;
