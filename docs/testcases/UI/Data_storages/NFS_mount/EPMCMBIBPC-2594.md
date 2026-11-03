# NFS File upload/download validation

Test verifies that a file downloaded from an NFS mount matches the file that was uploaded to it.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Open an existing NFS mount |  |
| 3 | Click on **Upload** button |  |
| 4 | Select a file in the appeared pop-up window and confirm the upload |  |
| 5 | Click on the download icon opposite the uploaded file name | The name and size of the downloaded file are equal to the file uploaded at step 4 to the NFS storage page |
