# Create > Pipeline offers no WDL and LUIGI templates

Test verifies that:
- the **Pipeline** list of the **+ Create** menu has the **DEFAULT** item and the built-in pipeline templates, and no **WDL** or **LUIGI** item
- an admin also sees the **AWS-HEALTHOMICS-WORKFLOW** template, and a pipeline manager does not

**Prerequisites**:
- Admin user
- Non-admin user with the `ROLE_PIPELINE_MANAGER` role
- The API service uses the pipeline templates of its image: its `CP_PREF_TEMPLATES_DIRECTORY_EXT` folder is empty or not set (a default install mounts the `/opt/api/templates/pipe-templates` host folder there), and `CP_PREF_TEMPLATES_DEFAULT_TEMPLATE` is not set (the default template is `empty`)

**Preparations**:
1. Login as the admin user from the prerequisites
2. Open the **Library** page, create the folder `folder1`
3. Open `folder1`, click the gear icon in the upper-right corner, click **Edit folder**
4. In the pop-up that appears, open the **Permissions** tab, add the non-admin user from the prerequisites, and set the checkboxes in the **Allow** column opposite the **Read** and **Write** permissions. Close the pop-up

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Library** page, open `folder1` | |
| 3 | Hover over **+ Create v**, then hover over **Pipeline** in the list that appears | The list that appears has: <li> **DEFAULT** with the "Create pipeline without template" description <li> these templates, in any order, each with its description: **CWL** (CWL pipeline engine), **NEXTFLOW** (Nextflow pipeline engine), **PYTHON** (Pure Python pipeline template), **SHELL** (Shell pipeline template), **SNAKEMAKE** (Snakemake pipeline engine), **AWS-HEALTHOMICS-WORKFLOW** (AWS HealthOmics Workflow pipeline) <li> no other item. There is no **WDL** and no **LUIGI** item |
| 4 | Logout and login as the non-admin user from the prerequisites | |
| 5 | Repeat steps 2-3 | The list that appears has: <li> **DEFAULT** with the "Create pipeline without template" description <li> the **CWL**, **NEXTFLOW**, **PYTHON**, **SHELL** and **SNAKEMAKE** templates, in any order, each with its description <li> no other item. There is no **AWS-HEALTHOMICS-WORKFLOW**, **WDL** or **LUIGI** item |

**After**:
- Delete `folder1`
