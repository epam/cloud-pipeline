# LS operation from root with role model

**Requirements:**
Owner user and other TestUser

**Actions**:
1.	Create storage `storage_name` as Owner
2.	Call: `pipe storage ls`
3.	Switch user on TestUser
4.	Call: `pipe storage ls`
5.	Delete storage

***
**Expected result:**

1.	Check that `storage_name` has been listed after step 2
2.	Check that `storage_name` hasn't been listed after step 4