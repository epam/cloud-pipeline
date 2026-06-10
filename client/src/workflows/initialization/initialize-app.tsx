import {ReactNode} from 'react';
import cloudPipelineApi from '../../api/cloud-pipeline-api.ts';
import {AuthorizationError} from '../../api/base/errors.ts';
import {whoAmI} from '../../api/users/who-am-i.ts';
import {authenticatedUserStore} from '../../stores/users/current-user.ts';
import {getStringPreferenceValue} from '../../queries/preferences/hooks.ts';
import {preferenceNames} from '../../stores/preferences/names.ts';
import {loadUiNavigation} from '../../stores/ui-navigation';
import {fetchThemesPreference, markAppReadyForThemePreferences} from '../../stores/themes';

export function initCloudPipelineApi() {
  if (!cloudPipelineApi.initialized) {
    const apiHref = SERVER + API_PATH;
    console.log(`Initializing Cloud Pipeline API: ${apiHref}`);
    cloudPipelineApi.initialize({
      base: SERVER + API_PATH,
    });
  }
}

export async function initializeApp(statusCallback?: (message: ReactNode) => void) {
  try {
    document.title = 'Loading...';
    initCloudPipelineApi();
    const report = (status: ReactNode) => {
      if (statusCallback) {
        statusCallback(status);
      }
    };
    report('Authenticating...');
    const authenticated = await cloudPipelineApi.authenticate();
    if (!authenticated) {
      throw new AuthorizationError();
    }
    const [user, deploymentName] = await Promise.all([
      whoAmI(),
      getStringPreferenceValue(preferenceNames.uiPipelineDeploymentName),
    ]);
    console.log('Authenticated user: ', user.userName);
    authenticatedUserStore.setState({user});
    console.log('Deployment Name: ', deploymentName);
    if (deploymentName) {
      document.title = deploymentName;
    } else {
      document.title = 'Cloud Pipeline';
    }
    report(
      deploymentName ? (
        <span>
          Loading <b>{deploymentName}</b> preferences...
        </span>
      ) : (
        <span>Loading preferences...</span>
      ),
    );
    await Promise.all([loadUiNavigation()]);
    report(
      deploymentName ? (
        <span>
          Setting up <b>{deploymentName}</b> themes...
        </span>
      ) : (
        <span>Setting up themes...</span>
      ),
    );
    markAppReadyForThemePreferences();
    fetchThemesPreference();
  } catch (error) {
    console.error('Error initializing Cloud Pipeline', error);
    throw error;
  }
}
