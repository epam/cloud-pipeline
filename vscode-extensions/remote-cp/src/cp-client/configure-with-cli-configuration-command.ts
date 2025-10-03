import * as vscode from "vscode";
import open from "open";

import { ILogger } from "../common/logger";
import { ICpExtConfig } from "../config";
import { ICpClientConfig } from "./cp-client-config";

export async function configureWithCliConfigurationCommand(
  cpExtConfig: ICpExtConfig,
  logger: ILogger,
): Promise<ICpClientConfig | null> {
  logger.info("Configuring with CLI configuration command ...");
  const cliConfigUri = vscode.Uri.parse(cpExtConfig.platformUrl).with({
    path: "/pipeline/",
    fragment: "/settings/cli",
  });

  open(cliConfigUri.toString(true))
    .then((cccBrowserProcess) => {
      logger.info(`  browser opened`);
      cccBrowserProcess.on("close", () => {
        logger.info(`  browser closed`);
      });
    })
    .catch((err) => {
      logger.error(`  browser failed to open: ${err}`);
    });

  const cliConfigCmd = await vscode.window.showInputBox({
    ignoreFocusOut: true,
    placeHolder: "pipe configure ...",
    prompt: "Enter pipe configure command",
    title:
      "In the opened platform web page, click 'Generate access key' " +
      "and copy the whole 'CLI configuration command'.",
  });
  logger.info(`  received command: ${cliConfigCmd}`);

  const tokenM = cliConfigCmd?.match(/--auth-token ([^ ]+)/);
  const apiM = cliConfigCmd?.match(/--api ([^ ]+)/);
  const tzM = cliConfigCmd?.match(/--timezone (\w+)/);
  const proxyM = cliConfigCmd?.match(/--proxy (\w+)/);
  if (!tokenM || !apiM || !tzM || !proxyM) {
    const errMsg = "Invalid CLI configuration command";
    logger.error(errMsg);
    vscode.window.showErrorMessage(errMsg);
    return null;
  }

  // ccc stands for "cli configure command"
  const cccApiToken = tokenM![1];
  const cccApiUrl = apiM![1];
  const cccPlatformUrl = new URL(cccApiUrl).origin;
  const cccTimezone = tzM![1];
  const cccProxy = proxyM![1];

  if (cccPlatformUrl !== cpExtConfig.platformUrl) {
    const newPlatformUrlUserResp = await vscode.window.showWarningMessage(
      `${cpExtConfig.prefix}: The CLI configuration command API Platform URL '${cccPlatformUrl}'` +
        ` does not match the current Platform URL '${cpExtConfig.platformUrl}'.`,
      ...["Apply", "Cancel"],
    );
    if (newPlatformUrlUserResp === "Apply") {
      await cpExtConfig.setPlatformUrl(cccPlatformUrl);
      await vscode.window.showInformationMessage(
        `${cpExtConfig.prefix}: Platform URL updated to '${cccPlatformUrl}'`,
      );
    }

    await cpExtConfig.setPlatformUrl(cccApiUrl);
  }
  const resConfig: ICpClientConfig | null = {
    apiToken: cccApiToken,
    apiUri: cccApiUrl,
    timezone: cccTimezone,
    proxy: cccProxy,
  };
  return resConfig;
}
