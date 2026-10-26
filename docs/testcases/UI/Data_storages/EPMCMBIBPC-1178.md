# [MANUAL] Validation of upload several files

Test verifies uploading several files at once shows a progress bar per file and lists the uploaded files sorted after the folders.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Select a bucket |  |
| 2 | Click **UPLOAD** button |  |
| 3 | Select several files to upload in the appeared OS dialog and confirm the upload | <li> the `Uploading files...` pop-up appears <li> the pop-up shows as many progress bars as files were selected <li> the file name is displayed above each progress bar <li> the files appear in the root directory of the bucket <li> the files are sorted alphabetically after the folders |
