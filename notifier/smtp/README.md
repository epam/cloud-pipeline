# How to build Notificator
In order to build Notificator jar file just execute simple command from the root dir of Pipeline project:
```
./gradlew build :notifier:smtp:build
```

Your jar will be available in:
```
<root-project-dir>/notifier/smtp/build/libs/smtp-<version>.jar
```

# How to run Notificator

You can run notificator with command:
```
java -jar smtp-<version>.jar &
```

It will start daemon process with java application for the current shell.

You can provide an external file **application.properties** to specify notificator configuration. 
Available properties:
* **spring.datasource.url** - path to Pipeline database
* **spring.datasource.username** - user name for connect to Pipeline database 
* **spring.datasource.password** - password for connect to Pipeline database 
* **spring.datasource.driver-class-name** - JDBC driver for Pipeline database
* **spring.datasource.maxActive** - Max number of connection to a database (default: 5)
* **spring.datasource.initialSize** - Initial size of connection pool to a database (default: 10)
* **email.notification.retry.count** - How many time notificator can try to send message
* **notification.scheduler.delay** - How often notificator will check queue for available messages (value in ms)
* **notification.enable.smtp** - Enable or disable smtp notificator (true/false)
* **submit.threads** - Size of a thread pool 
* **email.smtp.server.host.name** - Host name of an email server
* **email.smtp.port** - Port of an email server
* **email.ssl.on.connect** - Enable or disable usage of SSL on connect to an email server
* **email.start.tls.enabled** - Enable or disable TLS when send an email
* **email.from** - Email address of an author of a notification
* **email.user** - username for authorization on an email server (optional)
* **email.password** - password for authorization on an email server (optional)
