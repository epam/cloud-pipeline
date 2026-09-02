import * as vscode from "vscode";
import * as fs from "fs";
import got from "got";
import { DependentAbortError, UserCancelledError } from "../../cp-client/error";
import { ILogger } from "../logger";

export async function downloadFile(
  url: string,
  targetPath: string,
  options?: {
    title?: string;
    retryLimit?: number /* 3 */;
    abortSignal?: AbortSignal;
    logger?: ILogger;
  },
): Promise<void> {
  const title = options?.title;
  const retryLimit = options?.retryLimit ?? 3;

  let abortHandler: (err: any) => void;
  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: title ?? "Downloading file",
      cancellable: true,
    },
    async (progress, token) => {
      let currentProgress: number = 0; // 0..100
      return new Promise<void>((resolve, reject) => {
        const downloadStream = got.stream(url, {
          retry: retryLimit,
        });
        const fileWriter = fs.createWriteStream(targetPath);

        const abort = (err: Error) => {
          options?.logger?.debug("Download aborted outside.");
          downloadStream.destroy();
          fileWriter.close();
          reject(err);
        };

        options?.abortSignal?.addEventListener(
          "abort",
          (abortHandler = () => {
            abort(new DependentAbortError("Download aborted dependent"));
          }),
        );

        // Handle cancellation
        token.onCancellationRequested(() => {
          options?.logger?.debug("Download cancelled by user.");
          abort(new UserCancelledError("Download cancelled by user"));
        });

        downloadStream.on("downloadProgress", (p) => {
          if (p.percent !== undefined) {
            const diffProgress = 100 * p.percent - currentProgress;
            if (diffProgress >= 1) {
              currentProgress += diffProgress;
              progress.report({
                message: `${Math.round(currentProgress)}%`,
                increment: diffProgress,
              });
            }
          }
        });

        downloadStream.on("error", (err) => {
          fileWriter.close();
          reject(err);
        });

        fileWriter.on("finish", () => {
          resolve();
        });

        fileWriter.on("error", (err) => {
          downloadStream.destroy();
          reject(err);
        });

        downloadStream.pipe(fileWriter);
      }).finally(() => {
        options?.abortSignal?.removeEventListener("abort", abortHandler);
      });
    },
  );
}
