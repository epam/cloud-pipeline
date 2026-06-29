import {useCallback, useState} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {Button} from 'antd';
import {SettingOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';
import {configurationQueryOptions} from '../../../../queries/configuration/configuration.ts';
import {ConfigurationEditModal} from '../../../shared/object-actions/configuration/edit/configuration-edit-modal.tsx';

type SettingsActionProps = CommonProps & {
  configurationId?: number | string;
};

function SettingsAction(props: SettingsActionProps) {
  const {configurationId} = props;
  const [open, setOpen] = useState(false);
  const numericId = configurationId !== undefined ? Number(configurationId) : undefined;

  const {data: configuration} = useQuery(configurationQueryOptions(numericId));
  const navigate = useNavigate();
  const {pathname} = useLocation();

  const handleRemove = useCallback(() => {
    setOpen(false);
    const parentFolderId = configuration?.parent?.id;
    if (pathname.startsWith(`/configuration/${numericId}`)) {
      if (parentFolderId !== undefined) {
        navigate(`/folder/${parentFolderId}`);
      } else {
        navigate('/library');
      }
    }
  }, [configuration?.parent?.id, numericId, pathname, navigate]);

  return (
    <>
      <Button id="edit-configuration-button" size="small" onClick={() => setOpen(true)}>
        <SettingOutlined />
      </Button>
      {numericId !== undefined && (
        <ConfigurationEditModal
          open={open}
          onClose={() => setOpen(false)}
          onRemove={handleRemove}
          configurationId={numericId}
        />
      )}
    </>
  );
}

export {SettingsAction};
