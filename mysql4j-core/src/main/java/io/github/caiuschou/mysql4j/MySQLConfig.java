package io.github.caiuschou.mysql4j;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MySQLConfig {

    public static final String DEFAULT_VERSION = "8.0.25";

    private final String version;
    private final int port;
    private final File dataDir;
    private final File cacheDir;
    private final String databaseName;
    private final List<String> extraArgs;
    private final long startTimeoutMs;

    private MySQLConfig(Builder b) {
        this.version = b.version;
        this.port = b.port;
        this.dataDir = b.dataDir;
        this.cacheDir = b.cacheDir;
        this.databaseName = b.databaseName;
        this.extraArgs = Collections.unmodifiableList(new ArrayList<>(b.extraArgs));
        this.startTimeoutMs = b.startTimeoutMs;
    }

    public String version() {
        return version;
    }

    /** 0 means pick a free port. */
    public int port() {
        return port;
    }

    /** null means a fresh temp directory that is deleted on close. */
    public File dataDir() {
        return dataDir;
    }

    /** Where the binaries are unpacked once and reused across runs. */
    public File cacheDir() {
        return cacheDir;
    }

    /** Created at startup if not null. */
    public String databaseName() {
        return databaseName;
    }

    /** Extra mysqld options, passed to both --initialize and the server start, e.g. --lower-case-table-names=1. */
    public List<String> extraArgs() {
        return extraArgs;
    }

    public long startTimeoutMs() {
        return startTimeoutMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MySQLConfig defaults() {
        return builder().build();
    }

    public static final class Builder {
        private String version = DEFAULT_VERSION;
        private int port = 0;
        private File dataDir;
        private File cacheDir = new File(System.getProperty("user.home"), ".mysql4j");
        private String databaseName = "test";
        private final List<String> extraArgs = new ArrayList<>();
        private long startTimeoutMs = 120_000;

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder dataDir(File dataDir) {
            this.dataDir = dataDir;
            return this;
        }

        public Builder cacheDir(File cacheDir) {
            this.cacheDir = cacheDir;
            return this;
        }

        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder extraArg(String arg) {
            this.extraArgs.add(arg);
            return this;
        }

        public Builder startTimeoutMs(long startTimeoutMs) {
            this.startTimeoutMs = startTimeoutMs;
            return this;
        }

        public MySQLConfig build() {
            return new MySQLConfig(this);
        }
    }
}
