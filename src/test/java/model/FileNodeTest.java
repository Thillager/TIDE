package model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileNodeTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsWrappedFile() throws Exception {
        Path file = Files.createFile(tempDir.resolve("Example.java"));
        FileNode node = new FileNode(file.toFile());

        assertEquals(file.toFile(), node.getFile());
    }

    @Test
    void toStringReturnsFileName() throws Exception {
        Path file = Files.createFile(tempDir.resolve("Example.java"));
        FileNode node = new FileNode(file.toFile());

        assertEquals("Example.java", node.toString());
    }
}
