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

# Logs

The notificator writes its log to `logs/notifier.log` in its working directory, and prints the same lines to the console.
A new file is started every day, and the last 20 are kept (`src/main/resources/log4j2-spring.xml`).
In a deployment the working directory is `/opt/notifier`, and `logs/` is mounted from `/opt/notifier/logs` on the
host (`deploy/contents/k8s/cp-notifier/cp-notifier-dpl.yaml`). The console output is in `logs/runtime.log` and in
`kubectl logs deploy/cp-notifier`. Unlike `notifier.log`, `runtime.log` is not rotated: it keeps every line since the
container start, and is emptied only when the container starts again (`deploy/docker/cp-notifier/init`).

For a message it takes from the queue, the notificator logs:
* `Email from message <id> with subject '<subject>' formed and will be send to: [<to>], cc: [<cc>], bcc: [<bcc>]` -
the email is built, before each attempt to send it. The message's recipient is in `to`, and its copy recipients are
in `bcc`: the notificator sends copies as BCC, so `cc` is empty. Only users with a valid email address are listed.
A user with no `email` attribute, or with an invalid one, and a copy recipient that does not exist are left out
with no log line. A recipient (`toUserId`) that does not exist only gets the
`Cannot find user with id <user id> for message <id>` line.
* `Message with id: <id> was successfully send` - the email server has accepted the email. Only this line shows that
the email was sent. It is also logged for a message with no recipients, after
`Email with message <id> won't be sent: no recipients found`.
* `Fail to send message with id <id>. Attempt <n>/<count>.` - an attempt failed. There are
`email.notification.retry.count` attempts, and each attempt that gets as far as building the email logs the
`formed` line again. An invalid `email.from`, or an address that the mail library rejects, fails an attempt before
the email is built, so only the `Fail to send` lines are logged.
* `All attempts are failed. Message with id: <id> will not be sent.` - the message is dropped. It has already been
removed from the queue, so it is not sent later.
* `Exception while trying to send email`, at WARN, without the message id - another error, e.g. in a template. The
message is not tried again, and is dropped. The same line is logged when the in-app notification of the message
(`notification.enable.ui`) cannot be built or saved.
