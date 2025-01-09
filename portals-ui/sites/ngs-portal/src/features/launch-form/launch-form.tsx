import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { Button, message } from 'antd';
import {
  type PipelineConfiguration,
  type MappedPipelineParameter,
  type Pipeline,
  noop,
} from '@cloud-pipeline/core';
import { launchPipeline } from '@cloud-pipeline/api';
import { generateLaunchPayload } from './utils/generate-launch-payload';
import { LaunchParametersForm } from './forms/parameters-form';
import { mapParameters, unMapParameters } from './utils/parameters';
import { AppRoutes, RoutePath } from '../../shared/constants/routes';

type LaunchFormProps = {
  configuration?: PipelineConfiguration;
  pipelineInfo?: Pipeline;
  version?: string;
  prettyNameEditable?: boolean;
};

export function LaunchForm({
  configuration,
  pipelineInfo,
  version,
  prettyNameEditable = false,
}: LaunchFormProps) {
  const [pending, setPending] = useState(false);
  const navigate = useNavigate();
  const [parametersFormData, setParametersFormData] = useState<
    MappedPipelineParameter[] | undefined
  >();
  const [messageApi, contextHolder] = message.useMessage();
  useEffect(() => {
    if (version && configuration) {
      setParametersFormData(mapParameters(configuration));
    }
  }, [configuration, version]);
  const onChangeParameter = useCallback(
    (key: string, parameter: MappedPipelineParameter) => {
      if (!parametersFormData?.length) {
        return;
      }
      const idx = parametersFormData.findIndex(
        (parameter) => parameter.key === key,
      );
      const update = parametersFormData.slice();
      update.splice(idx, 1, parameter);
      setParametersFormData(update);
    },
    [parametersFormData],
  );
  const launchDisabled = useMemo(() => {
    if (parametersFormData && pipelineInfo && configuration && version) {
      return parametersFormData.some(
        (parameter) => parameter.error ?? parameter.keyError,
      );
    }
    return true;
  }, [configuration, parametersFormData, pipelineInfo, version]);
  const formChanged = useMemo(() => {
    return parametersFormData?.some((parameter) => parameter.touched);
  }, [parametersFormData]);
  const launch = () => {
    if (launchDisabled) {
      return;
    }
    messageApi.open({
      key: 'launch',
      type: 'loading',
      content: 'Launching pipeline...',
    });
    setPending(true);
    const payload = generateLaunchPayload(
      pipelineInfo!,
      configuration!,
      unMapParameters(parametersFormData),
      version!,
    );
    launchPipeline(payload)
      .then(noop)
      .catch((error) => {
        messageApi.open({
          key: 'launch',
          type: 'error',
          content: (
            <div className="flex flex-col items-start">
              <b>Launch failed.</b>
              <span>
                {error instanceof Error ? error.message : String(error)}
              </span>
            </div>
          ),
          duration: 2,
        });
      })
      .finally(() => {
        setPending(false);
        navigate(RoutePath[AppRoutes.HOME]);
      });
  };
  const resetForm = () => {
    setParametersFormData(mapParameters(configuration));
  };
  return (
    <div className="flex flex-col gap-2 overflow-hidden h-full w-full">
      {contextHolder}
      <LaunchParametersForm
        parameters={parametersFormData}
        onChange={onChangeParameter}
        prettyNameEditable={prettyNameEditable}
      />
      <div className="flex items-center gap-1 justify-end">
        <Button onClick={resetForm} disabled={!formChanged} className="ml-auto">
          Reset
        </Button>
        <Button
          onClick={launch}
          loading={pending}
          disabled={launchDisabled}
          type="primary">
          Launch
        </Button>
      </div>
    </div>
  );
}
