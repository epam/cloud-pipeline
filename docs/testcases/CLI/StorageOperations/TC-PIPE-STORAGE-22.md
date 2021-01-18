# MV operation: wrong schema

**Actions**:

1.	Create storage
2.	Call `pipe storage mv local cp://...`
3.	Call `pipe storage mv cp://... local`
4.	Call `pipe storage mv cp://... cp://...`
5.	Call `pipe storage mv cp://... cp://...`
6.	Delete storage

***
**Expected result:**

Command should fail and print appropriate error message