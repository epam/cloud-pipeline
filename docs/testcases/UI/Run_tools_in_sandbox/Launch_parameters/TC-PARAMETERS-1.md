# Check the configure allowed instance types

Test verifies that **`cluster.allowed.instance.types`** preference has value specified at deploy.

**Prerequisites**:
- Admin user
- The **`cluster.allowed.instance.types`** preference value that should be specified at deploy

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Settings** page | |
| 3 | Click the **PREFERENCES** tab | |
| 4 | Click the **Cluster** tab | |
| 5 | Find the **`cluster.allowed.instance.types`** preference | The **`cluster.allowed.instance.types`** preference contains the value from Prerequisites |