import React from 'react';
import PermissionsForm from '../../roleModel/PermissionsForm';
import {Modal, Button, Icon} from 'antd';

export default class EditSystemDictionaryPermissions extends React.Component {
    state = {
      visible: false,
      objectId: undefined,
      isNew: false
    }
    componentDidMount () {
      this.updateState();
    }
    componentDidUpdate (prevProps) {
      if (
        prevProps.isNew !== this.props.isNew ||
        prevProps.objectId !== this.props.objectId
      ) {
        this.updateState();
      }
    }

    updateState = () => {
      const {objectId, isNew} = this.props;
      this.setState({
        objectId,
        isNew
      });
    };

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
            objectIdentifier={this.state.objectId}
          />
        </Modal>
      );
    };
    render () {
      const {objectId, isNew} = this.state;
      if (objectId && !isNew) {
        return (
          <Button
            id="permissions-setting-button"
            style={{marginLeft: 5, height: 28}}
            size="small"
            onClick={() => this.showModalWindow()}
          >
            <Icon type="setting" style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
            {this.renderModalWindow()}
          </Button>
        );
      }
      return null;
    }
}
