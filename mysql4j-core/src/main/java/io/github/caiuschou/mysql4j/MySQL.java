package io.github.caiuschou.mysql4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * An embedded MySQL server: unpacks the bundled binaries, initializes a data directory and runs mysqld
 * bound to 127.0.0.1 with an empty root password.
 */
public final class MySQL implements AutoCloseable {

    private static final int OUTPUT_LINES_KEPT = 200;

    private final MySQLConfig config;
    private final Platform platform;
    private final File baseDir;
    private final File instanceDir;
    private final File dataDir;
    private final boolean deleteInstanceOnClose;
    private final int port;
    private final LinkedList<String> lastOutput = new LinkedList<>();
    private Process process;
    private volatile Thread lastPump;

    private MySQL(MySQLConfig config) {
        this.config = config;
        this.platform = Platform.detect();
        this.baseDir = Binaries.prepare(config, platform);
        try {
            if (config.dataDir() == null) {
                this.instanceDir = Files.createTempDirectory("mysql4j-").toFile();
                this.dataDir = new File(instanceDir, "data");
                this.deleteInstanceOnClose = true;
            } else {
                this.instanceDir = config.dataDir().getAbsoluteFile().getParentFile();
                this.dataDir = config.dataDir().getAbsoluteFile();
                this.deleteInstanceOnClose = false;
            }
            this.port = config.port() > 0 ? config.port() : freePort();
        } catch (IOException e) {
            throw new MySQL4jException("Failed to prepare instance", e);
        }
    }

    public static MySQL start() {
        return start(MySQLConfig.defaults());
    }

    public static MySQL start(MySQLConfig config) {
        MySQL db = new MySQL(config);
        try {
            db.initializeIfNeeded();
            db.startServer();
        } catch (RuntimeException e) {
            db.close();
            throw e;
        }
        return db;
    }

    public int port() {
        return port;
    }

    public String jdbcUrl() {
        return jdbcUrl(config.databaseName() == null ? "" : config.databaseName());
    }

    public String jdbcUrl(String database) {
        return "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
    }

    public String username() {
        return "root";
    }

    public String password() {
        return "";
    }

    /** Last console lines of mysqld, for diagnostics. */
    public synchronized List<String> lastOutput() {
        return new ArrayList<>(lastOutput);
    }

    private void initializeIfNeeded() {
        if (new File(dataDir, "mysql").isDirectory()) {
            return;
        }
        List<String> cmd = baseCommand();
        cmd.add("--initialize-insecure");
        cmd.add("--console");
        Process init = launch(cmd, null);
        try {
            if (!init.waitFor(config.startTimeoutMs(), TimeUnit.MILLISECONDS)) {
                init.destroyForcibly();
                throw new MySQL4jException("mysqld --initialize timed out\n" + tail());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            init.destroyForcibly();
            throw new MySQL4jException("Interrupted during initialize", e);
        }
        if (init.exitValue() != 0) {
            throw new MySQL4jException("mysqld --initialize failed with exit " + init.exitValue() + "\n" + tail());
        }
    }

    private void startServer() {
        List<String> cmd = baseCommand();
        cmd.addAll(Arrays.asList(
                "--console",
                "--port=" + port,
                "--bind-address=127.0.0.1",
                "--mysqlx=OFF",
                "--skip-name-resolve",
                "--default-authentication-plugin=mysql_native_password",
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_general_ci",
                // undo truncation leaves an undeletable undo_N_trunc.log.*.d file in the data dir on Windows
                "--innodb-undo-log-truncate=OFF",
                "--skip-log-bin",
                "--performance-schema=OFF"));
        if (!platform.isWindows()) {
            cmd.add("--socket=" + new File(instanceDir, "mysql.sock").getAbsolutePath());
            cmd.add("--pid-file=" + new File(instanceDir, "mysqld.pid").getAbsolutePath());
        }
        // With --skip-name-resolve the initial root@localhost does not match 127.0.0.1, so add root@% too.
        StringBuilder sql = new StringBuilder()
                .append("CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED WITH mysql_native_password BY '';\n")
                .append("GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;\n");
        if (config.databaseName() != null) {
            sql.append("CREATE DATABASE IF NOT EXISTS `").append(config.databaseName())
                    .append("` CHARACTER SET utf8mb4;\n");
        }
        File initFile = new File(instanceDir, "init-" + port + ".sql");
        try {
            Files.write(initFile.toPath(), sql.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MySQL4jException("Failed to write init file", e);
        }
        cmd.add("--init-file=" + initFile.getAbsolutePath());

        CountDownLatch ready = new CountDownLatch(1);
        process = launch(cmd, line -> {
            if (line.contains("ready for connections") && line.contains("port: " + port)) {
                ready.countDown();
            }
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.startTimeoutMs());
            while (!ready.await(200, TimeUnit.MILLISECONDS)) {
                if (!process.isAlive()) {
                    throw new MySQL4jException("mysqld exited with " + process.exitValue() + " before ready\n" + tail());
                }
                if (System.nanoTime() > deadline) {
                    throw new MySQL4jException("mysqld not ready after " + config.startTimeoutMs() + " ms\n" + tail());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MySQL4jException("Interrupted while waiting for mysqld", e);
        }
    }

    private List<String> baseCommand() {
        List<String> cmd = new ArrayList<>();
        File exe = new File(new File(baseDir, "bin"), platform.serverExecutable());
        if (!exe.canExecute()) {
            exe.setExecutable(true);
        }
        cmd.add(exe.getAbsolutePath());
        cmd.add("--no-defaults"); // must be the first option
        cmd.add("--basedir=" + baseDir.getAbsolutePath());
        cmd.add("--datadir=" + dataDir.getAbsolutePath());
        cmd.addAll(config.extraArgs());
        return cmd;
    }

    private Process launch(List<String> cmd, Consumer<String> lineListener) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            Thread pump = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        record(line);
                        if (lineListener != null) {
                            lineListener.accept(line);
                        }
                    }
                } catch (IOException ignored) {
                    // process ended
                }
            }, "mysql4j-output");
            pump.setDaemon(true);
            pump.start();
            lastPump = pump;
            return p;
        } catch (IOException e) {
            throw new MySQL4jException("Failed to launch " + cmd.get(0), e);
        }
    }

    private synchronized void record(String line) {
        lastOutput.add(line);
        if (lastOutput.size() > OUTPUT_LINES_KEPT) {
            lastOutput.removeFirst();
        }
    }

    private String tail() {
        // Let the reader thread finish consuming what a just-exited process wrote.
        Thread pump = lastPump;
        if (pump != null) {
            try {
                pump.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (this) {
            return String.join("\n", lastOutput);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Graceful SHUTDOWN over JDBC when a MySQL driver is present; otherwise (or on failure) kill the process. */
    @Override
    public void close() {
        Process p = process;
        if (p != null && p.isAlive()) {
            try (Connection c = DriverManager.getConnection(jdbcUrl(""), username(), password());
                    Statement st = c.createStatement()) {
                st.execute("SHUTDOWN");
            } catch (SQLException ignored) {
                // no driver, or already going down
            }
            try {
                if (!p.waitFor(30, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    p.waitFor(10, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }
        }
        if (deleteInstanceOnClose) {
            FileUtils.deleteRecursively(instanceDir);
        }
    }
}
