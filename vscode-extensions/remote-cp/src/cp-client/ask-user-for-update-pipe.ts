import * as vscode from "vscode";

import { ICpExtConfig } from "../config";
import {
  ActionQuickPickItem,
  quickPickWithCountdown,
} from "../common/quick-pick-with-countdown";
import { DateTime, DurationObjectUnits } from "luxon";
import { ILogger } from "../common/logger";

type ActionItem = ActionQuickPickItem<Promise<boolean>>;

export async function askUserForUpdatePipe(
  cpExtConfig: ICpExtConfig,
  v: { apiVersion: string; cliVersion: string },
  logger: ILogger,
): Promise<boolean> {
  let resNeedsDownload = false;
  if (
    cpExtConfig.pipeSnoozeUpdate &&
    cpExtConfig.pipeSnoozeUpdate > DateTime.now()
  )
    return false;

  const updateSnooze = async (snoozeDuration: DurationObjectUnits) => {
    const snoozedDt = DateTime.now().plus(snoozeDuration);
    logger.info("Pipe client update snoozed till `${snoozedDt}`.");
    cpExtConfig.pipeSnoozeUpdate = snoozedDt;
    // await cpExtConfig.save("askUserForUpdatePipe.updateSnooze()");
  };
  const choices: ActionItem[] = [
    {
      label: "Snooze (for 4 hours)",
      action: async () => {
        await updateSnooze({ hours: 4 });
        return false;
      },
    },
    {
      label: "Snooze (for 10 minutes)",
      action: async () => {
        await updateSnooze({ minutes: 10 });
        return false;
      },
    },
    {
      label: "Update",
      action: async () => {
        return true;
      },
    },
    {
      label: "-",
      kind: vscode.QuickPickItemKind.Separator,
      action: async () => {
        throw new Error("Unexpected");
      },
    },
    {
      label: "Keep",
      action: async () => {
        return false;
      },
    },
  ];

  const userResp = await quickPickWithCountdown<ActionItem>(
    `${cpExtConfig.prefix}: client API ${v.apiVersion} vs CLI ${v.cliVersion} version mismatch.`,
    choices,
    15000,
  );
  resNeedsDownload = await (await userResp.result).action();

  return resNeedsDownload;
}
