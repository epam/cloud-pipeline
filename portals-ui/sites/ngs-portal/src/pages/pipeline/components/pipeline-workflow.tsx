import { useMemo } from 'react';
import type { Pipeline } from '@cloud-pipeline/core';
import { usePipelineVersionParameters } from '../hooks';
import { usePipelineMainFile } from '../hooks/use-pipeline-main-file';
import { PipelineWorkflowViewer } from '../../../widgets/pipeline-workflow-viewer';
import { Alert } from 'antd';

type Props = {
  pipeline?: Pipeline;
  version?: string;
};

export default function PipelineWorkflow({ pipeline, version }: Props) {
  const {
    versionParameters,
    pending: parametersPending,
    error: parametersError,
  } = usePipelineVersionParameters(pipeline?.id, version);
  const {
    mainFile,
    pending: mainFilePending,
    error: mainFileError,
  } = usePipelineMainFile(pipeline, version, versionParameters);
  const error = useMemo(() => {
    return parametersError ?? mainFileError;
  }, [parametersError, mainFileError]);
  if (error) {
    return <Alert type="error" message={error} showIcon />;
  }
  return (
    <PipelineWorkflowViewer
      pending={parametersPending || mainFilePending}
      className="h-full flex flex-col"
      mainFile={mainFile}
    />
  );
}
