# Navigation test for set parameters pop-up on launch page

Test verifies navigating the storage tree in the path-parameter browse pop-up on the launch form.

**Prerequisites**:
- Login as Admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline, select version |  |
| 4 | On the pipeline page select **CODE** tab |  |
| 5 | Add a script for saving files into the code file (replace the code file with the attached one) |  |
| 6 | Click **RUN** button |  |
| 7 | Hover over the **v** element to the right of the **Add parameters** button |  |
| 8 | Select **Output path parameter** item |  |
| 9 | Specify the parameter name in the left field of the appeared row |  |
| 10 | Click the grey button of the right field |  |
| 11 | Click the triangle icon in front of a closed folder | The folder tree in the expanded view is displayed on the left panel |
| 12 | Click the triangle icon in front of the expanded folder | The subfolders and storages in the folder are no longer displayed on the left panel |
| 13 | Click on a storage | The storage's content is displayed |
