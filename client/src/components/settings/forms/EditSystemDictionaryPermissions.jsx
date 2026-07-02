import React from 'react';
import {PermissionsForm} from '../../shared/permissions-form';
import {Modal, Button} from 'antd';
import {SettingOutlined} from '@ant-design/icons';

export default class EditSystemDictionaryPermissions extends React.Component {
  state = {
    visible: false,
  };

  showModalWindow = () => {
    this.setState({visible: true});
  };

  closeModalWindow = () => {
    this.setState({visible: false});
  };

  renderModalWindow = () => {
    return (
      <Modal
        closable
        open={this.state.visible}
        title="Permissions"
        onCancel={this.closeModalWindow}
        footer={false}
      >
        <PermissionsForm
          objectType="CATEGORICAL_ATTRIBUTE"
          objectIdentifier={this.props.objectId}
        />
      </Modal>
    );
  };

  render() {
    if (this.props.objectId) {
      return (
        <>
          <Button id="permissions-setting-button" size="small" onClick={() => this.showModalWindow()}>
            <SettingOutlined style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
          </Button>
          {this.renderModalWindow()}
        </>
      );
    }
    return null;
  }
}
