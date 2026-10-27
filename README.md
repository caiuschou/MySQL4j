# MySQL4j

Embedded **MySQL 8.0.25** for Java tests, in the style of [MariaDB4j](https://github.com/MariaDB4j/MariaDB4j).
It unpacks a real `mysqld` from a jar, initializes a temporary data directory, starts the server on a free
port bound to `127.0.0.1` (root, empty password), and stops and cleans up afterwards. No Docker needed.

Supported platforms: **Windows x64** and **Linux x86_64** (glibc 2.17+). Java 8+.

## Modules

| Module | Purpose |
|---|---|
| `mysql4j-core` | `MySQL`, `MySQLConfig`: start/stop an embedded server, no test-framework dependency |
| `mysql4j-junit5` | `@EmbeddedMySQL` annotation and `MySQLExtension` for JUnit 5 |
| `mysql4j-spring-boot` | `@AutoConfigureEmbeddedMySQL` for Spring Boot 2.x tests |
| `mysql4j-db-winx64` | Trimmed MySQL 8.0.25 binaries for Windows (about 19 MB) |
| `mysql4j-db-linux-x86_64` | Trimmed MySQL 8.0.25 binaries for Linux (about 21 MB) |

## Usage

Add the integration you need, the binaries for your OS, and a MySQL driver, all with `test` scope:

```xml
<dependency>
  <groupId>io.mysql4j</groupId>
  <artifactId>mysql4j-junit5</artifactId>   <!-- or mysql4j-spring-boot / mysql4j-core -->
  <version>1.0.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>io.mysql4j</groupId>
  <artifactId>mysql4j-db-winx64</artifactId>   <!-- and/or mysql4j-db-linux-x86_64 -->
  <version>1.0.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>mysql</groupId>
  <artifactId>mysql-connector-java</artifactId>
  <version>8.0.25</version>
  <scope>test</scope>
</dependency>
```

If developers use both Windows and Linux, declare both binary artifacts, or select one with an OS profile as
this project's parent pom does.

### Plain Java

```java
try (MySQL db = MySQL.start()) {
    Connection c = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password());
    // db.port(), db.jdbcUrl("otherDb"), db.lastOutput()
}
```

```java
MySQL db = MySQL.start(MySQLConfig.builder()
        .port(3307)
        .databaseName("shop")                       // created at startup, null for none (default "test")
        .extraArg("--lower-case-table-names=1")     // applied to both initialize and start
        .build());
```

### JUnit 5

```java
@EmbeddedMySQL(database = "shop", extraArgs = "--lower-case-table-names=1")
class OrderDaoTest {
    @Test
    void test(MySQL db) { /* db.jdbcUrl() ... */ }
}
```

- One server per test class, stopped when the class ends. `shared = true` reuses one server per JVM for all
  classes asking for the same settings.
- System properties `mysql4j.jdbc-url`, `mysql4j.username`, `mysql4j.password`, `mysql4j.port` are set after startup.
- To configure in code instead:

```java
@RegisterExtension
static MySQLExtension mysql = new MySQLExtension(MySQLConfig.builder().databaseName("orders").build());
```

### Spring Boot 2.x

```java
@AutoConfigureEmbeddedMySQL(database = "app")
@SpringBootTest
class MyIT {
    @Autowired JdbcTemplate jdbc;
}
```

Before the context is created, `spring.datasource.url/username/password` are pointed at the embedded server
(overriding `application.properties`). One server is started per JVM for each distinct setting and stopped at
JVM exit, so several test classes and cached contexts share it.

## How it works

1. The `mysql4j-db-*` jar contains one zip with only `mysqld`, the DLLs it needs on Windows, and `share/`.
   It is built by downloading the official MySQL archive from `cdn.mysql.com` (cached in `tmpDir/`).
2. On first use the zip is unpacked once into `~/.mysql4j/<version>-<platform>/` and reused afterwards.
3. Each instance gets a fresh temp directory: `mysqld --initialize-insecure`, then `mysqld` with
   `--bind-address=127.0.0.1 --mysqlx=OFF --skip-name-resolve --default-authentication-plugin=mysql_native_password
   --character-set-server=utf8mb4 --skip-log-bin --performance-schema=OFF`.
4. An `--init-file` creates `root@'%'` (needed because `localhost` does not match `127.0.0.1` with
   `--skip-name-resolve`) and the configured database.
5. Ready is detected from the log line `ready for connections ... port: N`.
6. On close, `SHUTDOWN` is sent over JDBC if a MySQL driver is on the classpath; otherwise, or if that fails,
   the process is killed. The temp directory is then deleted.

## Build

```bash
mvn install
```

The first build downloads about 260 MB of MySQL archives into `tmpDir/`. Tests of each module run against the
binaries for the current OS (selected by the `db-windows` / `db-linux` profiles in the parent pom).

## Known issues

- **Windows temp leftovers**: after each run, InnoDB's renamed `*.d` files (for example
  `undo_2_trunc.log.XXXX.d`) stay delete-pending in `%TEMP%\mysql4j-*\data`. Deletion is retried and then
  ignored, so tests do not fail, but the directories remain until the files can be removed.
- **Linux is untested**: the package contents are verified, but the Linux path has not been run yet.
  `bin/mysqld` is made executable at runtime because the zip does not keep permissions.
- Closing a server that cannot be reached over JDBC waits up to 30 seconds before killing it.
- macOS and ARM are not supported.
- MySQL is GPLv2. Check your company's rules before publishing jars that bundle the binaries.
