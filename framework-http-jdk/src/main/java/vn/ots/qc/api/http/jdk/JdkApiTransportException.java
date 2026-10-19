package vn.ots.qc.api.http.jdk;

/** Unchecked failure raised when the JDK HTTP transport cannot complete a request. */
public final class JdkApiTransportException extends RuntimeException {
  public JdkApiTransportException(String message, Throwable cause) {
    super(message, cause);
  }
}
