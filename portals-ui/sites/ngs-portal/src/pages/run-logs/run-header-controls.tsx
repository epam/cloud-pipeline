import { useCallback, useMemo, useState } from 'react';
import { Button, message, Modal } from 'antd';
import dayjs from 'dayjs';
import { type CommonProps } from '@cloud-pipeline/components';
import { noop, RunStatuses, type Run } from '@cloud-pipeline/core';
import { stopRun } from '@cloud-pipeline/api';
import { generateLaunchRoutePath } from '../../shared/constants/routes';
import { useNavigate } from 'react-router';

type Props = CommonProps & {
  run?: Run;
  runName?: string | number;
  refresh: () => Promise<void>;
};

export default function RunHeaderControls({
  run,
  runName,
  className,
  refresh,
}: Props) {
  const navigate = useNavigate();
  const [pending, setPending] = useState(false);
  const [modal, modalConfirmContext] = Modal.useModal();
  const [messageApi, messageContext] = message.useMessage();
  const onStopClick = useCallback(async () => {
    if (!run) {
      return;
    }
    await modal.confirm({
      title: (
        <span>
          Stop Run <b>{runName ?? ''}</b> ?
        </span>
      ),
      okText: 'Stop',
      okButtonProps: {
        danger: true,
      },
      async onOk() {
        return await new Promise((resolve) => {
          messageApi.open({
            key: 'stop',
            type: 'loading',
            content: 'Stopping run...',
            duration: 0,
          });
          setPending(true);
          stopRun(run.id, dayjs().format('YYYY-MM-DD HH:mm:ss.SSS'))
            .then(() => {
              void refresh().then(() => {
                setPending(false);
                messageApi.open({
                  key: 'stop',
                  type: 'success',
                  content: `Run ${runName ?? ''} successfully stopped.`,
                  duration: 2,
                });
                resolve(true);
              });
            })
            .catch(noop);
        });
      },
    });
  }, [messageApi, modal, refresh, run, runName]);
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
        <Button
          danger
          disabled={pending}
          type="link"
          onClick={() => void onStopClick()}>
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
  }, [onRerunClick, onStopClick, pending, run]);
  if (!run) {
    return null;
  }
  return (
    <div className={className}>
      {messageContext}
      {modalConfirmContext}
      {controlButton}
    </div>
  );
}
