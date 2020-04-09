/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import NotificationSettings from '../../models/settings/NotificationSettings';
import NotificationSettingUpdate from '../../models/settings/NotificationSettingUpdate';
import NotificationTemplateUpdate from '../../models/settings/NotificationTemplateUpdate';
import NotificationTemplates from '../../models/settings/NotificationTemplates';
import LoadingView from '../special/LoadingView';
import {SplitPanel} from '../special/splitPanel/SplitPanel';
import Users from '../../models/user/Users';
import {Alert, message, Modal, Table} from 'antd';
import EditEmailNotification from './forms/EditEmailNotification';
import styles from './EmailNotificationSettings.css';

@inject('router')
@inject(() => {
  return {
    notificationSettings: new NotificationSettings(),
    emailTemplates: new NotificationTemplates(),
    users: new Users()
  };
})
@observer
export default class EmailNotificationSettings extends React.Component {

  state = {
    selectedTemplateId: null,
    changesCanBeSkipped: false
  };

  componentDidMount () {
    const {route, router} = this.props;
    if (route && router) {
      router.setRouteLeaveHook(route, this.checkSettingsBeforeLeave);
    }
  };

  componentDidUpdate () {
    if (!this.state.selectedTemplateId && this.templates.length > 0) {
      this.selectTemplate(this.templates[0]);
    }
  }

  reload = async (clearState = false) => {
    this.props.notificationSettings.fetch();
    this.props.emailTemplates.fetch();
    if (clearState) {
      this.editEmailNotificationForm && this.editEmailNotificationForm.resetFormFields();
      this.setState({
        selectedTemplateId: null
      });
    }
  };

  @computed
  get templates () {
    if (this.props.notificationSettings.loaded) {
      return (this.props.notificationSettings.value || [])
        .map(t => t)
        .sort((a, b) => {
          if (a.type > b.type) {
            return 1;
          } else if (a.type < b.type) {
            return -1;
          }
          return 0;
        });
    }
    return [];
  }

  @computed
  get emailTemplates () {
    if (this.props.emailTemplates.loaded) {
      return (this.props.emailTemplates.value || []).map(t => t);
    }
    return [];
  }

  @computed
  get users () {
    if (this.props.users.loaded) {
      return (this.props.users.value || []).map(u => u);
    }
    return [];
  }

  checkSettingsBeforeLeave = (nextLocation) => {
    const {router} = this.props;
    const {changesCanBeSkipped} = this.state;
    const makeTransition = nextLocation => {
      this.setState({changesCanBeSkipped: true},
        () => router.push(nextLocation)
      );
    };
    if (this.templateModified && !changesCanBeSkipped) {
      Modal.confirm({
        title: 'You have unsaved changes. Continue?',
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          makeTransition(nextLocation);
        },
        okText: 'Yes',
        cancelText: 'No'
      });
      return false;
    }
  };

  updateTemplate = async (values) => {
    const [template] = this.templates.filter(t => t.id === this.state.selectedTemplateId);
    const [emailTemplate] = this.emailTemplates.filter(t => t.id === template.templateId);
    if (template && emailTemplate) {
      const hide = message.loading('Updating...', -1);
      const updateSettingPayload = {
        id: template.id,
        informedUserIds: (values.informedUserIds || []).map(id => +id),
        keepInformedAdmins: values.keepInformedAdmins,
        keepInformedOwner: values.keepInformedOwner,
        statusesToInform: values.statusesToInform,
        resendDelay: +values.resendDelay,
        threshold: +values.threshold,
        templateId: emailTemplate.id,
        type: template.type,
        enabled: values.enabled
      };
      const updateTemplatePayload = {
        id: emailTemplate.id,
        name: emailTemplate.name,
        body: values.body,
        subject: values.subject
      };
      const templateUpdateRequest = new NotificationTemplateUpdate();
      await templateUpdateRequest.send(updateTemplatePayload);
      if (templateUpdateRequest.error) {
        hide();
        message.error(templateUpdateRequest.error, 5);
      } else {
        const settingUpdateRequest = new NotificationSettingUpdate();
        await settingUpdateRequest.send(updateSettingPayload);
        if (settingUpdateRequest.error) {
          hide();
          message.error(settingUpdateRequest.error, 5);
        } else {
          await this.reload();
          hide();
        }
      }
    } else {
      message.error('Template not found. Please refresh page', 5);
    }
  };

  selectTemplate = (template) => {
    const changeTemplate = () => {
      this.setState({
        selectedTemplateId: template ? template.id : null
      });
    };
    if (this.state.selectedTemplateId && this.templateModified) {
      Modal.confirm({
        title: 'You have unsaved changes. Continue?',
        style: {
          wordWrap: 'break-word'
        },
        async onOk () {
          changeTemplate();
        },
        okText: 'Yes',
        cancelText: 'No'
      });
    } else {
      changeTemplate();
    }
  };

  renderTemplatesTable = () => {
    const columns = [
      {
        dataIndex: 'type',
        key: 'name'
      }
    ];
    return (
      <Table
        className={styles.table}
        dataSource={this.templates}
        columns={columns}
        showHeader={false}
        pagination={false}
        rowKey="id"
        rowClassName={
          (template) => {
            const disabledClass = template.enabled ? '' : `${styles.disabled}`;
            return template.id === this.state.selectedTemplateId
              ? `${styles.templateRow} ${styles.selected} ${disabledClass}`
              : `${styles.templateRow} ${disabledClass}`;
          }
        }
        onRowClick={this.selectTemplate}
        size="medium" />
    );
  };

  editEmailNotificationForm;

  initializeEditEmailNotificationForm = (form) => {
    this.editEmailNotificationForm = form;
  };

  @computed
  get templateModified () {
    if (!this.editEmailNotificationForm) {
      return false;
    }
    return this.editEmailNotificationForm.modified;
  }

  renderTemplateForm = () => {
    const [template] = this.templates.filter(t => t.id === this.state.selectedTemplateId);
    if (!template) {
      return <div />;
    } else {
      const [emailTemplate] = this.emailTemplates.filter(t => t.id === template.templateId);
      if (!emailTemplate) {
        return <div />;
      }
      return (
        <EditEmailNotification
          users={this.users}
          onSubmit={this.updateTemplate}
          wrappedComponentRef={this.initializeEditEmailNotificationForm}
          template={
            Object.assign(
              {},
              template,
              {
                subject: emailTemplate.subject,
                body: emailTemplate.body
              }
            )} />
      );
    }
  };

  render () {
    if (!this.props.notificationSettings.loaded && this.props.notificationSettings.pending) {
      return <LoadingView />;
    }
    if (this.props.notificationSettings.error) {
      return <Alert type="warning" message={this.props.notificationSettings.error} />;
    }
    return (
      <SplitPanel
        contentInfo={[{
          key: 'templates',
          size: {
            pxDefault: 200
          }
        }]}>
        <div key="templates">{this.renderTemplatesTable()}</div>
        <div>{this.renderTemplateForm()}</div>
      </SplitPanel>
    );
  }
}
