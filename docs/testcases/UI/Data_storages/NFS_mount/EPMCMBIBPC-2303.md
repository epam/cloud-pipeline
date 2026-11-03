# [MANUAL] Using of Input/output data from NFS in pipeline validation

Test verifies that Input, Output and Common path parameters of a pipeline correctly read from and write to folders and files on an NFS mount.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform [EPMCMBIBPC-2274](EPMCMBIBPC-2274.md) |  |
| 2 | In the storage's root folder, create an empty folder `out` |  |
| 3 | In the storage's root folder, create a folder `common` |  |
| 4 | In the `common` folder, create 2 non-empty files `common1.txt` and `common2.txt` |  |
| 5 | In the storage's root folder, create a folder `common2` |  |
| 6 | In the `common2` folder, create a subfolder `common3` and a non-empty file `common3` |  |
| 7 | In the `common2` folder, create a non-empty file `common4` |  |
| 8 | In the storage's root folder, create a folder `in` |  |
| 9 | In the `in` folder, create a non-empty file `in.txt` |  |
| 10 | In the `in` folder, create a non-empty file `not_copied.txt` |  |
| 11 | Create a pipeline from the `SHELL` template |  |
| 12 | Replace the pipeline code with the code from the attached file |  |
| 13 | Open the **CONFIGURATION** tab of the created pipeline |  |
| 14 | Add a parameter of type **Output path parameter** |  |
| 15 | Specify the path to the `out` folder from step 2 as its value |  |
| 16 | Add a parameter of type **Input path parameter** |  |
| 17 | Specify the path to the `in.txt` file from step 9 as its value |  |
| 18 | Add a parameter of type **Common path parameter** |  |
| 19 | Specify the path to the `common` folder from step 3 and the path to the `common3` file as its values |  |
| 20 | Save the configuration |  |
| 21 | Launch the pipeline |  |
| 22 | Wait until it completes successfully |  |
| 23 | Open the `out` folder created at step 2 | The `out` folder displays: <li> the `common` folder, containing the files `common1.txt` and `common2.txt` (created at step 4) <li> the `common2` folder, containing the file `common3` <li> the `in` folder (created at step 8) - opening it shows only the file `in.txt` (created at step 9); the file `not_copied.txt` (created at step 10) is not displayed |
