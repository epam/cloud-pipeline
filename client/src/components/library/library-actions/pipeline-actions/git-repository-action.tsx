import {Button, message} from 'antd';
import {BranchesOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';

type GitRepositoryActionProps = CommonProps & {
  pipelineId?: number | string;
  https?: string;
  ssh?: string;
};

function GitRepositoryAction(props: GitRepositoryActionProps) {
  const {pipelineId, https, ssh} = props;

  const onClick = () => {
    message.info(
      `[mock] Git repository for pipeline ${pipelineId}: ${https ?? ssh ?? 'no repository'}`,
    );
  };

  return (
    <Button size="small" id="git-repository-button" onClick={onClick}>
      <BranchesOutlined />
    </Button>
  );
}

export {GitRepositoryAction};
