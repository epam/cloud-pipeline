/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Modal,
  Form,
  Input,
  Row,
  Spin,
  Tabs
} from 'antd';
import PropTypes from 'prop-types';
import SharedItemInfo from './SharedItemInfo';
import PermissionsForm, {OBJECT_TYPES, MODES} from '../../../roleModel/PermissionsForm';

// eslint-disable-next-line
const NAME_VALIDATION_TEXT = 'Name can contain only letters, digits, spaces, \'_\', \'-\', \'@\' and \'.\'.';

const TABS = {
  info: 'info',
  permissions: 'permissions'
};

const capitalizeString = (string = '') => {
  return string.charAt(0).toUpperCase() + string.slice(1).toLowerCase();
};

@Form.create()
class EditItemForm extends React.Component {
  static propTypes = {
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    name: PropTypes.string,
    title: PropTypes.string,
    includeFileContentField: PropTypes.bool,
    storageId: PropTypes.string,
    item: PropTypes.object,
    tabs: PropTypes.arrayOf(PropTypes.string),
    mode: PropTypes.string
  };

  state = {
    activeTab: TABS.info,
    showSharedItemInfo: false,
    usersToShare: []
  }

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 6}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 18}
    }
  };

  onOk = (event) => {
    const {mode} = this.props;
    const {showSharedItemInfo} = this.state;
    if (mode === MODES.share && !showSharedItemInfo) {
      return this.setState({showSharedItemInfo: true});
    }
    return this.handleSubmit(event);
  };

  onCancel = () => {
    const {onCancel} = this.props;
    const {showSharedItemInfo} = this.state;
    if (showSharedItemInfo) {
      this.setState({
        showSharedItemInfo: false,
        usersToShare: []
      });
    }
    onCancel && onCancel();
  };

  handleSubmit = (e) => {
    const {mode, item, onSubmit} = this.props;
    e.preventDefault();
    if (mode === MODES.share) {
      onSubmit && onSubmit();
      return;
    }
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        onSubmit && onSubmit(values, item);
      }
    });
  };

  onChangeTab = (key) => {
    this.setState({activeTab: key});
  };

  onChangeUsersToShare = (users) => {
    this.setState({
      usersToShare: (users || []).slice()
    });
  };

  renderInfoForm = () => {
    const {getFieldDecorator} = this.props.form;
    const nameShouldNotBeTheSameValidator = (rule, value, callback) => {
      let error;
      if (this.props.name && value && value.toLowerCase() === this.props.name.toLowerCase()) {
        error = 'Name should not be the same';
      }
      callback(error);
    };
    return (
      <Form>
        <Form.Item {...this.formItemLayout} label="Name">
          {getFieldDecorator('name', {
            rules: [
              {
                required: true,
                message: 'Name is required'
              },
              {
                pattern: /^[\da-zA-Z._\-@ ]+$/,
                message: NAME_VALIDATION_TEXT
              },
              {validator: nameShouldNotBeTheSameValidator}
            ],
            initialValue: this.props.name
          })(
            <Input
              ref={this.initializeNameInput}
              onPressEnter={this.handleSubmit}
              disabled={this.props.pending} />
          )}
        </Form.Item>
        {
          this.props.includeFileContentField &&
          <Form.Item {...this.formItemLayout} label="Content">
            {getFieldDecorator('content')(
              <Input
                disabled={this.props.pending}
                type="textarea"
              />
            )}
          </Form.Item>
        }
      </Form>
    );
  };

  renderSharedItemInfo = () => {
    const {
      storageId,
      item
    } = this.props;
    return (
      <SharedItemInfo
        storageId={storageId}
        item={item}
      />
    );
  };

  renderPermissionsForm = () => {
    const {storageId, mode} = this.props;
    const {usersToShare} = this.state;
    if (!storageId) {
      return null;
    }
    return (
      <PermissionsForm
        objectIdentifier={storageId}
        executeDisabled
        mode={mode}
        usersToShare={usersToShare}
        onChangeUsersToShare={this.onChangeUsersToShare}
        // todo: change objectType to dataStorageItem when item permissions API will be ready
        objectType={OBJECT_TYPES.dataStorage}
      />
    );
  };

  renderModalContent = () => {
    const {tabs} = this.props;
    const renderers = {
      info: this.renderInfoForm,
      permissions: this.renderPermissionsForm
    };
    if (tabs && tabs.length) {
      if (tabs && tabs.length === 1) {
        const renderFn = renderers[tabs[0]];
        return renderFn ? renderFn() : null;
      }
      return (
        <Tabs
          size="small"
          activeKey={this.state.activeTab}
          onChange={this.onChangeTab}
        >
          {tabs.map(tab => {
            const renderFn = renderers[tab];
            return renderFn
              ? (
                <Tabs.TabPane key={tab} tab={capitalizeString(tab)}>
                  {renderFn()}
                </Tabs.TabPane>
              ) : null;
          })}
        </Tabs>
      );
    }
    return this.renderInfoForm();
  };

  render () {
    const {
      activeTab,
      showSharedItemInfo,
      usersToShare
    } = this.state;
    const {
      mode,
      visible,
      pending,
      title,
      form
    } = this.props;
    const {resetFields} = form;
    const modalFooter = pending || activeTab === TABS.permissions
      ? false
      : (
        <Row>
          <Button
            onClick={this.onCancel}
          >
            Cancel
          </Button>
          <Button
            type="primary"
            htmlType="submit"
            disabled={!showSharedItemInfo && usersToShare.length === 0}
            onClick={this.onOk}
          >
            {mode === MODES.share && showSharedItemInfo ? 'SHARE' : 'OK'}
          </Button>
        </Row>
      );
    const onClose = () => {
      resetFields();
    };
    if (!visible) {
      return null;
    }
    return (
      <Modal
        maskClosable={!pending}
        afterClose={() => onClose()}
        closable={!pending}
        visible={visible}
        title={title}
        onCancel={this.onCancel}
        footer={modalFooter}
      >
        <Spin spinning={pending}>
          {mode === MODES.share && showSharedItemInfo
            ? this.renderSharedItemInfo()
            : this.renderModalContent()
          }
        </Spin>
      </Modal>
    );
  }

  initializeNameInput = (input) => {
    if (input && input.refs && input.refs.input) {
      this.nameInput = input.refs.input;
      this.nameInput.onfocus = function () {
        setTimeout(() => {
          this.selectionStart = (this.value || '').length;
          this.selectionEnd = (this.value || '').length;
        }, 0);
      };
    }
  };

  focusNameInput = () => {
    if (this.props.visible && this.nameInput) {
      setTimeout(() => {
        this.nameInput.focus();
      }, 0);
    }
  };

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.onChangeTab(TABS.info);
      this.focusNameInput();
    }
  }
}

export {TABS, MODES};
export default EditItemForm;
