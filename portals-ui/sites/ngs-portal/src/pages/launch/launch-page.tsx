import { Alert, Spin } from 'antd';
import { LaunchForm } from '../../features/launch-form';
import LaunchHeader from './launch-header';
import './style.css';
import { useSearchParams } from 'react-router-dom';
import { useRunInfo } from '../../shared/hooks/use-run-info';
import useLaunchInfo from '../../features/launch-form/hooks/use-launch-info';
import useLaunchConfiguration from '../../features/launch-form/hooks/use-launch-configuration';
import { PageSpinner } from '../../shared/ui';
import { LaunchFormSearchParams } from '../../shared/constants/search-params';

export function LaunchPage() {
  const [searchParams] = useSearchParams();
  const pipelineId =
    Number(searchParams.get(LaunchFormSearchParams.PipelineId)) || undefined;
  const runId =
    Number(searchParams.get(LaunchFormSearchParams.RunId)) || undefined;
  const version = searchParams.get(LaunchFormSearchParams.Version);

  const { run } = useRunInfo(runId);
  const {
    pipelineInfo,
    version: currentVersion,
    runDefaultParameters,
    configuration,
    errors,
    pending,
  } = useLaunchInfo(
    pipelineId ?? run?.pipelineId,
    run?.configName,
    version ?? run?.version,
  );
  const launchConfiguration = useLaunchConfiguration(configuration, run);

  if (errors.length) {
    return errors.map((error) => <Alert type="error" message={error} />);
  }

  if (!launchConfiguration) {
    return <PageSpinner />;
  }

  return (
    <div className="overflow-auto gap-4 h-full w-full flex flex-col">
      <Spin wrapperClassName="spin-container" spinning={pending}>
        <LaunchHeader
          className="list-container p-4 mb-2 shrink-0"
          pipelineInfo={pipelineInfo}
          version={currentVersion}
        />
        <LaunchForm
          version={currentVersion}
          configuration={launchConfiguration}
          pipelineInfo={pipelineInfo}
          className="list-container p-4 grow"
          runDefaultParameters={runDefaultParameters}
        />
      </Spin>
    </div>
  );
}
