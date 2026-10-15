package vn.ots.qc.api.core.http;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Transport-neutral multipart value. Binary content is never exposed by {@link #traceMetadata()}.
 */
public final class MultipartPart {
  public enum Kind {
    TEXT,
    BYTES,
    FILE
  }

  private final String name;
  private final String filename;
  private final String contentType;
  private final Kind kind;
  private final String text;
  private final byte[] bytes;
  private final Path file;

  private MultipartPart(
      String name,
      String filename,
      String contentType,
      Kind kind,
      String text,
      byte[] bytes,
      Path file) {
    this.name = required(name, "part name");
    this.filename = filename == null ? "" : filename.trim();
    this.contentType = required(contentType, "content type");
    this.kind = kind;
    this.text = text;
    this.bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    this.file = file;
  }

  public static MultipartPart text(String name, String value) {
    return text(name, value, "text/plain; charset=UTF-8");
  }

  public static MultipartPart text(String name, String value, String contentType) {
    if (value == null) throw new IllegalArgumentException("part value is required");
    return new MultipartPart(name, "", contentType, Kind.TEXT, value, null, null);
  }

  public static MultipartPart bytes(
      String name, String filename, byte[] value, String contentType) {
    if (value == null) throw new IllegalArgumentException("part bytes are required");
    return new MultipartPart(
        name, safeFilename(filename), contentType, Kind.BYTES, null, value, null);
  }

  public static MultipartPart file(String name, Path path, String contentType) {
    if (path == null) throw new IllegalArgumentException("part file is required");
    Path normalized = path.toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalized))
      throw new IllegalArgumentException("Multipart file does not exist: " + normalized);
    return new MultipartPart(
        name,
        safeFilename(normalized.getFileName().toString()),
        contentType,
        Kind.FILE,
        null,
        null,
        normalized);
  }

  public String name() {
    return name;
  }

  public String filename() {
    return filename;
  }

  public String contentType() {
    return contentType;
  }

  public Kind kind() {
    return kind;
  }

  public String text() {
    return text;
  }

  public byte[] bytes() {
    return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
  }

  public Path file() {
    return file;
  }

  public String traceMetadata() {
    long size =
        switch (kind) {
          case TEXT -> text.length();
          case BYTES -> bytes.length;
          case FILE -> fileSize(file);
        };
    return name + "{" + kind.name().toLowerCase() + "," + contentType + "," + size + " bytes}";
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " is required");
    return value.trim();
  }

  private static String safeFilename(String value) {
    String filename = required(value, "filename");
    String safe = Path.of(filename).getFileName().toString().replaceAll("[\\r\\n\\u0000]", "_");
    if (!safe.equals(filename) || safe.equals(".") || safe.equals("..")) {
      throw new IllegalArgumentException("filename must not contain a path: " + filename);
    }
    return safe;
  }

  private static long fileSize(Path path) {
    try {
      return Files.size(path);
    } catch (java.io.IOException exception) {
      return -1;
    }
  }
}
