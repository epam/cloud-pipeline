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
import {Button, Form, Input, Modal, Row, Spin} from 'antd';
import {inject, observer} from 'mobx-react';
import connect from '../../../../utils/connect';
import roleModel from '../../../../utils/roleModel';
import PropTypes from 'prop-types';
import Folder from '../Folder';
import {generateTreeData, getTreeItemByKey, ItemTypes} from '../../model/treeStructureFunctions';
import pipelinesLibrary from '../../../../models/folders/FolderLoadTree';

@connect({
  pipelinesLibrary
})
@inject(({pipelinesLibrary}) => {
  return {
    pipelinesLibrary
  };
})
@Form.create()
@observer
export default class CloneForm extends React.Component {
  static propTypes = {
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    parentId: PropTypes.number
  };

  state = {
    value: null,
    loaded: false
  };

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 2}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 22}
    }
  };

  onSelectFolder = (folder) => {
    if (folder.key === `${ItemTypes.folder}_root`) {
      this.setState({
        value: null
      });
    } else {
      const tree = generateTreeData(
        this.props.pipelinesLibrary.value,
        false,
        null,
        [],
        [ItemTypes.folder]
      );
      const foundFolder = getTreeItemByKey(folder.key, tree);
      this.setState({
        value: foundFolder || folder
      });
    }
  };

  handleSubmit = (e) => {
    e.preventDefault();
    this.props.form.validateFieldsAndScroll(async (err, values) => {
      if (!err) {
        this.props.onSubmit &&
        await this.props.onSubmit(this.state.value ? this.state.value.id : null, values.name);
      }
    });
  }

  render () {
    const modalFooter = this.props.pending ? false : (
      <Row>
        <Button
          id="folder-clone-form-cancel-button"
          onClick={this.props.onCancel}>Cancel</Button>
        <Button
          id="folder-clone-form-ok-button"
          disabled={
            this.state.value
              ? !roleModel.writeAllowed(this.state.value)
              : !roleModel.writeAllowed(this.props.pipelinesLibrary.value)
          }
          type="primary"
          onClick={this.handleSubmit}>Clone{this.state.value ? ` into '${this.state.value.name}'` : ' into Library'}</Button>
      </Row>
    );

    const onClose = () => {
      this.setState({
        value: null,
        loaded: false
      });
    };
    const {getFieldDecorator} = this.props.form;
    return (
      <Modal
        maskClosable={!this.props.pending}
        afterClose={onClose}
        closable={!this.props.pending}
        visible={this.props.visible}
        title="Select destination folder"
        width="50%"
        onCancel={this.props.onCancel}
        footer={modalFooter}>
        <Form>
          <Spin spinning={this.props.pending}>
            <div style={{height: '50vh', display: 'flex', flexDirection: 'column', overflow: 'auto'}}>
              <Folder
                id={this.state.value ? this.state.value.id : null}
                onSelectItem={this.onSelectFolder}
                listingMode={true}
                readOnly={true}
                supportedTypes={[ItemTypes.folder]} />
            </div>
          </Spin>
          <Form.Item
            style={{padding: '5px'}}
            {...this.formItemLayout}
            label="Name">
            {getFieldDecorator('name', {
              rules: [
                {required: true, message: 'Name is required'}
              ]
            })(
              <Input />
            )}
          </Form.Item>
        </Form>
      </Modal>
    );
  }

  updateState = (props) => {
    if (props.parentId && this.props.pipelinesLibrary.loaded) {
      const tree = generateTreeData(
        this.props.pipelinesLibrary.value,
        false,
        null,
        [],
        [ItemTypes.folder]
      );
      const folder = getTreeItemByKey(`${ItemTypes.folder}_${props.parentId}`, tree);
      this.setState({
        value: folder
      });
    } else {
      this.setState({
        value: null
      });
    }
  };

  componentWillReceiveProps (nextProps) {
    if (nextProps.parentId !== this.props.parentId) {
      this.updateState(nextProps);
    }
  }

  componentDidMount () {
    this.updateState(this.props);
  }

  componentDidUpdate () {
    if (this.props.pipelinesLibrary.loaded && this.props.parentId && !this.state.loaded) {
      this.setState({
        loaded: true
      }, () => this.updateState(this.props));
    }
  }
}
