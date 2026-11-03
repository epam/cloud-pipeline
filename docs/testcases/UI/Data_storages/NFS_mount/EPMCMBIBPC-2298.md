# [MANUAL] Validation of spaces and slash symbol in NFS mount path

Test verifies creating an NFS mount whose path contains spaces and a slash, and creating a folder and a file in it.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Click **Library** button |  |
| 3 | Click **Create** button |  |
| 4 | Hover over **Storage** |  |
| 5 | Click **Create new NFS mount** |  |
| 6 | Enter a string like `nfs://fs-2a5ab373.efs.eu-central-1.amazonaws.com:/{NFS STORAGE NAME WITH SPACES}/{SECOND PART}` into the **Storage path** field |  |
| 7 | Click **Create** button | A new NFS storage appears |
| 8 | Click on the created storage | The page of the storage created at step 7 opens |
| 9 | Create a folder and a file | The folder and file are successfully created |
