# [MANUAL] Role_model validation in Advanced_filtering

Test verifies that a non-admin user filtering by owner only sees their own runs, whether the search is by full or partial user name.

**Prerequisites**:
- A non-admin user, from whom pipeline runs were launched, who has no permissions on other pipelines/tools

| Steps | Actions                                                                                                                                                                                                                                                          | Expected results |
| :---: |------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| --- |
| 1 | Login as the user from the prerequisites                                                                                                                                                                                                                         |  |
| 2 | Open the **RUNS** page                                                                                                                                                                                                                                           |  |
| 3 | Click the **Advanced filter** label                                                                                                                                                                                                                              |  |
| 4 | Perform the cases: <br> 1. perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case; <br> 2. enter into the search field a string like `owner={existing user name}`; <br> 3. enter into the search field a string like `owner={start of the user name from step 1}*` | <li> after step 4.2, no records are displayed <li> after step 4.3, only pipelines launched by the user from step 1 are displayed |
