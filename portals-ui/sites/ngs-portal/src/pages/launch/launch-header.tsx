import { Select } from 'antd';
import type { LaunchInfo } from '../../features/launch-form/type';

type Props = Omit<LaunchInfo, 'pending' | 'errors'> & {
  disabled: boolean;
};

export default function LaunchHeader({
  pipelineInfo,
  version,
  versions,
  configuration,
  configurations,
  setVersion,
  setConfiguration,
  disabled,
}: Props) {
  return (
    <div className="flex items-center justify-between gap-2 pb-1">
      <span className="text-sm">
        Launch <b>{pipelineInfo?.name ?? 'pipeline'}</b>
      </span>
      <div className="flex items-center gap-1">
        {versions ? (
          <Select
            disabled={disabled || versions.length <= 1}
            prefix={<span className="text-faded">Version:</span>}
            onChange={setVersion}
            style={{ minWidth: '200px' }}
            value={version}
            options={versions?.map((version) => ({
              value: version.name,
              label: version.name,
            }))}
          />
        ) : null}
        {configurations?.length ? (
          <Select
            disabled={disabled || configurations.length <= 1}
            prefix={<span className="text-faded">Configuration:</span>}
            onChange={(value) =>
              setConfiguration(
                configurations.find(
                  (configuration) => configuration.name === value,
                ),
              )
            }
            style={{ minWidth: '200px' }}
            value={configuration?.name}
            options={configurations?.map((configuration) => ({
              value: configuration.name,
              label: configuration.name,
            }))}
          />
        ) : null}
      </div>
    </div>
  );
}
