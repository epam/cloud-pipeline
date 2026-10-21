# [MANUAL] Validation of restore marked for deletion non-empty folder

Test verifies that restoring a non-empty folder marked for deletion brings it back.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-885](EPMCMBIBPC-885.md) case |  |
| 2 | Restore: `pipe storage restore cp://{bucket_name}/{folder1_name}` |  |
| 3 | List: `pipe storage ls cp://{bucket_name}/` | The restored (previously deleted) folder is displayed |
