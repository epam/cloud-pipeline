# Validation of friendly URL [NEGATIVE]

Test verifies that launching another run with an already-used Friendly URL is rejected.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case, don't stop the launched run |  |
| 2 | Repeat steps 1-9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case | An error message appears with the text: `Url '<inputted_friendly_URL>' is already used for run '<RunID>'.` |
