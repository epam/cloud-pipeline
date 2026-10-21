# [MANUAL] Run pipeline without permissions on docker image validation

Test verifies that launching a pipeline whose Docker image the user has no permissions on is denied.

**Prerequisites**:
- The user has permissions to create a pipeline from a template (ROLE_PIPELINE_MANAGER)
- The user has no permissions on the Docker images used in the templates

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a pipeline (e.g. SHELL, WDL, DEFAULT) |  |
| 2 | Click the **Run** button |  |
| 3 | Click the **Launch** button | The message "Access denied" appears |
