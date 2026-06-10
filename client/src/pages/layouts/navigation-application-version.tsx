import {useMemo} from 'react';
import {useQuery} from '@tanstack/react-query';
import {applicationInfoQueryOptions} from '../../queries';
import {preferenceNames} from '../../stores/preferences/names.ts';
import {useStringPreferenceValue} from '../../queries/preferences/hooks.ts';

type NavigationApplicationVersionProps = {
  className?: string;
  style?: React.CSSProperties;
};

function NavigationApplicationVersion({className, style}: NavigationApplicationVersionProps) {
  const deploymentName =
    useStringPreferenceValue(preferenceNames.uiPipelineDeploymentName) || 'EPAM Cloud Pipeline';
  const {data: applicationInfo, isSuccess: applicationInfoLoaded} = useQuery(
    applicationInfoQueryOptions(),
  );

  const versionDescription = useMemo(() => {
    if (!applicationInfoLoaded) {
      return VERSION;
    }
    const {version = VERSION, prettyName} = applicationInfo;
    if (version && prettyName) {
      return `${prettyName} (${version})`;
    }
    if (prettyName) {
      return prettyName;
    }
    if (version) {
      return version;
    }
    return VERSION;
  }, [applicationInfo, applicationInfoLoaded]);

  return (
    <div className={className} style={style}>
      <div>
        <b>{deploymentName}</b>
      </div>
      {versionDescription ? (
        <div>
          <b style={{marginRight: 5}}>Version:</b>
          {versionDescription}
        </div>
      ) : null}
    </div>
  );
}

export {NavigationApplicationVersion};
