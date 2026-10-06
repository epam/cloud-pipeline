import { useEffect, useState } from 'react';
import fetchSettings from '../../models/base/settings';
import { getApplicationTypeSettings } from '../../models/folder-application-types';
import { getRunTaskLogs } from '../../models/cloud-pipeline-api/run-tasks';

const asciiColorCodes = /[\u001b\u009b][[()#;?]*(?:[0-9]{1,4}(?:;[0-9]{0,4})*)?[0-9A-ORZcf-nqry=><]/g;

function removeAsciiColorCodes(string) {
  return (string || '').replace(asciiColorCodes, '');
}

async function getSettings(appType) {
  const rawSettings = await fetchSettings();
  return getApplicationTypeSettings(rawSettings, appType);
}

let progressErrorReported = false;

function correctPercent(value) {
  if (Number.isNaN(Number(value))) {
    return undefined;
  }
  return Math.max(0, Math.min(1, Number(value)));
}

function parseProgress(message, progressConfig = 'Step (\\d+)/(\\d+)') {
  try {
    let regExp, percentIndex, stepsIndex, totalIndex;
    const config = progressConfig || 'Step (\\d+)/(\\d+)';
    if (typeof config === 'string') {
      regExp = new RegExp(config, 'i');
      stepsIndex = 1;
      totalIndex = 2;
    } else if (typeof config === 'object') {
      const {
        mask,
        percent,
        steps,
        total,
      } = config;
      if (!mask || typeof mask !== 'string') {
        throw new Error('mask should be specified');
      }
      regExp = new RegExp(mask, 'i');
      if (percent !== undefined) {
        percentIndex = Number(percent);
        if (Number.isNaN(percentIndex)) {
          throw new Error('`percent` should be a number');
        }
      } else if (steps !== undefined && total !== undefined) {
        stepsIndex = Number(steps);
        totalIndex = Number(total);
        if (Number.isNaN(stepsIndex)) {
          throw new Error('`steps` should be a number');
        }
        if (Number.isNaN(totalIndex)) {
          throw new Error('`total` should be a number');
        }
      } else {
        stepsIndex = 1;
        totalIndex = 2;
      }
    }
    const e = regExp.exec(message);
    if (e) {
      if (percentIndex !== undefined && e[percentIndex] && !Number.isNaN(Number(e[percentIndex]))) {
        return correctPercent(Number(e[percentIndex]) / 100.0);
      }
      if (
        stepsIndex !== undefined &&
        totalIndex !== undefined &&
        e[stepsIndex] &&
        e[totalIndex] &&
        !Number.isNaN(Number(e[stepsIndex])) &&
        !Number.isNaN(Number(e[totalIndex]))
      ) {
        return correctPercent(Number(e[stepsIndex]) / Number(e[totalIndex]));
      }
    }
    return undefined;
  } catch (error) {
    if (!progressErrorReported) {
      progressErrorReported = true;
      console.warn(`Error parsing check progress config: ${error.message}`);
    }
  }
  return undefined;
}

function parseLog(message, config) {
  const {
    progress,
  } = config || {};
  const progressValue = parseProgress(message.message, progress);
  if (progressValue !== undefined) {
    return {
      ...message,
      type: 'progress',
      value: progressValue,
    };
  }
  return {
    ...message,
    type: 'message',
    value: removeAsciiColorCodes(message.message),
  };
}

async function check(runId, appType, callback) {
  if (runId) {
    const settings = await getSettings(appType);
    const {
      runProgress = false,
    } = settings || {};
    if (runProgress && typeof runProgress !== 'object') {
      console.warn(`"runProgress" value should be either "false" or "Object" (progress check configuration), but "${runProgress}" was provided`);
      return true;
    }
    const {
      task
    } = runProgress;
    if (!task) {
      console.warn(`"runProgress.task" should be specified`);
      return true;
    }
    if (typeof task !== 'string') {
      console.warn(`"runProgress.task" value is unsupported ("${task}" provided, "string" is expected)`);
      return true;
    }
    const logs = await getRunTaskLogs(runId, task);
    const messages = (logs || []).map((log) => ({
      status: log.status,
      message: log.logText,
    }));
    const parsed = messages.map((message) => parseLog(message, runProgress));
    const last = parsed.pop();
    if (last) {
      const {
        type,
        value,
      } = last;
      callback({
        progress: /^progress$/i.test(type) && !Number.isNaN(Number(value))
          ? Number(value)
          : 0,
        showProgress: /^progress$/i.test(type) && !Number.isNaN(Number(value)),
        message: /^message$/i.test(type) ? value : undefined,
      });
    } else {
      callback(undefined);
    }
    return last && /^success$/i.test(last.status);
  }
  return false;
}

function checkProgress(runId, appType, callback) {
  let token;
  let stopped = false;
  const poll = () => {
    const continuePolling = () => {
      if (!stopped) {
        getSettings(appType)
          .then((settings) => {
            const interval = settings?.runProgress?.pollingInterval;
            return interval && !Number.isNaN(Number(interval))
              ? Number(interval)
              : undefined;
          })
          .catch((error) => console.warn(`Error fetching settings: ${error.message}`))
          .then((interval = 5000) => {
            console.log(`Scheduling run progress check: ${interval} ms`);
            token = setTimeout(poll, interval);
          });
      }
    };
    if (runId) {
      check(runId, appType, callback)
        .catch((error) => console.warn(`Error checking run progress: ${error.message}`))
        .then((done = false) => {
          if (!done) {
            continuePolling();
          }
        });
    }
  };
  poll();
  return () => {
    stopped = true;
    clearTimeout(token);
  }
}

export default function useRunProgress(runId, appType = undefined) {
  const [progress, setProgress] = useState(undefined);
  useEffect(
    () => checkProgress(runId, appType, setProgress),
    [runId, appType, setProgress],
  );
  return progress;
}
