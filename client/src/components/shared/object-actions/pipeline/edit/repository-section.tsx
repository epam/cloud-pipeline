import {Button, Checkbox, Form, FormInstance, Input, Select, Spin} from 'antd';
import {useCallback, useEffect, useState, MouseEvent, KeyboardEvent} from 'react';
import {
  RepositoryTypes,
  RepositoryTypeNames,
  availableRepositoryTypes,
  normalizeRepositoryType,
} from '../../../../special/git-repository-control';
import cloudPipelineApi from '../../../../../api/cloud-pipeline-api.ts';
import {Pipeline} from '../../../../../@types/library.ts';

type FormLayout = {
  labelCol: object;
  wrapperCol: object;
};

type Namespace = {id: string | number; name?: string};
type Repository = {httpUrl?: string; name?: string};

type RepositorySectionProps = {
  form: FormInstance;
  formItemLayout: FormLayout;
  formItemStyle: object;
  githubType: string;
  onGithubTypeChange: (type: string) => void;
  pipeline?: Pipeline;
  pending: boolean;
  readOnly: boolean;
  showRepoSettings: boolean;
  onShowRepoSettings: (event: MouseEvent | KeyboardEvent) => void;
};

function useGitHubNamespaces(active: boolean) {
  const [namespaces, setNamespaces] = useState<Namespace[]>([]);
  const [namespacePending, setNamespacePending] = useState(false);

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    setNamespacePending(true);
    cloudPipelineApi
      .jsonGet<Namespace[]>({
        uri: `pipeline/git/namespaces`,
        query: {type: RepositoryTypes.GitHubApp},
      })
      .then((data) => {
        if (!cancelled) setNamespaces(data ?? []);
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setNamespacePending(false);
      });
    return () => {
      cancelled = true;
    };
  }, [active]);

  return {namespaces, namespacePending};
}

function useGitHubRepositories(namespaceId: string | number | undefined) {
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [repoPending, setRepoPending] = useState(false);

  useEffect(() => {
    if (!namespaceId) {
      setRepositories([]);
      return;
    }
    let cancelled = false;
    setRepoPending(true);
    cloudPipelineApi
      .jsonGet<Repository[]>({
        uri: `pipeline/git/${encodeURIComponent(namespaceId)}/repositories`,
        query: {type: RepositoryTypes.GitHubApp},
      })
      .then((data) => {
        if (!cancelled) setRepositories(data ?? []);
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setRepoPending(false);
      });
    return () => {
      cancelled = true;
    };
  }, [namespaceId]);

  return {repositories, repoPending};
}

function GitHubAppForm({
  form,
  formItemLayout,
  formItemStyle,
  pending,
  pipeline,
  readOnly,
}: Pick<
  RepositorySectionProps,
  'form' | 'formItemLayout' | 'formItemStyle' | 'pending' | 'pipeline' | 'readOnly'
>) {
  const isExisting = !!pipeline?.repository;
  const [selectedNamespace, setSelectedNamespace] = useState<string | number | undefined>(
    undefined,
  );
  const {namespaces, namespacePending} = useGitHubNamespaces(!isExisting);
  const {repositories, repoPending} = useGitHubRepositories(selectedNamespace);

  const onNamespaceChange = useCallback(
    (id: string | number) => {
      setSelectedNamespace(id);
      form.setFieldsValue({githubRepository: undefined});
    },
    [form],
  );

  if (isExisting) {
    return (
      <Form.Item
        {...formItemLayout}
        style={formItemStyle}
        label="GitHub repository"
        name="githubRepository"
      >
        <Input disabled />
      </Form.Item>
    );
  }

  return (
    <>
      <Form.Item {...formItemLayout} style={formItemStyle} label="Organization" name="githubOwner">
        <Select
          allowClear
          showSearch
          placeholder="Select owner or organization"
          disabled={pending || readOnly}
          optionFilterProp="children"
          onChange={onNamespaceChange}
          notFoundContent={namespacePending ? <Spin size="small" /> : 'No organizations'}
        >
          {namespaces.map((ns) => (
            <Select.Option key={String(ns.id)} value={ns.id}>
              {ns.name ?? ns.id}
            </Select.Option>
          ))}
        </Select>
      </Form.Item>
      <Form.Item noStyle dependencies={['githubOwner']}>
        {({getFieldValue}) => {
          const owner = getFieldValue('githubOwner');
          return (
            <Form.Item
              {...formItemLayout}
              style={formItemStyle}
              label="GitHub repository"
              name="githubRepository"
            >
              <Select
                allowClear
                showSearch
                placeholder="Select GitHub repository"
                disabled={!owner || pending || readOnly}
                optionFilterProp="children"
                notFoundContent={repoPending ? <Spin size="small" /> : 'No GitHub repositories'}
              >
                {repositories.map((repo) => {
                  const value = repo.httpUrl;
                  if (!value) return null;
                  return (
                    <Select.Option key={value} value={value}>
                      {repo.name ?? value}
                    </Select.Option>
                  );
                })}
              </Select>
            </Form.Item>
          );
        }}
      </Form.Item>
      <Form.Item {...formItemLayout} style={formItemStyle} label="Branch" name="githubBranch">
        <Input disabled={pending || readOnly} />
      </Form.Item>
    </>
  );
}

function ManualRepoFields({
  formItemLayout,
  formItemStyle,
  pending,
  pipeline,
  readOnly,
  showToken,
}: Pick<
  RepositorySectionProps,
  'formItemLayout' | 'formItemStyle' | 'pending' | 'pipeline' | 'readOnly'
> & {showToken?: boolean}) {
  const isExisting = !!pipeline;
  return (
    <>
      <Form.Item
        {...formItemLayout}
        style={formItemStyle}
        label="Repository"
        name="repository"
        dependencies={['repositoryType']}
        rules={[
          ({getFieldValue}) => ({
            validator: async (_, value) => {
              const repoType = getFieldValue('repositoryType') ?? pipeline?.repositoryType;
              if (repoType === 'AZURE_DEVOPS' && !value) {
                throw new Error('Repository is required');
              }
            },
          }),
        ]}
      >
        <Input disabled={isExisting || pending} />
      </Form.Item>
      <Form.Item {...formItemLayout} style={formItemStyle} label="Branch" name="branch">
        <Input disabled={pending || readOnly} />
      </Form.Item>
      {(showToken ?? true) && (
        <Form.Item
          {...formItemLayout}
          style={formItemStyle}
          label="Token"
          name="token"
          dependencies={['repositoryType']}
          rules={[
            ({getFieldValue}) => ({
              validator: async (_, value) => {
                const repoType = getFieldValue('repositoryType') ?? pipeline?.repositoryType;
                if (repoType === 'AZURE_DEVOPS' && !value) {
                  throw new Error('Token is required');
                }
              },
            }),
          ]}
        >
          <Input type="password" autoComplete="off" disabled={pending || readOnly} />
        </Form.Item>
      )}
    </>
  );
}

function RepositorySection(props: RepositorySectionProps) {
  const {
    form,
    formItemLayout,
    formItemStyle,
    githubType,
    onGithubTypeChange,
    pipeline,
    pending,
    readOnly,
    showRepoSettings,
    onShowRepoSettings,
  } = props;

  const hideStyle = {...formItemStyle, display: showRepoSettings ? 'inherit' : 'none'};

  const onRepoTypeChanged = useCallback(
    (type: string) => {
      if (!pipeline) {
        form.setFieldsValue({
          githubOwner: undefined,
          githubRepository: undefined,
          githubBranch: undefined,
          repository: undefined,
          branch: undefined,
          token: undefined,
        });
        onGithubTypeChange(RepositoryTypes.GitHubApp);
      }
    },
    [pipeline, form, onGithubTypeChange],
  );

  const onManualSettingsChange = useCallback(
    (e: {target: {checked: boolean}}) => {
      onGithubTypeChange(e.target.checked ? RepositoryTypes.GitHub : RepositoryTypes.GitHubApp);
      if (!pipeline) {
        if (e.target.checked) {
          form.setFieldsValue({
            githubOwner: undefined,
            githubRepository: undefined,
            githubBranch: undefined,
          });
        } else {
          form.setFieldsValue({repository: undefined, branch: undefined, token: undefined});
        }
      }
    },
    [pipeline, form, onGithubTypeChange],
  );

  return (
    <>
      {!showRepoSettings && (
        <div style={{textAlign: 'right', marginBottom: 5}}>
          <a onClick={onShowRepoSettings}>Edit repository settings</a>
        </div>
      )}
      <Form.Item
        {...formItemLayout}
        style={hideStyle}
        label="Repository Type"
        name="repositoryType"
      >
        <Select disabled={!!pipeline || pending} onChange={onRepoTypeChanged}>
          {availableRepositoryTypes.map((type) => (
            <Select.Option key={type} value={type}>
              {RepositoryTypeNames[type] ?? type}
            </Select.Option>
          ))}
        </Select>
      </Form.Item>
      <Form.Item noStyle dependencies={['repositoryType']}>
        {({getFieldValue}) => {
          const repoType = normalizeRepositoryType(
            getFieldValue('repositoryType') ?? pipeline?.repositoryType ?? RepositoryTypes.GitLab,
          );
          const isGitHub = repoType === RepositoryTypes.GitHub;
          const isExistingPipeline = !!pipeline?.repository;
          const resolvedGithubType = isExistingPipeline
            ? pipeline?.repositoryType === RepositoryTypes.GitHub
              ? RepositoryTypes.GitHub
              : RepositoryTypes.GitHubApp
            : githubType;
          const showManual = isExistingPipeline || resolvedGithubType === RepositoryTypes.GitHub;

          if (!isGitHub) {
            return (
              <ManualRepoFields
                formItemLayout={formItemLayout}
                formItemStyle={hideStyle}
                pending={pending}
                pipeline={pipeline}
                readOnly={readOnly}
              />
            );
          }
          return (
            <>
              <Form.Item {...formItemLayout} style={hideStyle} label=" " colon={false}>
                <Checkbox
                  checked={resolvedGithubType === RepositoryTypes.GitHub}
                  onChange={onManualSettingsChange}
                  disabled={isExistingPipeline || pending || readOnly}
                >
                  Manual settings
                </Checkbox>
              </Form.Item>
              {showManual ? (
                <ManualRepoFields
                  formItemLayout={formItemLayout}
                  formItemStyle={hideStyle}
                  pending={pending}
                  pipeline={pipeline}
                  readOnly={readOnly}
                  showToken={resolvedGithubType === RepositoryTypes.GitHub}
                />
              ) : (
                <GitHubAppForm
                  form={form}
                  formItemLayout={formItemLayout}
                  formItemStyle={hideStyle}
                  pending={pending}
                  pipeline={pipeline}
                  readOnly={readOnly}
                />
              )}
            </>
          );
        }}
      </Form.Item>
      <Form.Item
        {...formItemLayout}
        style={hideStyle}
        label="Configuration path"
        name="configurationPath"
      >
        <Input disabled={pending || readOnly} />
      </Form.Item>
      <Form.Item {...formItemLayout} style={hideStyle} label="Code path" name="codePath">
        <Input disabled={pending || readOnly} />
      </Form.Item>
      <Form.Item {...formItemLayout} style={hideStyle} label="Docs path" name="docsPath">
        <Input disabled={pending || readOnly} />
      </Form.Item>
    </>
  );
}

export {RepositorySection};
export default RepositorySection;
