# PIPELINE: Set, get, delete tags specified by pipeline name

**Actions**:

Create pipeline

1.	Create tags for object: `pipe tag set pipeline <pipeline NAME> key_1=value_1 key_2=value_2`
2.	Delete keys: `pipe tag delete pipeline <pipeline NAME> key_1 key_3`
3.	Update existing key: `pipe tag set pipeline <pipeline NAME> key_1=new_value`
4.	Add new keys: `pipe tag set pipeline <pipeline NAME> key_3=value_3 key_4=value_4`
5.	Delete all data: `pipe tag delete pipeline <pipeline NAME>`

Delete pipeline

***

**Expected result**:
1.	Message: 'Metadata for pipeline <pipeline NAME> updated.'
make sure data really has been uploaded: `pipe tag get pipeline <pipeline NAME>`
2.	Message: 'Deleted keys from metadata for pipeline <pipeline NAME>: key_1, key_3 '
make sure key_1 has been deleted: `pipe tag get pipeline <pipeline NAME>`
3.	Message: 'Metadata for pipeline <pipeline NAME> updated.'
make sure data really has been updated: `pipe tag get pipeline <pipeline NAME>`
4.	Message: 'Metadata for pipeline <pipeline ID> updated.'
make sure data really has been updated: `pipe tag get pipeline <pipeline NAME>`
5.	Message: 'Metadata for pipeline <pipeline NAME> deleted.'
make sure data really has been deleted: `pipe tag get pipeline <pipeline NAME>`