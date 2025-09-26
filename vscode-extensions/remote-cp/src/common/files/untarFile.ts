import * as fs from "fs";
import * as tar from "tar";
import * as zlib from "zlib";
import * as vscode from "vscode";

export async function untarFile(
  tarFile: string,
  targetDir: string,
  title: string,
): Promise<void> {
  const tarStats = fs.statSync(tarFile);
  const tarTotalSize = tarStats.size;

  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      cancellable: false,
      title: title || "Untar file...",
    },
    (progress) => {
      let currentProgress: number = 0; // 0..100
      let tarReadBytes = 0;

      return new Promise<void>((resolve, reject) => {
        const readStream = fs.createReadStream(tarFile);

        readStream.on("data", (chunk) => {
          tarReadBytes += chunk.length;
          const diffProgress =
            100 * (tarReadBytes / tarTotalSize) - currentProgress;

          if (diffProgress >= 1) {
            currentProgress += diffProgress;
            progress.report({
              message: `${Math.round(currentProgress)}%`,
              increment: diffProgress,
            });
          }
        });

        readStream
          .pipe(zlib.createGunzip())
          .pipe(tar.extract({ cwd: targetDir }))
          .on("finish", resolve)
          .on("error", reject);
      });
    },
  );
}
