# Check shell functionality

Test verifies running a shell command over an SSH session into a launched tool.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-584](EPMCMBIBPC-584.md) case |  |
| 2 | On the opened tab with a terminal, run: `cd /home && mkdir test && ls` | An appeared row contains the text `test` |
