# [MANUAL] Run pipeline without permissions on docker image validation

Test verifies that launching a pipeline whose Docker image the user has no permissions on is denied.

**Prerequisites**:
- The user has permissions to create a pipeline from a template (ROLE_PIPELINE_MANAGER)
- The user has no permissions on the Docker images used in the templates (e.g. `172.31.38.143:5000/library/base-generic-centos7`, `172.31.38.143:5000/library/exec-cromwell`, `172.31.38.143:5000/library/exec-luigi`)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a pipeline (e.g. SHELL, WDL, DEFAULT) |  |
| 2 | Click the **Run** button |  |
| 3 | Click the **Launch** button | The message "Access denied" appears |
