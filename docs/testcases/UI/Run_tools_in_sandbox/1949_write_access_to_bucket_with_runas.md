# User And Write Access To Bucket With Run As Option

Test verifies that user that has Read, Execute access to a pipeline with input and output parameters and no access to the output storage and Tool can start that pipeline without any warnings.

**Prerequisites**:
- Admin user
- Non-admin user
- Tool isn't available to the non-admin user (*tool1*)

**Preparations**
1. Login as admin user from the prerequisites
2. Create and open the object storage (*storage1*)
3. Create a folder in the created storage (*folder1*)
4. Create a file in the created storage (*file1*)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites | |
| 2 | Create and open the pipeline | |
| 3 | Open the **Configuration** tab | |
| 4 | Select *tool1* as value for *Docker image* and specify valid values for required fields| |
| 5 | Navigate into the ***Parameters*** section | |
| 6 | Add <li> *Input path parameter* `in` with value `<storage1_path>/<folder1>` <li> *Output path parameter* `out` with value `<storage1_path>/<file1>` | |
| 7 | Click **Save** button | |
| 8 | Open **Code** tab | |
| 9 | Open `config.json` file. Click **Edit** button | |
| 10 |  In the "configuration" section specify: `"run_as": "admin_user_name"`, where `admin_user_name` is the admin name from the prerequisites . Click **Save** button | |
| 11 | Click the **gear** icon in the right upper corner | |
| 12 | Click the **Edit** button in the appeared list | |
| 13 | Click the **Permissions** tab | |
| 14 | Add the non-admin user from the prerequisites to the permissions list and | |
| 15 | Allow ***Read*** and ***Execute*** permissions to the non-admin user from the prerequisites | |
| 16 | Close the pop-up | |
| 17 | Open the **Settings** page | |
| 18 | Select the ***User Management*** tab | The **Users** tab opens by default |
| 19 | Click the *admin user* from the prerequisites in the table | |
| 20 | Click the *configure* link next to the *Can run as this user:* | |
| 21 | Click the **Add user** button on the *Share with users and groups* pop up | |
| 22 | Specify the non-admin user name from prerequisites. Click the **OK** button | |
| 23 | Save changes by the **OK** button | |
| 24 | Logout | |
| 25 | Login as non-admin user from prerequisites | |
| 26 | Open the **Library** page | |
| 27 | Click the pipeline created at step 2 | |
| 28 | Click the **RUN** button | |
| 29 | Click the **Launch** button | ***Launch*** pop up appears that doesn't contain any error messages | |
| 30 | Click the **Launch** button | |
| 31 | Open the **Runs** page | |
| 32 | Click *View other available active runs* link | Launched pipeline run is displayed in the table |
