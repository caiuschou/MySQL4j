package io.mysql4j;

import java.io.File;

final class FileUtils {

    private FileUtils() {
    }

    /**
     * Best effort: on Windows, InnoDB's renamed "*.d" files can stay delete-pending for a while after mysqld
     * exits, so retry briefly and then give up quietly rather than fail the test.
     */
    static void deleteRecursively(File f) {
        for (int attempt = 0; attempt < 10 && f.exists(); attempt++) {
            deleteOnce(f);
            if (f.exists()) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void deleteOnce(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteOnce(c);
            }
        }
        f.delete();
    }
}
