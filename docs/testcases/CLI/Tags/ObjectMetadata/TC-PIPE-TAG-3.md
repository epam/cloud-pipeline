# FOLDER: Set, get, delete tags specified by folder id

**Actions**:

Create folder

1.	Create tags for object: `pipe tag set folder <folder ID> key_1=value_1 key_2=value_2`
2.	Delete keys: `pipe tag delete folder <folder ID> key_1 key_3`
3.	Update existing key: `pipe tag set folder <folder ID> key_1=new_value`
4.	Add new keys: `pipe tag set folder <folder ID> key_3=value_3 key_4=value_4`
5.	Delete all data: `pipe tag delete folder <folder ID>`

Delete folder

***

**Expected result**:
1.	Message: 'Metadata for folder <folder ID> updated.'
make sure data really has been uploaded: `pipe tag get folder <folder ID>`
2.	Message: 'Deleted keys from metadata for folder <folder ID>: key_1, key_3 '
make sure key_1 has been deleted: `pipe tag get folder <folder ID>`
3.	Message: 'Metadata for folder <folder ID> updated.'
make sure data really has been updated: `pipe tag get folder <folder ID>`
4.	Message: 'Metadata for folder <folder ID> updated.'
make sure data really has been updated: `pipe tag get folder <folder ID>`
5.	Message: 'Metadata for folder <folder ID> deleted.'
make sure data really has been deleted: `pipe tag get folder <folder ID>`