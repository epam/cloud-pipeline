import PreRunCheckError from './pre-run-check-error';

const MESSAGE = `You will not be able to install packages.`

const DISCLAIMER = `### You are going to start an application which restricts installation of the new packages.

Existing package set can be used without limitations, but new packages / extensions won't be installed.`

class DisablePackagesError extends PreRunCheckError {
  constructor(disclaimer) {
    super(
      MESSAGE,
      disclaimer || DISCLAIMER
    );
  }
}

export default function checkDisablePackages (settings, application) {
  if (application && application.disablePackages) {
    console.log('Application has `disablePackages` flag');
    return Promise.reject(new DisablePackagesError(settings?.disablePackages?.warning));
  }
  return Promise.resolve();
}
