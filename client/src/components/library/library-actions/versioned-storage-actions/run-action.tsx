import {Button, message} from 'antd';

import type {CommonProps} from '../../../../@types/common.ts';

type RunActionProps = CommonProps & {
  storageId?: number | string;
  readOnly?: boolean;
  executable?: boolean;
};

function RunAction(props: RunActionProps) {
  const {storageId, readOnly = false, executable = true} = props;

  if (!executable) {
    return null;
  }

  return (
    <Button
      size="small"
      type="primary"
      disabled={readOnly}
      onClick={() => message.info(`[mock] Run versioned storage ${storageId}`)}
    >
      RUN
    </Button>
  );
}

export {RunAction};
