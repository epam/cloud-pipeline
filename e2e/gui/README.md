# Requirements

* **[Oracle JDK 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html)** or **[Open JDK 8](http://openjdk.java.net/install/)**
* **[Google Chrome](https://support.google.com/chrome/answer/95346?co=GENIE.Platform%3DDesktop&hl=en-GB)**
* **[Chrome Driver](https://chromedriver.storage.googleapis.com/index.html?path=2.31/)**

You can use the `install.sh` scripts to meet all requirements:

```
$ sh install.sh
```

# Running tests

To run autotests use Gradle task:

```
$ ./gradlew test
```
# Test extension

To extend the test cases, it's necessary the `service` and the `driver` to be created and closed at the end in each test.

```
@BeforeClass
public void preparationService() {
    service = WebDriverUtils.createAndStartChromeService();
}

@Before
public void preparationDriver() {
    driver = WebDriverUtils.createChromeWebDriverInstance(service);
}

@After
public void quitDriver() {
    WebDriverUtils.destroyWebDriverInstance(driver);
}

@AfterClass
public void createAndStopService() {
    service.stop();
}
```