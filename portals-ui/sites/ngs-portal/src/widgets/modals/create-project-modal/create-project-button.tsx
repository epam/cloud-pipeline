import { useCallback, useState } from 'react';
import classNames from 'classnames';
import { Button } from 'antd';
import { PlusIcon } from '@heroicons/react/24/outline';
import { CreateProjectModal } from './create-project-modal';
import type { CommonProps } from '@cloud-pipeline/components';

type Props = CommonProps & {
  showIcon?: boolean;
  text?: string;
};

export const CreateProjectButton = (props: Props) => {
  const { showIcon = true, text, className, style } = props;
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
        type="primary"
        onClick={showModal}
        className={classNames('flex', className)}
        size="small"
        style={style}>
        {showIcon ? <PlusIcon className="size-3 stroke-2" /> : null}{' '}
        {text ?? 'Create project'}
      </Button>
      <CreateProjectModal onCancel={hideModal} visible={visible} />
    </>
  );
};
