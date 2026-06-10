import {Button, message} from 'antd';

import type {CommonProps} from '../../../../@types/common.ts';

type GenerateUrlActionProps = CommonProps & {
  storageId?: number | string;
};

function GenerateUrlAction(props: GenerateUrlActionProps) {
  const {storageId} = props;

  return (
    <Button
      id="generate-folder-url"
      size="small"
      onClick={() => message.info(`[mock] Generate URL for storage ${storageId}`)}
    >
      Generate URL
    </Button>
  );
}

export {GenerateUrlAction};
