import {useParams} from 'react-router-dom';
import PipelineLatestVersion from '../../components/pipelines/browser/redirections/PipelineLatestVersion';
import {LegacyComponentBridge} from '../_shared/legacy-component-bridge';

function PipelineGitRefPage() {
  const {pipeline, section, subSection} = useParams<{
    pipeline: string;
    section?: string;
    subSection?: string;
  }>();
  return (
    <LegacyComponentBridge
      component={PipelineLatestVersion}
      componentProps={{routeParams: {pipeline, section, subSection}}}
    />
  );
}

export {PipelineGitRefPage};
