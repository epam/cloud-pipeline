# [MANUAL] [Negative] Try to restore not removed file

Test verifies that restoring a file with no delete marker reports the expected error.

**Prerequisites**:
- A file without a delete marker exists

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Run `pipe storage restore cp://{bucket_name}/{file_name}` | The message `Error: Latest version in the buckets is not a delete marker. Please specify "--version" parameter.` is displayed |
