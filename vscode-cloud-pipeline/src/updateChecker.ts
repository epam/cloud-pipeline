import * as fs from 'fs';
import * as http from 'http';
import * as https from 'https';
import * as os from 'os';
import * as path from 'path';
import * as vscode from 'vscode';
import { CloudPipelineApi } from './api';
import { resolveCredentials } from './config';
import { getVsixUpdateUrl } from './extensionEnv';
import { COMPONENT_VERSION } from './version';

const CHECK_INTERVAL_MS = 10 * 60 * 1000;
const INITIAL_CHECK_DELAY_MS = 30_000;

let notified = false;

export function startUpdateChecker(context: vscode.ExtensionContext): void {
  const vsixUrl = getVsixUpdateUrl();
  if (!vsixUrl) {
    return;
  }

  const pkgVersion = context.extension.packageJSON.version as string;

  const check = (): void => { void checkForUpdate(vsixUrl, pkgVersion); };

  setTimeout(check, INITIAL_CHECK_DELAY_MS);
  const timer = setInterval(check, CHECK_INTERVAL_MS);
  context.subscriptions.push({ dispose: () => clearInterval(timer) });
}

async function checkForUpdate(vsixUrl: string, pkgVersion: string): Promise<void> {
  if (notified) {
    return;
  }
  const auth = resolveCredentials();
  if (!auth) {
    return;
  }
  try {
    const api = new CloudPipelineApi(auth.apiUrl, auth.accessKey);
    const info = await api.getAppInfo();
    const serverHash = info.components?.['vscode-cloud-pipeline'];
    if (!serverHash || serverHash === COMPONENT_VERSION) {
      return;
    }
    notified = true;
    const answer = await vscode.window.showInformationMessage(
      `A new version of the Cloud Pipeline extension is available (installed: ${pkgVersion}.${COMPONENT_VERSION.slice(0, 8)}).`,
      'Update',
      'Later'
    );
    if (answer !== 'Update') {
      return;
    }
    await downloadAndInstall(vsixUrl, pkgVersion);
  } catch {
    // best-effort — silent on network errors
  }
}

async function downloadAndInstall(vsixUrl: string, pkgVersion: string): Promise<void> {
  const tmpPath = path.join(os.tmpdir(), 'cloud-pipeline-remote-update.vsix');
  try {
    await vscode.window.withProgress(
      { location: vscode.ProgressLocation.Notification, title: 'Cloud Pipeline: Downloading update…', cancellable: false },
      () => downloadFile(vsixUrl, tmpPath)
    );
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    vscode.window.showErrorMessage(`Cloud Pipeline: Failed to download update: ${msg}`);
    return;
  }

  try {
    await vscode.commands.executeCommand('workbench.extensions.installExtension', vscode.Uri.file(tmpPath));
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    vscode.window.showErrorMessage(`Cloud Pipeline: Failed to install update: ${msg}`);
    return;
  } finally {
    fs.unlink(tmpPath, () => {});
  }

  const answer = await vscode.window.showInformationMessage(
    'Cloud Pipeline extension updated. Reload window to apply.',
    'Reload'
  );
  if (answer === 'Reload') {
    await vscode.commands.executeCommand('workbench.action.reloadWindow');
  }
}

function downloadFile(url: string, dest: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const isHttps = u.protocol === 'https:';
    const lib = isHttps ? https : http;
    const opts: https.RequestOptions = {
      hostname: u.hostname,
      port: u.port || (isHttps ? 443 : 80),
      path: u.pathname + u.search,
      rejectUnauthorized: false,
    };
    const file = fs.createWriteStream(dest);
    lib.get(opts, (res) => {
      if (res.statusCode !== 200) {
        file.close(() => fs.unlink(dest, () => {}));
        reject(new Error(`HTTP ${res.statusCode}`));
        return;
      }
      res.pipe(file);
      file.on('finish', () => file.close(() => resolve()));
      file.on('error', (e) => { fs.unlink(dest, () => {}); reject(e); });
    }).on('error', (e) => { fs.unlink(dest, () => {}); reject(e); });
  });
}
