# [MANUAL] Commit a tool with denied permissions on the group and write permission on the registry

Test verifies that write-only permission on the registry, with all permissions denied on the group, is not enough to enable the commit button.

**Prerequisites**:
- Grant the user write-only permissions on the registry (default registry)
- Deny the user all permissions on the group (library)
- Grant the user read and execute permissions on the tool from the group

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **Tools** button at the navigation panel |  |
| 2 | Open the tool from the prerequisites |  |
| 3 | Run the tool |  |
| 4 | Open the running tool's log tab |  |
| 5 | Wait until the **COMMIT** button appears |  |
| 6 | Click **COMMIT** button |  |
| 7 | Select the registry and tool group from the prerequisites |  |
| 8 | Enter a new unique name for the tool |  |
| 9 | Click **Commit** button | The commit button is inactive |
