package vn.ots.qc.api.core.http;

import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

public class MultipartPartTest {
  @Test
  public void protectsBinaryContentAndFilename() throws Exception {
    byte[] source = {1, 2, 3};
    MultipartPart part = MultipartPart.bytes("document", "report.pdf", source, "application/pdf");
    source[0] = 9;

    Assert.assertEquals(part.bytes(), new byte[] {1, 2, 3});
    Assert.assertFalse(part.traceMetadata().contains("report.pdf"));
    Assert.expectThrows(
        IllegalArgumentException.class,
        () -> MultipartPart.bytes("document", "../secret.pdf", source, "application/pdf"));

    Path file = Files.createTempFile("multipart-contract", ".txt");
    try {
      Assert.assertEquals(
          MultipartPart.file("file", file, "text/plain").kind(), MultipartPart.Kind.FILE);
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  public void rejectsAmbiguousBodyAndMultipart() {
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            ApiRequest.post("files", "/upload")
                .body("payload")
                .multipart(MultipartPart.text("description", "safe"))
                .build());
  }
}
