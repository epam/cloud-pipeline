# [Negative] Set tags with invalid value (without '=' delimiter)

**Actions**:
1. Call `pipe storage set-object-tags {storage_name} {file_name} key `

***

**Expected result**:

Should return appropriate error message that parameter is specified incorrectly.