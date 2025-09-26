import * as fs from "fs";
import * as unzipper from "unzipper";
import * as vscode from "vscode";
import * as path from "path";

export async function unzipperFile(
  zipFile: string,
  targetDir: string,
  title?: string,
): Promise<void> {
  const zipStats = fs.statSync(zipFile);
  const zipTotalSize = zipStats.size;
  let zipReadBytes = 0;

  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      cancellable: false,
      title: title || "Unzipping file...",
    },
    (progress) => {
      let currentProgress: number = 0; // 0..100
      return new Promise<void>((resolve, reject) => {
        const readStream = fs.createReadStream(zipFile);
        const extractStream = unzipper.Extract({ path: targetDir });

        readStream.on("data", (chunk) => {
          zipReadBytes += chunk.length;
          const diffProgress =
            100 * (zipReadBytes / zipTotalSize) - currentProgress;

          if (diffProgress >= 1) {
            currentProgress += diffProgress;
            progress.report({
              message: `${Math.round(currentProgress)}%`,
              increment: diffProgress,
            });
          }
        });

        readStream.on("error", (err) => reject(err));
        extractStream.on("error", (err) => reject(err));
        extractStream.on("close", () => resolve());

        readStream.pipe(extractStream);
      });
    },
  );
}
