# Click SSH link

Test verifies that clicking the SSH link opens a terminal tab connected to the run.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-583](EPMCMBIBPC-583.md) case |  |
| 2 | Click the SSH link in the right upper corner of the page | <li> a new tab opens with a terminal <li> that terminal contains a line with the text `pipeline-{id}` |
