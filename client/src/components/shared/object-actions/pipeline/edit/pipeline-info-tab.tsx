import {Form, FormInstance, Input, Select} from 'antd';
import {MouseEvent, KeyboardEvent} from 'react';
import {Pipeline} from '../../../../../@types/library.ts';
import {preventDefaultAndStopPropagation} from '../../../../../utilities/callbacks.ts';
import RepositorySection from './repository-section.tsx';
import {
  PipelineFormValues,
  formItemLayout,
  formItemStyle,
  getInitialValues,
} from './pipeline-edit-form-utils.ts';

type PipelineInfoTabProps = {
  form: FormInstance<PipelineFormValues>;
  pipeline: Pipeline | undefined;
  pipelineId: number | undefined;
  isVersionedStorage: boolean;
  githubType: string;
  onGithubTypeChange: (type: string) => void;
  pending: boolean;
  readOnly: boolean;
  showRepoSettings: boolean;
  onShowRepoSettings: (event: MouseEvent | KeyboardEvent) => void;
};

function PipelineInfoTab(props: PipelineInfoTabProps) {
  const {
    form,
    pipeline,
    pipelineId,
    isVersionedStorage,
    githubType,
    onGithubTypeChange,
    pending,
    readOnly,
    showRepoSettings,
    onShowRepoSettings,
  } = props;

  return (
    <Form
      form={form}
      initialValues={getInitialValues(pipeline)}
      key={pipelineId ?? 'new'}
      onClick={preventDefaultAndStopPropagation}
    >
      <Form.Item
        {...formItemLayout}
        style={formItemStyle}
        label={isVersionedStorage ? 'Name:' : 'Pipeline name'}
        name="name"
        rules={[
          {
            required: true,
            message: `${isVersionedStorage ? 'Versioned storage' : 'Pipeline'} name is required`,
          },
        ]}
      >
        <Input disabled={pending || readOnly} />
      </Form.Item>
      <Form.Item
        {...formItemLayout}
        style={formItemStyle}
        label={isVersionedStorage ? 'Description:' : 'Pipeline description'}
        name="description"
      >
        <Input.TextArea autoSize={{minRows: 2, maxRows: 6}} disabled={pending || readOnly} />
      </Form.Item>
      {!isVersionedStorage && (
        <>
          <Form.Item {...formItemLayout} style={formItemStyle} label="Visibility" name="visibility">
            <Select allowClear disabled={pending || readOnly}>
              <Select.Option value="INHERIT">Inherit</Select.Option>
              <Select.Option value="OWNER">Owner</Select.Option>
            </Select>
          </Form.Item>
          <RepositorySection
            form={form}
            formItemLayout={formItemLayout}
            formItemStyle={formItemStyle}
            githubType={githubType}
            onGithubTypeChange={onGithubTypeChange}
            pipeline={pipeline}
            pending={pending}
            readOnly={readOnly}
            showRepoSettings={showRepoSettings}
            onShowRepoSettings={onShowRepoSettings}
          />
        </>
      )}
    </Form>
  );
}

export {PipelineInfoTab};
