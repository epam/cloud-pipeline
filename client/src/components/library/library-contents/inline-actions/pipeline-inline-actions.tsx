import {Pipeline} from '../../../../@types/library.ts';
import {LibraryInlineActionsProps} from './types.ts';
import {IssuesButton} from './shared.tsx';
import {Button, message} from 'antd';
import {EditOutlined} from '@ant-design/icons';
import {useCallback} from 'react';
import {useNavigate} from 'react-router-dom';
import {routeingPaths} from '../../../../routing/paths.ts';
import {getErrorDescription} from '../../../../utilities/errors.ts';
import {fetchPipeline} from '../../../../queries';
import {PipelineEditButton} from '../../../shared/object-actions/pipeline/edit/pipeline-edit-button.tsx';

function PipelineInlineActions(props: LibraryInlineActionsProps & {pipeline: Pipeline}) {
  const {item, onIssuesClick, pipeline} = props;
  const navigate = useNavigate();
  const canLaunchPipeline = pipeline.pipelineType !== 'VERSIONED_STORAGE';
  const canEditPipeline = pipeline.pipelineType !== 'VERSIONED_STORAGE';
  const canEditVersionedStorage = pipeline.pipelineType === 'VERSIONED_STORAGE';
  const onLaunchPipeline = useCallback(async () => {
    if (pipeline && navigate) {
      const hide = message.loading(
        <span>
          Loading <b>{pipeline.name}</b> versions...
        </span>,
      );
      try {
        const pipelineObj = await fetchPipeline(pipeline.id);
        const {currentVersion} = pipelineObj ?? {};
        if (!currentVersion) {
          throw new Error('latest version not found');
        }
        navigate(routeingPaths.launchPipeline(pipeline.id, currentVersion.name));
      } catch (error) {
        message.error(
          <span>
            Error loading <b>{pipeline.name}</b> versions: {getErrorDescription(error)}
          </span>,
          5,
        );
      } finally {
        hide();
      }
    }
  }, [pipeline, navigate]);
  return (
    <>
      {onIssuesClick && <IssuesButton item={item} onClick={onIssuesClick} />}
      {canLaunchPipeline && (
        <Button size="small" type="primary" onClick={onLaunchPipeline}>
          Run
        </Button>
      )}
      {canEditPipeline && <PipelineEditButton pipelineId={pipeline.id} size="small" />}
      {canEditVersionedStorage && (
        <Button size="small">
          <EditOutlined />
        </Button>
      )}
    </>
  );
}

export {PipelineInlineActions};
