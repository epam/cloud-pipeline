# [MANUAL] Validation of spaces and slash symbol in NFS mount path

Test verifies creating an NFS mount whose path contains spaces and a slash, and creating a folder and a file in it.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Click **Library** button |  |
| 3 | Click **Create** button |  |
| 4 | Hover over **Storage** |  |
| 5 | Click **Create new FS mount** |  |
| 6 | In the **Storage path** field, select a pre-registered file-share mount from the dropdown on the left, then enter a value like `/{NFS STORAGE NAME WITH SPACES}/{SECOND PART}` into the path input on the right |  |
| 7 | Click **Create** button | A new NFS storage appears |
| 8 | Click on the created storage | The page of the storage created at step 7 opens |
| 9 | Create a folder and a file | The folder and file are successfully created |
