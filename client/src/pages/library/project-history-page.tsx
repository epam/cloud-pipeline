import {useParams} from 'react-router-dom';
import RunTable from '../../components/runs/run-table';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';
import {useMemo} from 'react';

function ProjectHistoryPage() {
  const {id} = useParams<{id: string}>();
  const folderId = Number(id);
  const filters = useMemo(
    () => ({
      projectIds: Number.isNaN(folderId) || folderId === 0 ? [] : [folderId],
    }),
    [folderId],
  );

  return (
    <LegacyComponentBridge
      component={RunTable}
      componentProps={{filters, className: 'w-full h-full overflow-auto'}}
    />
  );
}

export {ProjectHistoryPage};
