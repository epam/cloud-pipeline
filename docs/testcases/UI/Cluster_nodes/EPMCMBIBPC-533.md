# [MANUAL] Filtering nodes by full label name

Test verifies that filtering the cluster nodes table by a full pipeline run ID label shows only the matching node.

**Prerequisites**:
- Two or more working nodes (including the master), started at different times

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-267](EPMCMBIBPC-267.md) case |  |
| 2 | Click the icon near the **Labels** column header |  |
| 3 | In the appeared pop-up, enter the full numeric Run ID `{valid_id}` (not merely a part of it), for a node that hasn't been terminated or reused yet. Entering it as text, e.g. in the format `run id {valid_id}`, fails validation with the message `Please enter valid integer` and the filter isn't applied |  |
| 4 | Click **OK** | Only the node that has a label with the ID entered at step 3 is displayed |
