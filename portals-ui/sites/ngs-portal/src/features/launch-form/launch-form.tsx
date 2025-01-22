import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { Button, message, Modal } from 'antd';
import {
  type PipelineConfiguration,
  type MappedPipelineParameter,
  type Pipeline,
} from '@cloud-pipeline/core';
import { launchPipeline } from '@cloud-pipeline/api';
import { generateLaunchPayload } from './utils/generate-launch-payload';
import { LaunchParametersForm } from './forms/parameters-form';
import { mapParameters, unMapParameters } from './utils/parameters';
import { generateRunLogsRoutePath } from '../../shared/constants/routes';
import type { CommonProps } from '@cloud-pipeline/components';
import classNames from 'classnames';

type LaunchFormProps = CommonProps & {
  configuration?: PipelineConfiguration;
  pipelineInfo?: Pipeline;
  version?: string;
  prettyNameEditable?: boolean;
  readOnly?: boolean;
};

export function LaunchForm({
  configuration,
  pipelineInfo,
  version,
  prettyNameEditable = false,
  className,
  readOnly = false,
}: LaunchFormProps) {
  const [pending, setPending] = useState(false);
  const [modal, launchConfirmContext] = Modal.useModal();
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
  const launch = async (): Promise<void> => {
    if (launchDisabled) {
      return;
    }
    await modal.confirm({
      title: (
        <span>
          Launch <b>{pipelineInfo?.name}</b> ?
        </span>
      ),
      okText: 'Launch',
      async onOk() {
        return await new Promise((resolve) => {
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
            .then((run) => {
              setPending(false);
              resolve(true);
              navigate(generateRunLogsRoutePath(Number(run.id)));
            })
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
            });
        });
      },
    });
  };
  const resetForm = () => {
    setParametersFormData(mapParameters(configuration));
  };
  return (
    <div
      className={classNames('flex flex-col gap-2 overflow-hidden', className)}>
      {contextHolder}
      {launchConfirmContext}
      <LaunchParametersForm
        parameters={parametersFormData}
        onChange={onChangeParameter}
        prettyNameEditable={prettyNameEditable}
        readOnly={readOnly}
      />
      {!readOnly ? (
        <div className="flex items-center gap-1 justify-end">
          <Button
            onClick={resetForm}
            disabled={!formChanged}
            className="ml-auto">
            Reset
          </Button>
          <Button
            onClick={() => {
              void launch();
            }}
            loading={pending}
            disabled={launchDisabled}
            type="primary">
            Launch
          </Button>
        </div>
      ) : null}
    </div>
  );
}
