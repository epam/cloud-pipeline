# [MANUAL] Advanced_filter by partly non equal owner validation

Test verifies filtering the advanced search results using `owner`.

**Prerequisites**:
- Several completed pipelines launched by different users

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `owner!={part of existing user name} (e.g. `owner!=*epm-cmbi*`)` into the search field and press **Enter** | All pipelines are displayed except those launched by a user whose name contains the substring specified at step 2 |
