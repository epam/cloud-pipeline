import type { CommonProps } from '@cloud-pipeline/components';
import type { LaunchInfo } from '../../features/launch-form/type';
import classNames from 'classnames';

type Props = CommonProps &
  Pick<LaunchInfo, 'pipelineInfo'> & {
    version?: string;
  };

export default function LaunchHeader({
  pipelineInfo,
  className,
  version,
}: Props) {
  return (
    <div
      className={classNames(
        'flex items-center justify-between gap-2',
        className,
      )}>
      <span className="text-sm">
        Launch <b>{pipelineInfo?.name ?? 'pipeline'}</b>{' '}
        {version ? `(${version})` : null}
      </span>
    </div>
  );
}
