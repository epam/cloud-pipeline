# [MANUAL] Overwrite tool version by user with restricted permissions

Test verifies that a user with full permissions only on the specific tool can overwrite its latest version by committing.

**Prerequisites**:
- User has a role without any permissions on registries, groups and tools
- User has write and execute denied on the tool group
- User is granted or denied any permissions in the registry
- User is granted all permissions on the tool located in the tool group above

**Preparations**:
1. Open the tool (default registry - library - endpoint-test)
2. Make sure all fields in the **Settings** tab are filled in with valid values
3. Click **Run** button
4. Confirm the run with default settings
5. Open the running tool's log page
6. Wait until the **COMMIT** button appears

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **COMMIT** button |  |
| 2 | Select the registry, tool group and tool name from the prerequisites (default registry - library - endpoint-test) |  |
| 3 | Select the existing version (latest) |  |
| 4 | Click **COMMIT** button | The **COMMIT** button immediately changes to unavailable and the commit finishes successfully |
