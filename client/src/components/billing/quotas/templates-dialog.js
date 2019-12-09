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
import {Alert, Button, Icon, message, Modal, Row} from 'antd';
import QuotaTemplateEditDialog from './quota-template-edit-dialog';
import QuotaDescription from './quota-description';
import * as billing from '../../../models/billing';
import styles from './quotas.css';

@inject('quotaTemplates')
@observer
class TemplatesDialog extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    onClose: PropTypes.func
  };

  state = {
    disabled: false,
    editableTemplate: null
  };

  get templates () {
    const {quotaTemplates} = this.props;
    if (quotaTemplates.loaded) {
      return (quotaTemplates.value || []).map(t => t);
    }
    return [];
  }

  onAddTemplateClicked = () => {
    this.setState({editableTemplate: {draft: true}});
  };

  onEditTemplateClicked = (template) => {
    this.setState({editableTemplate: template});
  };

  onCancelEditTemplate = () => {
    this.setState({editableTemplate: null});
  };

  onEditTemplate = async (template) => {
    const hide = message.loading('Updating quota template...', 0);
    const QuotaTemplateUpdate = billing.quotas.templates.Update;
    const request = new QuotaTemplateUpdate();
    await request.send(template);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.quotaTemplates.fetch();
      this.setState({editableTemplate: null}, hide);
    }
  };

  onRemoveTemplate = async () => {
    const hide = message.loading('Removing quota template...', 0);
    const {editableTemplate} = this.state;
    const QuotaTemplateRemove = billing.quotas.templates.Remove;
    const request = new QuotaTemplateRemove();
    await request.send(editableTemplate);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.quotaTemplates.fetch();
      hide();
      this.setState({editableTemplate: null}, hide);
    }
  };

  renderTemplate = (template, index) => {
    return (
      <div key={index} className={styles.quota} onClick={e => this.onEditTemplateClicked(template)}>
        <b className={styles.target}>{template.name}</b>
        <QuotaDescription quota={template} />
      </div>
    );
  };

  render () {
    const {onClose, visible, quotaTemplates} = this.props;
    const pending = quotaTemplates.pending && !quotaTemplates.loaded;
    const error = quotaTemplates.error;
    const {
      disabled,
      editableTemplate
    } = this.state;
    return (
      <Modal
        visible={visible}
        title={(
          <Row type="flex" justify="space-between" style={{paddingRight: 50}}>
            <span>Quota templates</span>
            <Button
              disabled={disabled}
              onClick={this.onAddTemplateClicked}
            >
              <Icon type="plus" /> Add template
            </Button>
          </Row>
        )}
        onCancel={onClose}
        footer={false}
        width="50%"
      >
        {
          pending &&
          (
            <Row type="flex" justify="space-around">
              <Icon type="loading" />
            </Row>
          )
        }
        {
          !pending && error &&
          (
            <Alert type="error" message={quotaTemplates.error} />
          )
        }
        {
          this.templates.length === 0 &&
          (
            <Alert
              type="info"
              message={(
                <div>
                  <span>Templates are not set. </span>
                  <span>Click </span>
                  <b><a disabled={disabled} onClick={this.onAddTemplateClicked}>Add template</a></b>
                  <span> button to create first template.</span>
                </div>
              )}
            />
          )
        }
        {this.templates.map(this.renderTemplate)}
        <QuotaTemplateEditDialog
          visible={!!editableTemplate}
          onCancel={this.onCancelEditTemplate}
          onRemove={this.onRemoveTemplate}
          onSave={this.onEditTemplate}
          isNew={editableTemplate && editableTemplate.draft}
          template={editableTemplate}
        />
      </Modal>
    );
  }
}

export default TemplatesDialog;
