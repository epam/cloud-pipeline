# [Negative] Delete non-existing key from an object

**Prerequisites**:

File shall not have any tags. 

**Actions**:
1.	Call `pipe storage set-object-tags {bucket_name} {file_name} key=value`
2.	Call `pipe storage delete-object-tags {bucket_name} {file_name} {unexisting_tag}`
3.	Call `pipe storage get-object-tags {bucket_name} {file_name}`

***

**Expected result**:

Should return appropriate error message that key isn't found.
After step 3 pair key=value from step 1 is returned.