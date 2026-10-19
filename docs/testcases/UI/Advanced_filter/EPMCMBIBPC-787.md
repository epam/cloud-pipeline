# [MANUAL] Advanced_filtering by node.name

Test verifies filtering the advanced search results by `node.name` using `=`, `!=`, and (partial) `*` matches.

**Prerequisites**:
- Several pipelines launched on different nodes

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `node.name = "ip-{'-'-delimited ip address}"` into the search field and press **Enter** |  |
| 3 | Click on a pipeline |  |
| 4 | Expand the **Instance** element | For all found pipelines, the **ip** field value equals the value specified at step 2 |
| 5 | Return to the search page |  |
| 6 | Enter `node.name != "ip-{'-'-delimited ip address}"` into the search field and press **Enter** |  |
| 7 | Click on a pipeline |  |
| 8 | Expand the **Instance** element | For all found pipelines, the **ip** field value is not equal to the value specified at step 6 |
| 9 | Return to the search page |  |
| 10 | Enter `node.name = "ip-{part of '-'-delimited ip address}*"` into the search field and press **Enter** |  |
| 11 | Click on a pipeline |  |
| 12 | Expand the **Instance** element | For all found pipelines, the **ip** field value contains, as a substring, the value specified at step 10 |
| 13 | Return to the search page |  |
| 14 | Enter `node.name != "ip-{part of '-'-delimited ip address}*"` into the search field and press **Enter** |  |
| 15 | Click on a pipeline |  |
| 16 | Expand the **Instance** element | For all found pipelines, the **ip** field value does not contain, as a substring, the value specified at step 14 |
| 17 | Return to the search page |  |
