# Validation of get tag for S3 objects by non-admin and non-owner user

**Prerequisites**:

Create data storage with files in the root folder. 
File shall have tags. 
Non-admin and non-owner User shall have full access permissions for storage.

**Actions**:
1.	Perform command `pipe storage get-object-tags {bucket_name} {file_name}`

***

**Expected result**:
1.	The command output contains the table with data about pairs key = value for {file_name}