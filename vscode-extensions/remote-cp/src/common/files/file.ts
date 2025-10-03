import * as vscode from "vscode";

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
