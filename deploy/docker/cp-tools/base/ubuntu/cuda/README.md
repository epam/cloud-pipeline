# What is Ubuntu?

Ubuntu is a Debian-based Linux operating system. It is based on free software and named after the Southern African philosophy of ubuntu (literally, "human-ness"), which often is translated as "humanity towards others" or "the belief in a universal bond of sharing that connects all humanity".

Development of Ubuntu is led by UK-based Canonical Ltd., a company owned by South African entrepreneur Mark Shuttleworth. Canonical generates revenue through the sale of technical support and other services related to Ubuntu. The Ubuntu project is publicly committed to the principles of open-source software development; people are encouraged to use free software, study how it works, improve upon it, and distribute it.

[wikipedia.org/wiki/Ubuntu_(operating_system)](https://en.wikipedia.org/wiki/Ubuntu_%28operating_system%29)

# How to use this image

This image can be run on its own with SSH access or can be used as a base image for custom environments.

To run this tool perform the following steps:
1. Click `Run` button in the top-right corner and agree on a tool launch
2. Wait for the container to be initialized (`InitializeEnvironment` task shall be marked as finished)
3. Click `SSH` link to navigate to the terminal session

In addition to the base `ubuntu:16.04` a number of packages are installed:
* `python`
* `wget`
* `curl`
* `git`
* `java`
