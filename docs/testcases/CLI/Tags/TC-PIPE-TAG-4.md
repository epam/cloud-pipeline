# FOLDER: Set, get, delete tags specified by folder name

**Actions**:

Create folder

1.	Create tags for object: `pipe tag set folder <folder NAME> key_1=value_1 key_2=value_2`
2.	Delete keys: `pipe tag delete folder <folder NAME> key_1 key_3`
3.	Update existing key: `pipe tag set folder <folder NAME> key_1=new_value`
4.	Add new keys: `pipe tag set folder <folder NAME> key_3=value_3 key_4=value_4`
5.	Delete all data: `pipe tag delete folder <folder NAME>`

Delete folder

***

**Expected result**:
1.	Message: 'Metadata for folder <folder NAME> updated.'
make sure data really has been uploaded: `pipe tag get folder <folder NAME>`
2.	Message: 'Deleted keys from metadata for folder <folder NAME>: key_1, key_3 '
make sure key_1 has been deleted: `pipe tag get folder <folder NAME>`
3.	Message: 'Metadata for folder <folder NAME> updated.'
make sure data really has been updated: `pipe tag get folder <folder NAME>`
4.	Message: 'Metadata for folder <folder NAME> updated.'
make sure data really has been updated: `pipe tag get folder <folder NAME>`
5.	Message: 'Metadata for folder <folder NAME> deleted.'
make sure data really has been deleted: `pipe tag get folder <folder NAME>`