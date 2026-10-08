# [MANUAL] [Negative] Try to upload file that have spaces in file name

Test verifies that uploading a file whose name contains spaces succeeds, with the spaces replaced by underscores.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a pipeline |  |
| 2 | Go into the pipeline, to the **CODE** tab |  |
| 3 | Click the **UPLOAD** button |  |
| 4 | Select a file to upload in the appeared OS dialog and confirm the upload (the file name should contain spaces) | <li> the `Uploading files...` pop-up appears <li> the pop-up shows a progress bar <li> the file uploads, and in the pipeline the space is replaced by `_` |
