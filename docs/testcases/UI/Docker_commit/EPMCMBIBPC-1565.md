# [MANUAL] [NEGATIVE] Commit docker from stopped tool

Test verifies that a run that stopped due to an error can no longer be committed.

**Prerequisites**:
- An existing tool with filled-in **Instance type** and **Disk** fields

**Preparations**:
1. Click **Tools** button at the navigation panel
2. Select tool
3. Open the tool's **Settings** tab
4. Replace the **Default command** field content with the address of a nonexistent file (e.g. `/moose.txt`)
5. Save the changes
6. Click **Run** button
7. Confirm the launch with default settings
8. Click on the running tool

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Wait until the **COMMIT** button appears |  |
| 2 | Click **COMMIT** button |  |
| 3 | Enter a unique tool name |  |
| 4 | Wait until the tool stops because of an error (file doesn't exist) | The **COMMIT** button in the upper right corner disappears |
| 5 | Click **COMMIT** button | An error message appears: `You can commit only running pipelines` |
