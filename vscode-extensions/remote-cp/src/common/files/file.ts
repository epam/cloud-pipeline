import * as vscode from "vscode";
import * as fs from "fs";

export async function fileExists(filePath: vscode.Uri): Promise<boolean> {
  let resExists: boolean = false;
  try {
    const configStats = await vscode.workspace.fs.stat(filePath);
    resExists = configStats.type === vscode.FileType.File;
  } catch {
    resExists = false;
  }
  return resExists;
}

export function fsFileExistsSync(path: string): boolean {
  try {
    fs.accessSync(path);
    return true;
  } catch {
    return false;
  }
}

export async function dirExists(dirPath: vscode.Uri): Promise<boolean> {
  let resExists: boolean = false;
  try {
    const configStats = await vscode.workspace.fs.stat(dirPath);
    resExists = configStats.type === vscode.FileType.Directory;
  } catch {
    resExists = false;
  }
  return resExists;
}

export async function readJsonFile<T = any>(filePath: vscode.Uri): Promise<T> {
  const fileData = await vscode.workspace.fs.readFile(filePath);
  const fileText = Buffer.from(fileData).toString("utf8");
  const parsed = JSON.parse(fileText) as T;
  return parsed;
}

export function fsReadJsonFileSync<T = any>(file: fs.PathOrFileDescriptor): T {
  const fileText = fs.readFileSync(file, { encoding: "utf8" });
  const parsed = JSON.parse(fileText) as T;
  return parsed;
}

export function fsWriteJsonFileSync(
  file: fs.PathOrFileDescriptor,
  value: any,
): void {
  fs.writeFileSync(file, JSON.stringify(value, undefined, 2));
}
