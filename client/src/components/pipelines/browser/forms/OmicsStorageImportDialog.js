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
import PropTypes from 'prop-types';
import {computed} from 'mobx';
import {
  Button,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Spin,
  Icon
} from 'antd';
import roleModel from '../../../../utils/roleModel';
import BucketBrowser from '../../launch/dialogs/BucketBrowser';
import dataStorageRestrictedAccessCheck from '../../../../utils/data-storage-restricted-access';
import styles from './OmicsStorageImportDialog.css';

export const ServiceTypes = {
  omicsRef: 'AWS_OMICS_REF',
  omicsSeq: 'AWS_OMICS_SEQ'
};

const FileTypes = {
  [ServiceTypes.omicsRef]: {
    REFERENCE: 'REFERENCE'
  },
  [ServiceTypes.omicsSeq]: {
    FASTQ: 'FASTQ',
    BAM: 'BAM',
    UBAM: 'UBAM',
    CRAM: 'CRAM'
  }
};

const Fields = {
  name: 'name',
  subjectId: 'subjectId',
  sampleId: 'sampleId',
  description: 'description',
  generatedFrom: 'generatedFrom',
  sourceFileType: 'sourceFileType',
  sourceFile1: 'sourceFile1',
  sourceFile2: 'sourceFile2',
  referencePath: 'referencePath'
};

const FieldLabel = {
  [Fields.name]: 'Name',
  [Fields.subjectId]: 'Subject id',
  [Fields.sampleId]: 'Sample id',
  [Fields.description]: 'Description',
  [Fields.generatedFrom]: 'Generated from',
  [Fields.sourceFileType]: 'Source file type',
  [Fields.sourceFile1]: 'Source file',
  [Fields.sourceFile2]: 'Source file 2',
  [Fields.referencePath]: 'Reference path'
};

const FieldValid = {
  [Fields.name]: 'nameValid',
  [Fields.subjectId]: 'subjectIdValid',
  [Fields.sampleId]: 'sampleIdValid',
  [Fields.sourceFileType]: 'sourceFileTypeValid',
  [Fields.sourceFile1]: 'sourceFile1Valid',
  [Fields.referencePath]: 'referencePathValid'
};

const CommonRequiredFields = [
  Fields.name,
  Fields.subjectId,
  Fields.sampleId,
  Fields.sourceFileType,
  Fields.sourceFile1
];

const PlaceHolder = {
  [Fields.sourceFile1]: 'Source',
  [Fields.sourceFile2]: 'Source',
  [Fields.referencePath]: 'Reference'
};

@roleModel.authenticationInfo
@Form.create()
export class OmicsStorageImportDialog extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    dataStorage: PropTypes.object,
    pending: PropTypes.bool,
    policySupported: PropTypes.bool,
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func
  };

  state = {
    nameValid: false,
    subjectIdValid: false,
    sampleIdValid: false,
    sourceFileTypeValid: this.isReferenceStorage,
    restrictedAccessCheckInProgress: false,
    restrictedAccess: true,
    bucketBrowserVisible: false
  };

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

  @computed
  get sourceFileTypes () {
    return Object.values(FileTypes[this.storageType] || {}) || [];
  }

  @computed
  get storageType () {
    return this.props.dataStorage && this.props.dataStorage.type;
  }

  get isSequenceStorage () {
    return this.storageType === ServiceTypes.omicsSeq;
  }

  get isReferenceStorage () {
    return this.storageType === ServiceTypes.omicsRef;
  }

  get isReferencePathRequired () {
    return this.isSequenceStorage &&
      (this.state.sourceFileType === FileTypes[ServiceTypes.omicsSeq].BAM ||
      this.state.sourceFileType === FileTypes[ServiceTypes.omicsSeq].CRAM);
  }

  get requiredFields () {
    if (this.isSequenceStorage && this.isReferencePathRequired) {
      return [...CommonRequiredFields, Fields.referencePath];
    }
    return CommonRequiredFields;
  }

  get isImportButtonDisabled () {
    return this.requiredFields.some(field => {
      return !this.state[FieldValid[field]];
    });
  }

  get isFASTQ () {
    return this.state.sourceFileType === FileTypes[ServiceTypes.omicsSeq].FASTQ;
  }

  handleSubmit = (e) => {
    e && e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        const sources = {
          name: values.name,
          subjectId: values.subjectId,
          sampleId: values.sampleId,
          sourceFileType: values.sourceFileType,
          sourceFiles: {
            source1: values.sourceFile1
          }
        };
        if (values.description) {
          sources.description = values.description;
        }
        if (values.generatedFrom) {
          sources.generatedFrom = values.generatedFrom;
        }
        if (this.isFASTQ && values.sourceFile2) {
          sources.sourceFiles.source2 = values.sourceFile2;
        }
        if (values.referencePath) {
          sources.referencePath = values.referencePath;
        }
        this.props.onSubmit(sources);
      }
    });
  };

  validateRequiredField = (field, value, callback) => {
    if (!value) {
      this.setState({[FieldValid[field]]: false});
      // eslint-disable-next-line standard/no-callback-literal
      callback(`${FieldLabel[field]} is required`);
    } else {
      this.setState({[FieldValid[field]]: true});
    }
    callback();
  }

  renderRequiredStringField = (field) => {
    const {getFieldDecorator} = this.props.form;
    return (
      <Form.Item
        className={styles.omicsStorageFormItem}
        {...this.formItemLayout}
        label={FieldLabel[field]}>
        {getFieldDecorator(field, {
          initialValue: undefined,
          rules: [{
            required: true,
            validator: (rule, value, callback) => this.validateRequiredField(
              field,
              value,
              callback
            )
          }]
        })(
          <Input
            style={{width: '100%'}}
            disabled={this.props.pending} />
        )}
      </Form.Item>
    );
  }

  renderFileInput = (field) => {
    const {getFieldDecorator} = this.props.form;
    const isSourceFile1 = field === Fields.sourceFile1;
    const isReferencePath = field === Fields.referencePath;
    const rules = isSourceFile1 || (isReferencePath && this.isReferencePathRequired)
      ? ([{
        required: true,
        validator: (rule, value, callback) => (
          this.validateRequiredField(field, value, callback)
        )
      }])
      : undefined;
    const label = (isSourceFile1 &&
      this.isFASTQ &&
      this.props.form.getFieldValue([Fields.sourceFile1]))
      ? `${FieldLabel[field]} 1` : FieldLabel[field];
    return (
      <Form.Item
        className={styles.omicsStorageFormItem}
        {...this.formItemLayout}
        label={label}>
        {getFieldDecorator(field, {
          initialValue: undefined,
          rules
        })(
          <Input
            style={{width: '100%'}}
            disabled={this.props.pending}
            placeholder={PlaceHolder[field]}
            addonBefore={
              <div
                className={styles.pathType}
                onClick={() => this.openBucketBrowser(field)}>
                <Icon type="folder" />
              </div>
            }
          />
        )}
      </Form.Item>
    );
  }

  getFooter = () => {
    const getCancelFooter = () => {
      return (
        <Row>
          <Button
            id="omics-storage-dialog-cancel-button"
            onClick={this.props.onCancel}
          >
            Cancel
          </Button>
        </Row>
      );
    };
    const getImportFooter = () => {
      return (
        <Row>
          <Button
            id="omics-storage-dialog-cancel-button"
            onClick={this.props.onCancel}
          >
            Cancel
          </Button>
          <Button
            id="omics-storage-dialog-create-button"
            type="primary"
            htmlType="submit"
            disabled={this.isImportButtonDisabled}
            onClick={this.handleSubmit}
          >
            Import
          </Button>
        </Row>
      );
    };
    const footer = (this.props.pending || this.state.restrictedAccessCheckInProgress)
      ? false
      : (this.props.dataStorage &&
        !this.props.dataStorage.locked &&
        !this.state.restrictedAccess
        ? getImportFooter()
        : getCancelFooter()
      );
    return footer;
  };

  render () {
    const {getFieldDecorator, resetFields} = this.props.form;
    const bucketTypes = ['S3'];
    const refType = ['AWS_OMICS_REF'];
    const title = this.storageType === ServiceTypes.omicsRef
      ? 'Import to reference store'
      : 'Import to sequence store';
    return (
      <Modal
        maskClosable={!this.props.pending && !this.state.restrictedAccessCheckInProgress}
        afterClose={() => resetFields()}
        closable={!this.props.pending && !this.state.restrictedAccessCheckInProgress}
        visible={this.props.visible}
        title={title}
        onCancel={this.props.onCancel}
        style={{transition: 'width 0.2s ease'}}
        width={'50%'}
        footer={this.getFooter()}>
        <Spin spinning={this.props.pending || this.state.restrictedAccessCheckInProgress}>
          <Form id="edit-storage-form">
            {this.renderRequiredStringField(Fields.name)}
            {this.renderRequiredStringField(Fields.subjectId)}
            {this.renderRequiredStringField(Fields.sampleId)}
            <Form.Item
              className={styles.omicsStorageFormItem}
              {...this.formItemLayout}
              label={FieldLabel[Fields.description]}>
              {getFieldDecorator(Fields.description, {
                initialValue: undefined
              })(
                <Input
                  type="textarea"
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              className={styles.omicsStorageFormItem}
              {...this.formItemLayout}
              label={FieldLabel[Fields.generatedFrom]}>
              {getFieldDecorator(Fields.generatedFrom, {
                initialValue: undefined
              })(
                <Input
                  style={{width: '100%'}}
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              className={styles.omicsStorageFormItem}
              {...this.formItemLayout}
              label={FieldLabel[Fields.sourceFileType]}>
              {getFieldDecorator(Fields.sourceFileType, {
                initialValue: this.isReferenceStorage
                  ? FileTypes[ServiceTypes.omicsRef].REFERENCE : undefined,
                rules: [{
                  required: true,
                  validator: (rule, value, callback) => (
                    this.validateRequiredField(Fields.sourceFileType, value, callback)
                  )
                }]
              })(
                <Select
                  style={{width: '100%'}}
                  disabled={!this.storageType}
                  onChange={(type) => this.setState({sourceFileType: type})}
                >
                  {this.sourceFileTypes.map((type) => {
                    return (
                      <Select.Option key={type} title={type}>
                        {type}
                      </Select.Option>
                    );
                  })}
                </Select>
              )}
            </Form.Item>
            {this.renderFileInput(Fields.sourceFile1)}
            {
              this.isFASTQ &&
              this.props.form.getFieldValue([Fields.sourceFile1]) &&
              this.renderFileInput(Fields.sourceFile2)
            }
            {this.isSequenceStorage && this.renderFileInput(Fields.referencePath)}
            <BucketBrowser
              onSelect={this.selectBucketPath}
              onCancel={this.closeBucketBrowser}
              visible={this.state.sourceBrowserVisible}
              selectOnlyFiles
              checkWritePermissions
              bucketTypes={bucketTypes} />
            <BucketBrowser
              onSelect={this.selectBucketPath}
              onCancel={this.closeBucketBrowser}
              visible={this.state.referenceBrowserVisible}
              showOnlyFolder
              checkWritePermissions
              bucketTypes={refType} />
          </Form>
        </Spin>
      </Modal>
    );
  }

  openBucketBrowser = (field) => {
    if (field === Fields.referencePath) {
      this.setState({
        referenceBrowserVisible: true,
        referencePathField: field
      });
    } else {
      this.setState({
        sourceBrowserVisible: true,
        sourceFileField: field
      });
    }
  };

  closeBucketBrowser = () => {
    this.setState({
      sourceBrowserVisible: false,
      sourceFileField: null,
      referenceBrowserVisible: false,
      referencePathField: null
    });
  };

  selectBucketPath = (path) => {
    const {sourceFileField, referencePathField} = this.state;
    if (sourceFileField) {
      this.props.form.setFieldsValue({[Fields[sourceFileField]]: path});
    }
    if (referencePathField) {
      this.props.form.setFieldsValue({[Fields[referencePathField]]: path});
    }
    this.props.form.validateFieldsAndScroll();
    this.closeBucketBrowser();
  };

  componentDidMount () {
    this.checkRestrictedAccess();
  }

  componentDidUpdate (prevProps) {
    const dataStorageChanged = (a, b) => {
      const {
        id: aID
      } = a || {};
      const {
        id: bID
      } = b || {};
      return aID !== bID;
    };
    if (dataStorageChanged(this.props.dataStorage, prevProps.dataStorage)) {
      this.checkRestrictedAccess();
    }
  }

  componentWillUnmount () {
    this.increaseCheckRestrictedAccessToken();
  }

  increaseCheckRestrictedAccessToken = () => {
    this.checkRestrictedAccessToken = (this.checkRestrictedAccessToken || 0) + 1;
    return this.checkRestrictedAccessToken;
  };

  checkRestrictedAccess = () => {
    const {dataStorage} = this.props;
    const {id} = dataStorage || {};
    const token = this.increaseCheckRestrictedAccessToken();
    this.setState({
      restrictedAccessCheckInProgress: true,
      restrictedAccess: true
    }, async () => {
      const state = {
        restrictedAccessCheckInProgress: false
      };
      try {
        state.restrictedAccess = await dataStorageRestrictedAccessCheck(id);
      } catch (_) {
        state.restrictedAccess = true;
      } finally {
        if (token === this.checkRestrictedAccessToken) {
          if (state.restrictedAccess) {
            console.log(`Storage #${id} is in the restricted access mode for current user`);
          }
          this.setState(state);
        }
      }
    });
  };
}
