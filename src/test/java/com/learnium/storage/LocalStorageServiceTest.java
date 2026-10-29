package com.learnium.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final UUID MATERIAL = UUID.randomUUID();

    @TempDir private Path tempDir;

    private LocalStorageService service() {
        StorageProperties properties = new StorageProperties();
        properties.setLocalDir(tempDir.toString());
        return new LocalStorageService(properties);
    }

    @Test
    void storeReadDeleteRoundTrip() {
        LocalStorageService service = service();
        byte[] content = "chapter bytes".getBytes(StandardCharsets.UTF_8);

        String key = service.store(CENTRE, MATERIAL, "Chapter 4.pdf", content);

        assertThat(key).isEqualTo(CENTRE + "/" + MATERIAL + "/Chapter_4.pdf");
        assertThat(Files.exists(tempDir.resolve(key))).isTrue();
        assertThat(service.read(key)).isEqualTo(content);

        service.delete(key);
        assertThat(Files.exists(tempDir.resolve(key))).isFalse();
        // Deleting a missing key is a no-op.
        assertThatNoException().isThrownBy(() -> service.delete(key));
    }

    @Test
    void readOfMissingKeyFails() {
        assertThatExceptionOfType(StorageException.class)
                .isThrownBy(() -> service().read(CENTRE + "/" + MATERIAL + "/nope.pdf"));
    }

    @Test
    void keysCannotEscapeTheStorageRoot() {
        LocalStorageService service = service();
        assertThatExceptionOfType(StorageException.class)
                .isThrownBy(() -> service.read("../../outside.txt"));
        assertThatExceptionOfType(StorageException.class)
                .isThrownBy(() -> service.delete("../../outside.txt"));
    }

    @Test
    void storeFailsWhenTheRootIsNotWritable() throws IOException {
        Path fileRoot = tempDir.resolve("not-a-dir");
        Files.writeString(fileRoot, "occupied");
        StorageProperties properties = new StorageProperties();
        properties.setLocalDir(fileRoot.toString());
        LocalStorageService service = new LocalStorageService(properties);

        assertThatExceptionOfType(StorageException.class)
                .isThrownBy(() -> service.store(CENTRE, MATERIAL, "a.pdf", new byte[] {1}));
    }

    @Test
    void sanitizeKeepsOnlyASafeBasename() {
        assertThat(LocalStorageService.sanitize("Chapter 4: Fractions.pdf"))
                .isEqualTo("Chapter_4__Fractions.pdf");
        assertThat(LocalStorageService.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(LocalStorageService.sanitize("..\\windows\\evil.pdf")).isEqualTo("evil.pdf");
        assertThat(LocalStorageService.sanitize("..")).isEqualTo("file");
        assertThat(LocalStorageService.sanitize("")).isEqualTo("file");
        assertThat(LocalStorageService.sanitize(null)).isEqualTo("file");
    }
}
