package io.refrax.shared;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileReaderHelper {

    public static InputStream openStream(String location) throws IOException {
        Path path = Paths.get(location);

        if (Files.exists(path)) {
            return Files.newInputStream(path);
        }

        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(location);
        if (in != null) {
            return in;
        }

        throw new IOException("Resource neither found on filesystem (" + path.toAbsolutePath() + ") nor on classpath: " + location);
    }
}
