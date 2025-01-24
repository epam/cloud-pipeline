import { useCallback, useMemo, useState } from 'react';
import { Button, message } from 'antd';
import dayjs from 'dayjs';
import { type CommonProps } from '@cloud-pipeline/components';
import { noop, RunStatuses, type Run } from '@cloud-pipeline/core';
import { stopRun } from '@cloud-pipeline/api';
import { generateLaunchRoutePath } from '../../shared/constants/routes';
import { useNavigate } from 'react-router';

type Props = CommonProps & {
  run?: Run;
  refresh: () => Promise<void>;
};

export default function RunHeaderControls({ run, className, refresh }: Props) {
  const navigate = useNavigate();
  const [pending, setPending] = useState(false);
  const [messageApi, contextHolder] = message.useMessage();
  const onStopClick = useCallback(() => {
    if (!run) {
      return;
    }
    messageApi.open({
      key: 'stop',
      type: 'loading',
      content: 'Stopping run...',
    });
    setPending(true);
    stopRun(run.id, dayjs().format('YYYY-MM-DD HH:mm:ss.SSS'))
      .then(() => {
        void refresh().then(() => {
          setPending(false);
          messageApi.open({
            key: 'stop',
            type: 'success',
            content: `Run ${run.id} successfully stopped.`,
            duration: 2,
          });
        });
      })
      .catch(noop);
  }, [messageApi, refresh, run]);
  const onRerunClick = useCallback(() => {
    if (!run) {
      return;
    }
    const { pipelineId } = run;
    navigate(generateLaunchRoutePath(pipelineId, run.id));
  }, [navigate, run]);
  const controlButton = useMemo(() => {
    if (run?.status === RunStatuses.running) {
      return (
        <Button danger disabled={pending} type="link" onClick={onStopClick}>
          Stop
        </Button>
      );
    }
    if (run?.status === RunStatuses.stopped) {
      return (
        <Button
          disabled={pending || !run.pipelineId}
          type="link"
          onClick={onRerunClick}>
          Rerun
        </Button>
      );
    }
    if (run?.status === RunStatuses.paused) {
      return (
        <Button disabled={pending} type="link">
          Continue
        </Button>
      );
    }
  }, [onRerunClick, onStopClick, pending, run?.status]);
  if (!run) {
    return null;
  }
  return (
    <div className={className}>
      {contextHolder}
      {controlButton}
    </div>
  );
}
