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
import {Form, Input, Spin} from 'antd';
import {inject, observer} from 'mobx-react';
import connect from '../../../../utils/connect';
import roleModel from '../../../../utils/roleModel';
import PropTypes from 'prop-types';
import Folder from '../Folder';
import {generateTreeData, getTreeItemByKey, ItemTypes} from '../../model/treeStructureFunctions';
import pipelinesLibrary from '../../../../models/folders/FolderLoadTree';
import HiddenObjects from '../../../../utils/hidden-objects';

@connect({
  pipelinesLibrary,
})
@HiddenObjects.injectTreeFilter
@inject(({pipelinesLibrary}) => {
  return {
    pipelinesLibrary,
  };
})
@observer
export default class CloneForm extends React.Component {
  formRef = React.createRef();
  static propTypes = {
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    parentId: PropTypes.number,
    onFolderChange: PropTypes.func,
    formRef: PropTypes.object,
  };

  state = {
    value: null,
    loaded: false,
  };

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 4},
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 20},
    },
  };

  onSelectFolder = (folder) => {
    if (folder.key === `${ItemTypes.folder}_root`) {
      this.setState({value: null});
      this.props.onFolderChange?.(null);
    } else {
      const tree = generateTreeData(this.props.pipelinesLibrary.value, {
        types: [ItemTypes.folder],
        filter: this.props.hiddenObjectsTreeFilter(),
      });
      const foundFolder = getTreeItemByKey(folder.key, tree);
      this.setState({value: foundFolder || folder});
      this.props.onFolderChange?.(foundFolder || folder);
    }
  };

  handleFinish = (values) => {
    if (this.props.onSubmit) {
      this.props.onSubmit(this.state.value ? this.state.value.id : null, values.name);
    }
  };

  render() {
    return (
      <Form
        ref={this.formRef}
        scrollToFirstError={{behavior: 'smooth', block: 'end', focus: true}}
        onFinish={this.handleFinish}
      >
        <Spin spinning={this.props.pending}>
          <div
            style={{height: '50vh', display: 'flex', flexDirection: 'column', overflow: 'auto'}}
          >
            <Folder
              id={this.state.value ? this.state.value.id : null}
              onSelectItem={this.onSelectFolder}
              listingMode
              readOnly
              supportedTypes={[ItemTypes.folder]}
            />
          </div>
        </Spin>
        <Form.Item
          style={{padding: '5px'}}
          {...this.formItemLayout}
          label="Name"
          name="name"
          rules={[{required: true, message: 'Name is required'}]}
        >
          <Input />
        </Form.Item>
      </Form>
    );
  }

  updateState = (props) => {
    if (props.parentId && this.props.pipelinesLibrary.loaded) {
      const tree = generateTreeData(this.props.pipelinesLibrary.value, {
        types: [ItemTypes.folder],
        filter: this.props.hiddenObjectsTreeFilter(),
      });
      const folder = getTreeItemByKey(`${ItemTypes.folder}_${props.parentId}`, tree);
      this.setState({value: folder});
      this.props.onFolderChange?.(folder || null);
    } else {
      this.setState({value: null});
      this.props.onFolderChange?.(null);
    }
  };

  UNSAFE_componentWillReceiveProps(nextProps) {
    if (nextProps.parentId !== this.props.parentId) {
      this.updateState(nextProps);
    }
  }

  componentDidMount() {
    this.updateState(this.props);
    if (this.props.formRef) {
      this.props.formRef.current = this.formRef.current;
    }
  }

  componentDidUpdate(prevProps) {
    if (this.props.formRef && this.props.formRef !== prevProps.formRef) {
      this.props.formRef.current = this.formRef.current;
    }
    if (prevProps.visible && !this.props.visible) {
      this.setState({value: null, loaded: false});
      this.formRef.current?.resetFields();
      this.props.onFolderChange?.(null);
    }
    if (this.props.pipelinesLibrary.loaded && this.props.parentId && !this.state.loaded) {
      this.setState({loaded: true}, () => this.updateState(this.props));
    }
  }
}
