package io.mysql4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Unpacks the mysql4j-db-* zip from the classpath into a per-version cache directory, once. */
final class Binaries {

    private Binaries() {
    }

    static File prepare(MySQLConfig config, Platform platform) {
        File baseDir = new File(config.cacheDir(), config.version() + "-" + platform.id());
        File marker = new File(baseDir, ".extracted");
        if (marker.exists()) {
            return baseDir;
        }
        String resource = platform.resourcePath(config.version());
        // Several JVMs (parallel surefire forks) may race here; extract aside, then move into place.
        synchronized (Binaries.class) {
            if (marker.exists()) {
                return baseDir;
            }
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = Binaries.class.getClassLoader();
            }
            try (InputStream in = cl.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new MySQL4jException("MySQL binaries not on classpath: " + resource
                            + " (add the mysql4j-db-" + platform.id() + " dependency)");
                }
                File tmp = new File(config.cacheDir(), baseDir.getName() + ".tmp-" + System.nanoTime());
                unzip(in, tmp);
                new File(tmp, ".extracted").createNewFile();
                if (!tmp.renameTo(baseDir)) {
                    // Another process won the race.
                    FileUtils.deleteRecursively(tmp);
                    if (!marker.exists()) {
                        throw new MySQL4jException("Could not move " + tmp + " to " + baseDir);
                    }
                }
            } catch (IOException e) {
                throw new MySQL4jException("Failed to unpack " + resource, e);
            }
        }
        return baseDir;
    }

    private static void unzip(InputStream in, File target) throws IOException {
        target.mkdirs();
        String root = target.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File out = new File(target, entry.getName());
                if (!out.getCanonicalPath().startsWith(root)) {
                    throw new IOException("Zip entry escapes target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    out.getParentFile().mkdirs();
                    Files.copy(zip, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
