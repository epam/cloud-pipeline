import type { CommonProps } from '@cloud-pipeline/components';
import { Button } from 'antd';
import classNames from 'classnames';
import { useState, useCallback } from 'react';
import { PipelineToProjectModal } from './pipeline-to-project-modal';
import type { Pipeline } from '@cloud-pipeline/core';

type Props = CommonProps & {
  pipeline: Pipeline;
  showIcon?: boolean;
  text?: string;
};

export const PipelineToProjectButton = (props: Props) => {
  const { text, className, style, pipeline } = props;
  const [visible, setVisible] = useState(false);
  const showModal = useCallback(() => {
    setVisible(true);
  }, []);
  const hideModal = useCallback(() => {
    setVisible(false);
  }, []);
  return (
    <>
      <Button
        type="default"
        onClick={showModal}
        className={classNames('flex', className)}
        size="small"
        style={style}>
        {text ?? 'Add to project'}
      </Button>
      <PipelineToProjectModal
        onCancel={hideModal}
        visible={visible}
        pipeline={pipeline}
      />
    </>
  );
};
