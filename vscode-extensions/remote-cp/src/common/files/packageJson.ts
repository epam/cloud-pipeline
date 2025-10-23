import * as vscode from "vscode";

import { readJsonFile } from "./file";
import { ILogger } from "../logger";

export type IPackageJson = any;

export async function readPackageJson(
  context: vscode.ExtensionContext,
  logger: ILogger,
): Promise<IPackageJson> {
  const packageJsonUri = vscode.Uri.joinPath(
    context.extensionUri,
    "package.json",
  );
  logger.info("Read package.json :\n" + `  file: ${packageJsonUri.fsPath}`);
  const obj = await readJsonFile(packageJsonUri);
  return obj;
}

export interface MenuCommandDescriptor {
  command: string;
  title: string;
  category?: string;
  icon?: unknown;
  group?: string;
}

export class PackageJsonData {
  protected constructor(
    private readonly context: vscode.ExtensionContext,
    public readonly obj: IPackageJson,
  ) {
    this.context = context;
  }

  private static _instance: PackageJsonData | null = null;

  public static async create(
    context: vscode.ExtensionContext,
    logger: ILogger,
  ): Promise<PackageJsonData> {
    if (!this._instance) {
      const packageJson = await readPackageJson(context, logger);
      this._instance = new PackageJsonData(context, packageJson);
    }
    return this._instance;
  }

  public async getMenuCommands(
    menus: string,
    viewItem: string,
    groupName?: string,
  ): Promise<MenuCommandDescriptor[]> {
    const contributes = this.obj.contributes ?? {};
    const commandEntries: any[] = contributes.commands ?? [];
    const commandMap = new Map<string, any>();
    for (const entry of commandEntries) {
      if (entry?.command) {
        commandMap.set(entry.command, entry);
      }
    }

    const menusSection = contributes.menus ?? {};
    const menuBucket: any[] = menusSection[menus] ?? [];
    if (!Array.isArray(menuBucket)) {
      return [];
    }

    const results: Array<{
      descriptor: MenuCommandDescriptor;
      order: number;
      index: number;
    }> = [];
    for (const [index, item] of menuBucket.entries()) {
      if (!item?.command) {
        continue;
      }
      const whenSatisfied = evaluateWhenClause(item.when, viewItem);
      if (!whenSatisfied) {
        continue;
      }

      const groupInfo = parseGroup(item.group);
      if (groupName && (!groupInfo || groupInfo.name !== groupName)) {
        continue;
      }

      const commandInfo = commandMap.get(item.command) ?? {};
      results.push({
        descriptor: {
          command: item.command,
          title: commandInfo.title ?? item.command,
          category: commandInfo.category,
          icon: commandInfo.icon,
          group: item.group,
        },
        order: groupInfo?.order ?? Number.POSITIVE_INFINITY,
        index,
      });
    }

    results.sort((a, b) => {
      if (a.order === b.order) {
        return a.index - b.index;
      }
      return a.order - b.order;
    });

    return results.map((entry) => entry.descriptor);
  }
}

function parseGroup(
  group: unknown,
): { name: string; order: number } | undefined {
  if (typeof group !== "string") {
    return undefined;
  }
  const trimmed = group.trim();
  if (!trimmed) {
    return undefined;
  }

  const [namePart, orderPart] = trimmed.split("@", 2);
  const name = namePart.trim();
  if (!name) {
    return undefined;
  }

  if (orderPart === undefined || orderPart.trim() === "") {
    return { name, order: Number.POSITIVE_INFINITY };
  }

  const numericOrder = Number(orderPart);
  if (!Number.isFinite(numericOrder)) {
    return { name, order: Number.POSITIVE_INFINITY };
  }

  return { name, order: numericOrder };
}

function evaluateWhenClause(when: unknown, viewItem: string): boolean {
  if (!when || typeof when !== "string") {
    return true;
  }
  const expression = when.trim();
  if (!expression) {
    return true;
  }
  return evaluateExpression(expression, viewItem);
}

function evaluateExpression(expression: string, viewItem: string): boolean {
  const trimmed = expression.trim();
  if (!trimmed) {
    return true;
  }

  if (trimmed.startsWith("(")) {
    const maybe = unwrapParentheses(trimmed);
    if (maybe !== trimmed) {
      return evaluateExpression(maybe, viewItem);
    }
  }

  const orParts = splitTopLevel(trimmed, "||");
  if (orParts.length > 1) {
    return orParts.some((part) => evaluateExpression(part, viewItem));
  }

  const andParts = splitTopLevel(trimmed, "&&");
  if (andParts.length > 1) {
    return andParts.every((part) => evaluateExpression(part, viewItem));
  }

  if (trimmed.startsWith("!")) {
    return !evaluateExpression(trimmed.slice(1), viewItem);
  }

  return evaluateSimpleCondition(trimmed, viewItem);
}

function evaluateSimpleCondition(condition: string, viewItem: string): boolean {
  const trimmed = stripOuterParentheses(condition.trim());
  if (!trimmed) {
    return true;
  }

  if (trimmed === "true") {
    return true;
  }
  if (trimmed === "false") {
    return false;
  }

  const regexMatch = trimmed.match(/^viewItem\s*=~\s*\/(.*)\/([a-z]*)$/i);
  if (regexMatch) {
    const [, pattern, flags] = regexMatch;
    try {
      const regex = new RegExp(pattern, flags);
      return regex.test(viewItem);
    } catch (error) {
      console.error("Invalid when clause regex", condition, error);
      return false;
    }
  }

  const equalsMatch = trimmed.match(/^viewItem\s*==\s*(.+)$/);
  if (equalsMatch) {
    const target = normalizeLiteral(equalsMatch[1]);
    return viewItem === target;
  }

  const notEqualsMatch = trimmed.match(/^viewItem\s*!=\s*(.+)$/);
  if (notEqualsMatch) {
    const target = normalizeLiteral(notEqualsMatch[1]);
    return viewItem !== target;
  }

  return false;
}

function stripOuterParentheses(value: string): string {
  let result = value;
  while (
    result.startsWith("(") &&
    result.endsWith(")") &&
    isMatchingParentheses(result)
  ) {
    result = result.slice(1, -1).trim();
  }
  return result;
}

function unwrapParentheses(value: string): string {
  let result = value;
  while (
    result.startsWith("(") &&
    result.endsWith(")") &&
    isMatchingParentheses(result)
  ) {
    result = result.slice(1, -1).trim();
  }
  return result;
}

function isMatchingParentheses(value: string): boolean {
  let depth = 0;
  for (const ch of value) {
    if (ch === "(") {
      depth += 1;
    } else if (ch === ")") {
      depth -= 1;
      if (depth < 0) {
        return false;
      }
    }
  }
  return depth === 0;
}

function splitTopLevel(value: string, operator: "&&" | "||"): string[] {
  const parts: string[] = [];
  let depth = 0;
  let current = "";
  for (let i = 0; i < value.length; i += 1) {
    const ch = value[i];
    if (ch === "(") {
      depth += 1;
      current += ch;
      continue;
    }
    if (ch === ")") {
      depth -= 1;
      current += ch;
      continue;
    }
    if (depth === 0 && value.slice(i, i + operator.length) === operator) {
      parts.push(current.trim());
      current = "";
      i += operator.length - 1;
      continue;
    }
    current += ch;
  }
  if (current.trim()) {
    parts.push(current.trim());
  }
  return parts.length ? parts : [value];
}

function normalizeLiteral(value: string): string {
  const trimmed = value.trim();
  if (
    (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
    (trimmed.startsWith("'") && trimmed.endsWith("'"))
  ) {
    return trimmed.slice(1, -1);
  }
  return trimmed;
}
