import {Checkbox, Col, Form, Input, InputNumber, Row, Select} from 'antd';
import {CloudRegionTag} from '../../../cloud-region-tag/cloud-region-tag.tsx';
import {DataStoragePathInput} from '../../../../pipelines/browser/forms/data-storage-path-input/index.tsx';
import RestrictDockerImages from '../../../../pipelines/browser/forms/restrict-docker-images';
import type {DataStorageEditController} from './use-data-storage-edit-controller.ts';

const FORM_ITEM_LAYOUT = {
  labelCol: {xs: {span: 24}, sm: {span: 6}},
  wrapperCol: {xs: {span: 24}, sm: {span: 18}},
};

interface InfoTabProps {
  ctrl: DataStorageEditController;
  isNew: boolean;
  policySupported?: boolean;
  addExistingStorageFlag?: boolean;
  visible?: boolean;
  pending?: boolean;
}

function InfoTab({
  ctrl,
  isNew,
  policySupported,
  addExistingStorageFlag,
  visible,
  pending,
}: InfoTabProps) {
  const {
    isNfsMount,
    omicsStore,
    isReadOnly,
    mountDisabled,
    versioningEnabled,
    pathPermissionsEnabled,
    sharingEnabled,
    sensitive,
    skipPolicy,
    currentRegionSupportsPolicy,
    currentRegionSupportsStoragePermissions,
    storageVersioningAllowed,
    skipPolicyFlagVisible,
    awsRegions,
    omicsTypes,
    validateStoragePath,
    validateAlias,
    handleSubmit,
    setNfsStoragePathValid,
    setMountDisabled,
    setSensitive,
    setSkipPolicy,
    setVersioningEnabled,
    setPathPermissionsEnabled,
    setSharingEnabled,
    setOmicsType,
  } = ctrl;

  const disabled = !!(pending || isReadOnly);

  return (
    <>
      {!omicsStore && (
        <Form.Item
          className="edit-storage-storage-path-container"
          {...FORM_ITEM_LAYOUT}
          label="Storage path"
          name="path"
          rules={[{validator: validateStoragePath}]}
        >
          <DataStoragePathInput
            cloudRegions={awsRegions}
            onValidation={setNfsStoragePathValid}
            onPressEnter={handleSubmit}
            visible={visible}
            isFS={isNfsMount}
            isNew={isNew}
            addExistingStorageFlag={addExistingStorageFlag}
            disabled={pending || !isNew || isReadOnly}
          />
        </Form.Item>
      )}
      <Form.Item
        {...FORM_ITEM_LAYOUT}
        label="Alias"
        name="name"
        rules={[{validator: validateAlias}]}
      >
        <Input onPressEnter={handleSubmit} disabled={disabled} />
      </Form.Item>
      {omicsStore && (
        <Form.Item {...FORM_ITEM_LAYOUT} label="Service type" name="omicsType">
          <Select
            style={{width: '100%'}}
            disabled={!isNew || isReadOnly}
            onChange={(type) => setOmicsType(type as string)}
            options={omicsTypes.map(([value, label]) => ({value, label}))}
          />
        </Form.Item>
      )}
      {!isNfsMount && (
        <Form.Item {...FORM_ITEM_LAYOUT} label="Cloud region" name="regionId">
          <Select
            style={{width: '100%'}}
            disabled={!isNew || isReadOnly}
            options={awsRegions
              .filter((region) => !omicsStore || region.provider === 'AWS')
              .map((region) => ({
                value: region.id.toString(),
                label: (
                  <>
                    <CloudRegionTag regionId={region.regionId} displayName={false} /> {region.name}
                  </>
                ),
              }))}
          />
        </Form.Item>
      )}
      <Form.Item {...FORM_ITEM_LAYOUT} label="Description" name="description">
        <Input type="textarea" disabled={disabled} />
      </Form.Item>
      {!omicsStore && (
        <Row>
          <Col xs={24} sm={6} />
          <Col xs={24} sm={18}>
            <Form.Item>
              <Checkbox
                disabled={disabled}
                onChange={(e) => setMountDisabled(e.target.checked)}
                checked={mountDisabled}
              >
                Disable mount
              </Checkbox>
            </Form.Item>
          </Col>
        </Row>
      )}
      {!omicsStore && !mountDisabled && (
        <Form.Item {...FORM_ITEM_LAYOUT} label="Allow mount to" name="toolsToMount">
          <RestrictDockerImages disabled={disabled} />
        </Form.Item>
      )}
      {!omicsStore && !isNfsMount && (
        <Row>
          <Col xs={24} sm={6} />
          <Col xs={24} sm={18}>
            <Form.Item>
              <Checkbox
                disabled={pending || isReadOnly || !isNew}
                onChange={(e) => setSensitive(e.target.checked)}
                checked={sensitive}
              >
                Sensitive storage
              </Checkbox>
            </Form.Item>
          </Col>
        </Row>
      )}
      {!omicsStore && !isNfsMount && skipPolicyFlagVisible && (
        <Row>
          <Col xs={24} sm={6} />
          <Col xs={24} sm={18}>
            <Form.Item>
              <Checkbox
                disabled={pending || isReadOnly || !isNew}
                onChange={(e) => setSkipPolicy(e.target.checked)}
                checked={skipPolicy}
              >
                Skip policy
              </Checkbox>
            </Form.Item>
          </Col>
        </Row>
      )}
      {!omicsStore &&
        !isNfsMount &&
        policySupported &&
        currentRegionSupportsPolicy &&
        storageVersioningAllowed && (
          <Row>
            <Col xs={24} sm={6} />
            <Col xs={24} sm={18}>
              <Form.Item>
                <Checkbox
                  disabled={pending || isReadOnly || skipPolicy}
                  onChange={(e) => setVersioningEnabled(e.target.checked)}
                  checked={versioningEnabled}
                >
                  Enable versioning
                </Checkbox>
              </Form.Item>
            </Col>
          </Row>
        )}
      {!omicsStore && !isNfsMount && currentRegionSupportsStoragePermissions && (
        <Row>
          <Col xs={24} sm={6} />
          <Col xs={24} sm={18}>
            <Form.Item>
              <Checkbox
                disabled={pending || isReadOnly || !isNew}
                onChange={(e) => setPathPermissionsEnabled(e.target.checked)}
                checked={pathPermissionsEnabled}
              >
                Fine-grained permissions
              </Checkbox>
            </Form.Item>
          </Col>
        </Row>
      )}
      {!omicsStore &&
        !isNfsMount &&
        policySupported &&
        versioningEnabled &&
        currentRegionSupportsPolicy &&
        storageVersioningAllowed && (
          <Form.Item {...FORM_ITEM_LAYOUT} label="Backup duration" name="backupDuration">
            <InputNumber style={{width: '100%'}} disabled={pending || isReadOnly || skipPolicy} />
          </Form.Item>
        )}
      {!omicsStore && !mountDisabled && (
        <Form.Item {...FORM_ITEM_LAYOUT} label="Mount-point" name="mountPoint">
          <Input style={{width: '100%'}} disabled={disabled} />
        </Form.Item>
      )}
      {!omicsStore && !mountDisabled && (
        <Form.Item {...FORM_ITEM_LAYOUT} label="Mount options" name="mountOptions">
          <Input style={{width: '100%'}} disabled={disabled} />
        </Form.Item>
      )}
      {!omicsStore && !isNfsMount && ((isNew && !addExistingStorageFlag) || !isNew) && (
        <Row>
          <Col xs={24} sm={6} />
          <Col xs={24} sm={18}>
            <Form.Item>
              <Checkbox
                disabled={!isNew || isReadOnly}
                onChange={(e) => setSharingEnabled(e.target.checked)}
                checked={sharingEnabled}
              >
                Enable sharing
              </Checkbox>
            </Form.Item>
          </Col>
        </Row>
      )}
    </>
  );
}

export {InfoTab};
