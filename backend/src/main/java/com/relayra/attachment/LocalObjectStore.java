package com.relayra.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalObjectStore {

  private final Path root;

  public LocalObjectStore(@Value("${relayra.storage.local-root:./data/attachments}") String rootDir) {
    this.root = Path.of(rootDir).toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.root);
    } catch (IOException failure) {
      throw new IllegalStateException("Attachment storage root is not writable.", failure);
    }
  }

  public void put(String storageKey, InputStream content, long sizeBytes) {
    Path target = resolve(storageKey);
    Path staging = null;
    try {
      Files.createDirectories(target.getParent());
      staging = target.resolveSibling(target.getFileName() + ".part-" + UUID.randomUUID());
      try (InputStream in = content;
          OutputStream out =
              Files.newOutputStream(staging, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        in.transferTo(out);
      }
      try {
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException fallback) {
        Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
      }
      staging = null;
    } catch (IOException failure) {
      throw new IllegalStateException("Attachment content could not be stored.", failure);
    } finally {
      if (staging != null) {
        try {
          Files.deleteIfExists(staging);
        } catch (IOException ignored) {
        }
      }
    }
  }

  public InputStream open(String storageKey) {
    try {
      return Files.newInputStream(resolve(storageKey), StandardOpenOption.READ);
    } catch (IOException failure) {
      throw new IllegalStateException("Attachment content is unavailable.", failure);
    }
  }

  public void delete(String storageKey) {
    try {
      Files.deleteIfExists(resolve(storageKey));
    } catch (IOException failure) {
      throw new IllegalStateException("Attachment content could not be deleted.", failure);
    }
  }

  private Path resolve(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      throw new IllegalArgumentException("storageKey is required.");
    }
    String normalized = storageKey.replace('\\', '/');
    if (normalized.startsWith("/")
        || normalized.contains("//")
        || normalized.contains("..")
        || !normalized.matches("[A-Za-z0-9/_.-]+")) {
      throw new IllegalArgumentException("Invalid storage key.");
    }
    Path resolved = root.resolve(normalized).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Invalid storage key.");
    }
    return resolved;
  }
}
