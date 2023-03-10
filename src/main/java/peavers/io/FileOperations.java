package peavers.io;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

@Slf4j
@UtilityClass
public class FileOperations {

    private static final RetryConfig retryConfig = RetryConfig.custom().maxAttempts(30).build();

    private static final RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

    private static final Retry retry = retryRegistry.retry("file-io-retry");

    /**
     * Moves the specified source file to the specified destination file while ensuring that only one process can modify
     * the file at a time.
     * <p>
     * This method uses a file locking mechanism to ensure that only one process can modify the source file at a time.
     * It then moves the source file to the destination file using Apache Commons IO FileUtils.moveFile method.
     * <p>
     * This method is useful when you need to move a file in a multi-process environment, where multiple processes might
     * try to modify the same file at the same time. By using file locking, this method guarantees that only one process
     * can move the file at a time, thus preventing conflicts and inconsistencies.
     * <p>
     * Note that this method relies on the executeWithLock method to acquire a lock on the source file. If you don't
     * need to ensure exclusive access to the file, you can use the Apache Commons IO FileUtils.moveFile method directly
     * instead.
     *
     * @param sourceFile      the source file to move
     * @param destinationFile the destination file to move the source file to
     * @return true if the file was successfully moved, false otherwise
     */
    public static Boolean move(final File sourceFile, final File destinationFile) {

        final Consumer<FileChannel> consumer = (final FileChannel sourceChannel) -> {
            try {
                FileUtils.moveFile(sourceFile, destinationFile);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        };

        return executeWithLock(sourceFile.toPath(), consumer);
    }

    /**
     * Deletes the specified file while ensuring that only one process can modify the file at a time.
     * <p>
     * This method uses a file locking mechanism to ensure that only one process can modify the file at a time. It then
     * deletes the file using Apache Commons IO FileUtils.deleteQuietly method.
     * <p>
     * This method is useful when you need to delete a file in a multi-process environment, where multiple processes
     * might try to modify the same file at the same time. By using file locking, this method guarantees that only one
     * process can delete the file at a time, thus preventing conflicts and inconsistencies.
     * <p>
     * Note that this method relies on the executeWithLock method to acquire a lock on the file. If you don't need to
     * ensure exclusive access to the file, you can use the Apache Commons IO FileUtils.deleteQuietly method directly
     * instead.
     *
     * @param sourceFile the file to delete
     * @return true if the file was successfully deleted, false otherwise
     */
    public static Boolean delete(final File sourceFile) {

        final Consumer<FileChannel> consumer = (final FileChannel sourceChannel) -> FileUtils.deleteQuietly(sourceFile);

        return executeWithLock(sourceFile.toPath(), consumer);
    }

    static Boolean executeWithLock(final Path toLock, final Consumer<FileChannel> operation) {

        final Callable<Boolean> callable = () -> {
            try (final FileChannel sourceChannel = FileChannel.open(toLock, StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {
                final FileLock lock = sourceChannel.tryLock();

                if (lock == null) {
                    log.warn("{} is already locked", toLock.getFileName());
                } else {
                    operation.accept(sourceChannel);
                    lock.release();

                    return true; // Success
                }
            } catch (final IOException e) {
                log.warn("{} cannot be locked", toLock.getFileName());
            }

            return false; // Failure
        };

        try {
            return retry.executeCallable(callable);
        } catch (final Exception e) {
            log.error("Exception executing callable: {}", e.getMessage(), e);
            return false;
        }
    }

}

