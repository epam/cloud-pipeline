import { useMemo } from 'react';
import { useParams } from 'react-router';
import { Alert, Spin } from 'antd';
import useLaunchInfo from '../../features/launch-form/hooks/use-launch-info';
import { LaunchForm } from '../../features/launch-form';
import LaunchHeader from './launch-header';
import './style.css';

export function LaunchPage() {
  const { pipelineId } = useParams();
  const {
    pipelineInfo,
    version,
    versions,
    configuration,
    configurations,
    errors,
    pending,
    setVersion,
    setConfiguration,
  } = useLaunchInfo(Number(pipelineId));
  const launchInfoLoaded = useMemo(
    () => version && versions?.length && configuration && pipelineInfo,
    [configuration, pipelineInfo, version, versions?.length],
  );
  if (errors.length) {
    return errors.map((error) => <Alert type="error" message={error} />);
  }
  if (!launchInfoLoaded) {
    return <Spin wrapperClassName="flex h-full" spinning />;
  }
  return (
    <div className="overflow-auto list-container p-4 h-full w-full flex flex-col">
      <Spin wrapperClassName="spin-container" spinning={pending}>
        <LaunchHeader
          pipelineInfo={pipelineInfo}
          version={version}
          versions={versions}
          configuration={configuration}
          configurations={configurations}
          setVersion={setVersion}
          setConfiguration={setConfiguration}
          disabled={pending || errors.length > 0}
        />
        <LaunchForm
          version={version}
          configuration={configuration}
          pipelineInfo={pipelineInfo}
        />
      </Spin>
    </div>
  );
}
