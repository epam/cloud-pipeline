# Cloud Pipeline v.0.25 - Release notes

- [Migration to Java 21](#migration-to-java-21)

***

- [Notable Bug fixes](#notable-bug-fixes)

***

## Migration to Java 21

<<<<<<< HEAD
In the current version, main platform's Java-based services (`api`, `elasticsearch-agent`, `vm-monitor`, `monitoring-service`, `billing-report-agent`, `notifier`, `docker-comp-scan` and `jwt-generator`) moved from Java **8** to Java **21**.

The root build moved from Gradle **4.10** to Gradle **8.7**, and the Docker images of the affected services now bundle a Java **21** runtime.

Additionally, Spring Boot moved from **1.5**/**2** to Spring Boot **3.3**.
=======
In the current version, main platform's Java-based services (`api`, `elasticsearch-agent`, `vm-monitor`, `monitoring-service`, `billing-report-agent`, `notifier`, `docker-comp-scan` and `jwt-generator`) move from Java **8** to Java **21**.

The root build moves from Gradle **4.10** to Gradle **8.7**, and the Docker images of the affected services now bundle a Java **21** runtime.

Additionally, Spring Boot is moved from **1.5**/**2** to Spring Boot **3.3**.
>>>>>>> d6fd9fd2152e68f73a34aa4366346159b802933d

***

## Notable Bug fixes
