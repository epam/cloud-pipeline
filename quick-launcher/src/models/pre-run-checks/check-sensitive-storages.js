import PreRunCheckError from './pre-run-check-error';

const MESSAGE = `Your job contains sensitive storages.`

const DISCLAIMER = `### You are going to launch a job with **sensitive storages**.

This will apply a number of restrictions for the job: no Internet access, all the storages will be available in a read-only mode, you won't be able to extract the data from the running job and other.`

class CheckSensitiveStorages extends PreRunCheckError {
  constructor(disclaimer) {
    super(
      MESSAGE,
      disclaimer || DISCLAIMER
    );
  }
}

export function launchOptionsContainSensitiveMounts (launchOptions) {
  const {
    sensitiveMounts = []
  } = launchOptions || {};
  return sensitiveMounts.length > 0;
}

export default function checkSensitiveStorages(settings, launchOptions) {
  if (launchOptionsContainSensitiveMounts(launchOptions)) {
    console.log('Job contains sensitive mounts', launchOptions);
    return Promise.reject(new CheckSensitiveStorages(settings?.jobContainsSensitiveStoragesWarning));
  }
  return Promise.resolve();
}
