package io.github.caiuschou.mysql4j;

import java.util.Locale;

/** Supported OS/arch combinations; each one has a matching mysql4j-db-* artifact. */
public enum Platform {
    WINX64("winx64", "mysqld.exe"),
    LINUX_X86_64("linux-x86_64", "mysqld");

    private final String id;
    private final String serverExecutable;

    Platform(String id, String serverExecutable) {
        this.id = id;
        this.serverExecutable = serverExecutable;
    }

    public String id() {
        return id;
    }

    public String serverExecutable() {
        return serverExecutable;
    }

    public boolean isWindows() {
        return this == WINX64;
    }

    /** Classpath location of the trimmed distribution zip, e.g. io/github/caiuschou/mysql4j/db/mysql-8.0.25-winx64.zip */
    public String resourcePath(String mysqlVersion) {
        return "io/github/caiuschou/mysql4j/db/mysql-" + mysqlVersion + "-" + id + ".zip";
    }

    public static Platform detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        if (x64 && os.contains("win")) {
            return WINX64;
        }
        if (x64 && os.contains("linux")) {
            return LINUX_X86_64;
        }
        throw new MySQL4jException("Unsupported platform: os.name=" + os + ", os.arch=" + arch);
    }
}
