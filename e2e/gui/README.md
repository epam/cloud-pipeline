# Requirements

* **Linux OS (Debian 9/Ubuntu 16/EL7)**
* **Docker**

# Running tests

* Prepare a test configuration.

  Fill in all the required fields in the configuration file: `/path_to_e2e/e2e/gui/default.conf`
    
* Build the docker image:

```bash
docker build --build-arg RECORDING=<true/false> -t <image_name> .
```
By default `RECORDING=false`

* Launch the docker container with tests:
```bash
# navigate to the source code folder
cd /path_to_e2e/e2e/gui
export USER_HOME_DIR="/headless"
docker run  -it \
            --rm \
            -v ~/path_to_e2e/e2e/gui:/headless/e2e/gui \
            --user 0:0 \
            -p 5901:5901 \
            -p 6901:6901 \
            -v /dev/shm:/dev/shm \
            <image_name>
```
In the running container `/tmp/run.sh` script is run by default. This script launches `/gradlew clean test` and screen recording if `RECORDING=true`.
