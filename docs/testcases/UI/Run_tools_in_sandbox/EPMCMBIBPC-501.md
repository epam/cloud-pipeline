# Stop tool in sandbox

Test verifies stopping a running tool and that its endpoint then returns a 404.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-495](EPMCMBIBPC-495.md) case |  |
| 2 | Open the run log page of the tool launched at step 1 |  |
| 3 | Click the **STOP** hyperlink in the right upper corner of the page |  |
| 4 | Click **STOP** button in the appeared window | <li> the pipeline finishes with **STOPPED** status <li> clicking the hyperlink in the header opposite the **Endpoint** label returns a page with a `404 Not Found` error |
