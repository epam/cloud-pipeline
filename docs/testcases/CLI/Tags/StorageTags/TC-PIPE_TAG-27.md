# Validation of set, update, delete tag for S3 objects by non-admin and non-owner user 

**Prerequisites**:

Create data storage with files in the root folder. 
Files shall have tags. 

**Actions**:
1.	Perform command `pipe storage ls cp://{bucket_name}/{file_name} -v -l`
2.	Perform command `pipe storage set-object-tags {bucket_name} {file_name} new_key=value`
3.	Perform command `pipe storage set-object-tags {bucket_name} {file_name} existing_key_name=new_value`
4.	Perform command `pipe storage delete-object-tags {bucket_name} {file_name} \existing_key_name`

***

**Expected result**:
1.	Access denied message
2.	Access denied message
3.	Access denied message
4.	Access denied message