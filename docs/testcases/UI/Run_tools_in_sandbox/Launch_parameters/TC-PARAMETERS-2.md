# Check the configure allowed instance types for docker images

Test verifies that **`cluster.allowed.instance.types.docker`** preference has value specified at deploy.

**Prerequisites**:
- Admin user
- The **`cluster.allowed.instance.types.docker`** preference value that should be specified at deploy

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Settings** page | |
| 3 | Click the **PREFERENCES** tab | |
| 4 | Click the **Cluster** tab | |
| 5 | Find the **`cluster.allowed.instance.types.docker`** preference | The **`cluster.allowed.instance.types.docker`** preference contains the value from Prerequisites |
