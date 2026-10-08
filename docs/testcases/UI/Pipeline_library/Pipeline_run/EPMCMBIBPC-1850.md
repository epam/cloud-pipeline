# [MANUAL] Output to read only buckets validation

Test verifies that a pipeline run cannot write a file into a bucket folder for which its owning user only has read permission.

**Prerequisites**:
- A non-admin user with the ROLE_PIPELINE_MANAGER role and rights to create pipelines

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Create a bucket |  |
| 3 | Click the edit button |  |
| 4 | Click **Permissions** |  |
| 5 | Add the user from the prerequisites |  |
| 6 | Set read permissions |  |
| 7 | Create a folder in the bucket |  |
| 8 | Login as the user from the prerequisites |  |
| 9 | Create a pipeline |  |
| 10 | Add file generation to the pipeline's main file (`echo "test" > "test.txt"`) |  |
| 11 | Save and commit the changes |  |
| 12 | Click **CONFIGURATION** |  |
| 13 | Add an **Output path parameter** |  |
| 14 | Specify the variable name and the path to the folder in the bucket created at step 7 |  |
| 15 | Save the configuration |  |
| 16 | Launch the pipeline |  |
| 17 | Go to the running pipeline's page |  |
| 18 | Click **Parameters** |  |
| 19 | After the pipeline finishes, navigate to the path specified at step 17 | The file is not displayed in the bucket |
