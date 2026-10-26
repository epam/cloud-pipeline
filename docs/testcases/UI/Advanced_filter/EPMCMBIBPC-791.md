# [MANUAL] Validation of "AND" and "OR" operands in one request

Test verifies combining `and`/`or` operands in one query, mixing the conditions of the [EPMCMBIBPC-790](EPMCMBIBPC-790.md) case with an owner/parameter condition.

**Prerequisites**:
- Several pipelines launched by different users with different disk size values and on different instance types, completed with different statuses

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | (node.disk>20 and node.type="c4.l*" and run.status=FAILURE) or (owner="*test2*" and parameter.p1=value1) - enter this into the search field and press **Enter** |  |
| 3 | Click on a pipeline | <li> pipelines with parameter `p1` equal to `value1` and launched by a user whose name contains the substring `test2` are displayed <li> pipelines from the [EPMCMBIBPC-790](EPMCMBIBPC-790.md) case are displayed |
