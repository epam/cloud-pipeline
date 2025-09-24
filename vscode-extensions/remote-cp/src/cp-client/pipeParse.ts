export function pipeParse<TInfo>(
  table: string,
  objFactory: (cells: string[], header: string[]) => TInfo,
): TInfo[] {
  const lines = table
    .split("\n")
    .filter((line) => line.trim().startsWith("|") /* remove separator lines */);
  if (lines.length < 2) {
    return [];
  }

  // First line is header
  const headerLine = lines[0];
  const headerColNameList = headerLine
    .split("|")
    .map((h) => h.trim())
    .slice(1, -1);

  const runLineList = lines.slice(1); // skip header

  return runLineList
    .map((line) =>
      line
        .split("|")
        .map((cell) => cell.trim())
        .slice(1, -1),
    )
    .map((cells) => {
      return objFactory(cells, headerColNameList);
    });
}
