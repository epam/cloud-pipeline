import * as vscode from "vscode";
import * as fs from "fs";
import got from "got";

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
      //TODO: make cancellable
      cancellable: false,
    },
    async (progress) => {
      let currentProgress: number = 0; // 0..100
      return new Promise<void>((resolve, reject) => {
        const downloadStream = got.stream(url, {
          retry: retryLimit,
        });
        const fileWriter = fs.createWriteStream(targetPath);

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
          reject(err);
        });

        fileWriter.on("finish", () => {
          resolve();
        });

        downloadStream.pipe(fileWriter);
      });
    },
  );
}
