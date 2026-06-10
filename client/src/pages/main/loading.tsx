import {CommonProps} from '../../@types/common.ts';
import {useLoadingHook} from '../../hooks/loading/loading-hooks.ts';
import {initializeApp} from '../../workflows/initialization/initialize-app.tsx';
import {ReactNode, useState} from 'react';
import {LoadingMessage} from '../../components/shared/loading-message/loading-message.tsx';
import {Alert} from 'antd';
import {useStringPreferenceValue} from '../../queries/preferences/hooks.ts';
import {preferenceNames} from '../../stores/preferences/names.ts';

function LoadingPage(props: CommonProps & {children?: ReactNode}) {
  const {className, style, children} = props;
  const [currentMessage, setCurrentMessage] = useState<ReactNode | undefined>();
  const {error, loaded} = useLoadingHook(initializeApp, setCurrentMessage);
  const deploymentName = useStringPreferenceValue(preferenceNames.uiPipelineDeploymentName);
  if (error) {
    return (
      <div className={className} style={style}>
        <div className="w-full h-full overflow-auto flex items-center justify-center">
          <Alert
            type="error"
            title={
              <span>
                Error loading <b>{deploymentName ?? 'Cloud Pipeline'}</b>
              </span>
            }
            description={error}
            showIcon
            banner
          />
        </div>
      </div>
    );
  }
  if (loaded) {
    return (
      <div className={props.className} style={props.style}>
        {children}
      </div>
    );
  }
  return (
    <div className={props.className} style={props.style}>
      <div className="w-full h-full overflow-auto flex items-center justify-center">
        <LoadingMessage>{currentMessage}</LoadingMessage>
      </div>
    </div>
  );
}

export {LoadingPage};
