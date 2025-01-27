import { useMemo } from 'react';
import { Alert, Spin } from 'antd';
import { LaunchForm } from '../../features/launch-form';
import LaunchHeader from './launch-header';
import './style.css';
import { useSearchParams } from 'react-router-dom';
import { useRunInfo } from '../../shared/hooks/use-run-info';
import useLaunchInfo from '../../features/launch-form/hooks/use-launch-info';
import useLaunchConfiguration from '../../features/launch-form/hooks/use-launch-configuration';

export function LaunchPage() {
  const [searchParams] = useSearchParams();
  const pipelineId = Number(searchParams.get('pipelineId')) || undefined;
  const runId = Number(searchParams.get('runId')) || undefined;
  const { run } = useRunInfo(runId);
  const { pipelineInfo, version, versions, configuration, errors, pending } =
    useLaunchInfo(pipelineId ?? run?.pipelineId, run?.configName, run?.version);
  const launchConfiguration = useLaunchConfiguration(configuration, run);
  const launchInfoLoaded = useMemo(
    () => launchConfiguration,
    [launchConfiguration],
  );
  if (errors.length) {
    return errors.map((error) => <Alert type="error" message={error} />);
  }
  if (!launchInfoLoaded) {
    return <Spin wrapperClassName="flex h-full" spinning />;
  }
  return (
    <div className="overflow-auto gap-4 h-full w-full flex flex-col">
      <Spin wrapperClassName="spin-container" spinning={pending}>
        <LaunchHeader
          className="list-container p-4 mb-2 shrink-0"
          pipelineInfo={pipelineInfo}
          version={version}
        />
        <LaunchForm
          version={version}
          configuration={launchConfiguration}
          pipelineInfo={pipelineInfo}
          className="list-container p-4 grow"
        />
      </Spin>
    </div>
  );
}
