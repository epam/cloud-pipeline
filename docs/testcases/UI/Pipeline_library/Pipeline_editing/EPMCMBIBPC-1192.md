# [MANUAL] [Negative] Try to upload wrong named file and correct named file

Test verifies that uploading a file whose name contains parentheses is rejected, while a file with spaces in its name is accepted with the spaces replaced by underscores.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a pipeline |  |
| 2 | Go into the pipeline, to the **CODE** tab |  |
| 3 | Click the **UPLOAD** button |  |
| 4 | Select files to upload in the appeared OS dialog and confirm the upload: one file's name should contain parentheses, the other's name should contain spaces | <li> the `Uploading files...` pop-up appears <li> the pop-up shows as many progress bars as files were selected <li> for the file whose name contains parentheses, an error message is displayed: `Your changes could not be committed, because the file name, `src/2 (1).txt` can contain only letters, digits, '_', '-', '@' and '.'. Separate directories with a '/'.` <li> the file whose name contains spaces uploads without errors and is displayed in the pipeline's file list, with the spaces replaced by `_` |
