import type { LaunchInfo } from '../../features/launch-form/type';

type Props = Pick<LaunchInfo, 'pipelineInfo'>;

export default function LaunchHeader({ pipelineInfo }: Props) {
  return (
    <div className="flex items-center justify-between gap-2 pb-1">
      <span className="text-sm">
        Launch <b>{pipelineInfo?.name ?? 'pipeline'}</b>
      </span>
    </div>
  );
}
