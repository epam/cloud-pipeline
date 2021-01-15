# CP operation: wrong schema

**Actions**:

1.  Create storage
2.	Try to Put: `local cp://...`
3.	Try to Put file with path `cp://... local` to local machine
4.	Try to Put file with path `cp://... cp://...` to the created storage
5.	Try to Put file with path `cp://... cp://...` to the created storage
6.	Delete storage


***
**Expected result:**

In all these cases, an error occurs: try to Put a file with the path cp to the storage