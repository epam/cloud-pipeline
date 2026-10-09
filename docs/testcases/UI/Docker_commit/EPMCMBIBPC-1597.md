# [MANUAL] Commit pipeline as docker image by user with restricted permissions

Test verifies that a user with only execute permission on the pipeline's docker image can still commit the running pipeline as a new tool.

**Prerequisites**:
- User has a role without any permissions on registries, groups and tools
- User has all permissions in the registry denied
- User is granted read+write permissions in the tool group
- User is granted all permissions in the directory a pipeline is in
- User is granted execute permissions on the tool with the docker image the pipeline uses

**Preparations**:
1. Open the pipeline from the prerequisites
2. Click **Run** button
3. Make sure the settings are valid
4. Click **Launch** button
5. Open the running pipeline tab
6. Wait until the **COMMIT** button appears

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **COMMIT** button |  |
| 2 | Select the registry and tools group from the prerequisites |  |
| 3 | Enter a new unique tool name |  |
| 4 | Click **COMMIT** button | The **COMMIT** button immediately changes to unavailable, then the commit finishes successfully |
| 5 | Wait until the commit is done |  |
| 6 | Go to the tools group | The committed tool can be found in the tools group |
