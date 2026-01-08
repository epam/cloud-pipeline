import * as vscode from "vscode";
import * as fs from "fs";
import got from "got";
import { UserCancelledError } from "../../cp-client/error";

export async function downloadFile(
  url: string,
  targetPath: string,
  title?: string,
  retryLimit: number = 3,
): Promise<void> {
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

        // Handle cancellation
        token.onCancellationRequested(() => {
          downloadStream.destroy();
          fileWriter.close();

          // Delete the partial file
          fs.unlink(targetPath, (err) => {
            if (err && err.code !== "ENOENT") {
              console.error("Failed to delete partial file:", err);
            }
          });

          reject(new UserCancelledError("Download cancelled by user"));
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
      });
    },
  );
}
