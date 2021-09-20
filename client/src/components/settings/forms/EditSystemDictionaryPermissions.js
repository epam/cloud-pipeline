import React from 'react';
import PermissionsForm from '../../roleModel/PermissionsForm';
import {SettingOutlined} from '@ant-design/icons';
import {Modal, Button} from 'antd';

export default class EditSystemDictionaryPermissions extends React.Component {
    state = {
      visible: false
    }

    showModalWindow = () => {
      this.setState({visible: true});
    }

    closeModalWindow = () => {
      this.setState({visible: false});
    };
    renderModalWindow = () => {
      return (
        <Modal
          closable
          visible={this.state.visible}
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
    render () {
      if (this.props.objectId) {
        return (
          <Button
            id="permissions-setting-button"
            size="small"
            onClick={() => this.showModalWindow()}
          >
            <SettingOutlined style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
            {this.renderModalWindow()}
          </Button>
        );
      }
      return null;
    }
}
