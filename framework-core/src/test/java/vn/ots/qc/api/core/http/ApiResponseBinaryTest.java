package vn.ots.qc.api.core.http;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public class ApiResponseBinaryTest {
    @Test
    public void preservesAndSavesBinaryBody() throws Exception {
        byte[] source = {0, 1, 2, (byte) 255};
        ApiResponse response = new ApiResponse(200, Map.of(), source, Duration.ofMillis(4), 1);
        source[0] = 8;
        Assert.assertEquals(response.bytes(), new byte[]{0, 1, 2, (byte) 255});

        Path directory = Files.createTempDirectory("api-response-contract");
        Path destination = directory.resolve("nested/result.bin");
        try {
            Assert.assertEquals(response.saveTo(destination), destination.toAbsolutePath());
            Assert.assertEquals(Files.readAllBytes(destination), response.bytes());
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(destination.getParent());
            Files.deleteIfExists(directory);
        }
    }
}
