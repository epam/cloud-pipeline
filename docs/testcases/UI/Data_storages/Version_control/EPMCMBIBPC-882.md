# [MANUAL] Validation of file versions

Test verifies that listing with versions shows all uploaded revisions of a file with correct sizes and the `latest` marker.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Copy a file to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 2 | Copy a file with the same name and a different size to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 3 | List with details: `pipe storage ls cp://{bucket_name} -l` | One record about the file is displayed, the **Size** field shows the size of the last uploaded file |
| 4 | List with versions and details: `pipe storage ls cp://{bucket_name} -v -l` | 3 records are displayed: <li> the first record has an empty **Version** value, **Size** shows the size of the last uploaded file <li> the second record has a non-empty **Version** value with the `latest` mark in parentheses, **Size** shows the size of the last uploaded file <li> the third record has a non-empty **Version** value, **Size** shows the size of the file uploaded at step 1 |
