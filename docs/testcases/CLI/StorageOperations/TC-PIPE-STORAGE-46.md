# RM operation rm for storage's root

**Actions**:
1.	Create storage `[pipe storage create --name {storage_name} ..]`
2.	Try to delete everything from storage's root: `[pipe storage rm cp://storage_name/ --recursive]`

***
**Expected result:**

Deleting everything from root storage is not allowed, exit_code should be 1 with appropriate error message