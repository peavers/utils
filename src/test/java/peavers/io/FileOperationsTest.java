package peavers.io;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static peavers.io.FileOperations.executeWithLock;
import static peavers.io.FileOperations.move;

class FileOperationsTest {

    @TempDir
    static Path tempDirectory;

    @Test
    @DisplayName("Lock is acquired when file is not already locked")
    void testExecuteWithLockAcquiresLock() throws Exception {
        // Given
        final Path path = createTempFile();
        final Consumer<FileChannel> operation = mock(Consumer.class);

        // When
        final Boolean result = executeWithLock(path, operation);

        // Then
        assertTrue(result);
        verify(operation).accept(any(FileChannel.class));
    }

    @Test
    @DisplayName("Lock is not acquired when file is already locked")
    void testExecuteWithLockDoesNotAcquireLock() throws Exception {
        // Given
        final Path path = createTempFile();
        final Path lockFile = tempDirectory.resolve("lock.lock");

        // Simulate locking the file by creating a lock file
        Files.createFile(lockFile);

        // Create an operation that should not be executed since the file is already locked
        final Consumer<FileChannel> operation = mock(Consumer.class);

        // When
        final Boolean result = executeWithLock(path, operation);

        // Then
        assertFalse(result);
        verify(operation, never()).accept(any(FileChannel.class));

        // Clean up
        Files.delete(lockFile);
    }


    @Test
    @DisplayName("File is moved successfully")
    void testMove() throws Exception {
        // Given
        final Path source = createTempFile();
        final Path destinationPath = tempDirectory.resolve("destination.txt");
        final File destination = destinationPath.toFile();

        // When
        final Boolean result = move(source.toFile(), destination);

        // Then
        assertTrue(result);
        assertFalse(source.toFile().exists());
        assertTrue(destination.exists());

        // Clean up
        Files.delete(destinationPath);
    }

    @Test
    @DisplayName("RuntimeException is caught and returns false")
    void testMoveWithLockRuntimeException() throws Exception {
        // Given
        final Path source = createTempFile();
        final Path destinationPath = tempDirectory.resolve("destination.txt");
        final File destination = destinationPath.toFile();

        // Simulate a RuntimeException by passing a null operation
        final Consumer<FileChannel> operation = null;

        // When
        final Boolean result = executeWithLock(source, operation);

        // Then
        assertFalse(result);
        assertTrue(source.toFile().exists());
        assertFalse(destination.exists());
    }

    private Path createTempFile() throws IOException {

        final File file = File.createTempFile("test", ".lock", tempDirectory.toFile());
        return file.toPath();
    }

}
