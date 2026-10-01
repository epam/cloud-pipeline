# Role model validation for set parameters pop-up on launch page

Test verifies that the path-parameter browse pop-up on the launch form only shows storages and folders the user has READ permission on.

**Prerequisites**:
- Login as a user who has READ permissions to a specific storage and some folders from that storage only

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline, select version |  |
| 4 | Click **RUN** button |  |
| 5 | Hover over the **v** element to the right of the **Add parameters** button: select **Output path parameter** item; specify the parameter name in the left field of the appeared row; click the grey button of the right field |  |
| 6 | Repeat step 5 for the other parameters from the dropdown list - **Path parameter**, **Input path parameter**, **Common path parameter** | After steps 5 and 6, only the storage for which the user has READ permissions is displayed on the left panel; only the folders for which the user has READ permissions are displayed in that storage |
