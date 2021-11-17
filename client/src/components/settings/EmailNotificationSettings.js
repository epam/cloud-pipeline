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
import {Alert, message, Modal} from 'antd';
import EditEmailNotification from './forms/EditEmailNotification';
import SubSettings from './sub-settings';

const notificationSettingsRequest = new NotificationSettings();
const emailTemplatesRequest = new NotificationTemplates();

@inject('router', 'users', 'authenticatedUserInfo')
@inject(() => ({
  notificationSettings: notificationSettingsRequest,
  emailTemplates: emailTemplatesRequest
}))
@observer
export default class EmailNotificationSettings extends React.Component {
  state = {
    changesCanBeSkipped: false
  };

  componentDidMount () {
    const {
      route,
      router,
      notificationSettings,
      emailTemplates
    } = this.props;
    if (route && router) {
      router.setRouteLeaveHook(route, this.checkSettingsBeforeLeave);
    }
    notificationSettings.fetch();
    emailTemplates.fetch();
  };

  reload = () => {
    this.props.notificationSettings.fetch();
    this.props.emailTemplates.fetch();
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

  updateTemplate = async (template, values) => {
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

  canChangeTemplate = () => {
    return new Promise((resolve) => {
      if (this.templateModified) {
        Modal.confirm({
          title: 'You have unsaved changes. Continue?',
          style: {
            wordWrap: 'break-word'
          },
          onOk () {
            resolve(true);
          },
          onCancel () {
            resolve(false);
          },
          okText: 'Yes',
          cancelText: 'No'
        });
      } else {
        resolve(true);
      }
    });
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

  renderTemplateForm = (template) => {
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
          onSubmit={(values) => this.updateTemplate(template, values)}
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
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return null;
    }
    if (!this.props.authenticatedUserInfo.value.admin) {
      return (
        <Alert type="error" message="Access is denied" />
      );
    }
    if (!this.props.notificationSettings.loaded && this.props.notificationSettings.pending) {
      return <LoadingView />;
    }
    if (this.props.notificationSettings.error) {
      return <Alert type="warning" message={this.props.notificationSettings.error} />;
    }
    return (
      <SubSettings
        sections={
          this.templates.map(template => ({
            key: template.type,
            title: template.type,
            render: () => this.renderTemplateForm(template)
          }))
        }
        canNavigate={this.canChangeTemplate}
      />
    );
  }
}
