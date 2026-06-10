import {Pipeline, RunVisibilityPolicy} from '../../../../../@types/library.ts';
import {RepositoryTypes} from '../../../../special/git-repository-control';

export type PipelineFormValues = {
  name: string;
  description?: string;
  visibility?: RunVisibilityPolicy;
  repositoryType?: string;
  repository?: string;
  branch?: string;
  token?: string;
  githubOwner?: string;
  githubRepository?: string;
  githubBranch?: string;
  configurationPath?: string;
  codePath?: string;
  docsPath?: string;
};

export const formItemLayout = {
  labelCol: {xs: {span: 24}, sm: {span: 6}},
  wrapperCol: {xs: {span: 24}, sm: {span: 18}},
};

export const formItemStyle = {marginBottom: 5};

export function getInitialValues(pipeline: Pipeline | undefined): PipelineFormValues {
  return {
    name: pipeline?.name ?? '',
    description: pipeline?.description ?? '',
    visibility: pipeline?.visibility ?? 'INHERIT',
    repositoryType: pipeline?.repositoryType ?? RepositoryTypes.GitLab,
    repository: pipeline?.repository ?? '',
    branch: pipeline?.branch ?? undefined,
    token: pipeline?.repositoryToken ?? '',
    githubOwner: undefined,
    githubRepository: pipeline?.repository ?? undefined,
    githubBranch: pipeline?.branch ?? undefined,
    configurationPath: pipeline?.configurationPath ?? undefined,
    codePath: pipeline?.codePath ?? undefined,
    docsPath: pipeline?.docsPath ?? undefined,
  };
}
