package org.pms.silverocean.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PMSUtilsFileSafetyTest {
    @TempDir Path temporaryDirectory;

    @Test
    void browserFilenameCannotEscapeTheUploadDirectory() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "../../outside.txt", "text/plain", "safe test".getBytes());

        Path stored = Path.of(PMSUtils.saveFile(temporaryDirectory.toString(), upload));

        assertThat(stored.getParent()).isEqualTo(temporaryDirectory.toAbsolutePath().normalize());
        assertThat(stored.getFileName().toString()).isEqualTo("outside.txt");
        assertThat(Files.readString(stored)).isEqualTo("safe test");
    }

    @Test
    void cleanupDoesNotClimbAndDeleteParentDirectories() throws Exception {
        Path parent = Files.createDirectories(temporaryDirectory.resolve("one/two/three"));
        Path stored = Files.writeString(parent.resolve("upload.txt"), "content");

        PMSUtils.deleteFileAndParents(stored.toString());

        assertThat(stored).doesNotExist();
        assertThat(parent).isDirectory();
    }
}
