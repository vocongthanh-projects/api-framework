package vn.ots.qc.api.core.security;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

public class RedactorTest {
    @Test
    public void redactsHeadersAndNestedJsonSecrets() {
        Redactor redactor = new Redactor();
        Assert.assertEquals(redactor.headers(Map.of("Authorization", "Bearer secret")).get("Authorization"), Redactor.MASK);
        String body = redactor.body("{\"username\":\"user\",\"nested\":{\"password\":\"secret\"}}");
        Assert.assertTrue(body.contains("\"password\":\"<redacted>\""));
        Assert.assertTrue(body.contains("\"username\":\"user\""));
    }

    @Test
    public void redactsSecretsFromNonJsonAndExceptionText() {
        Redactor redactor = new Redactor();
        String form = redactor.body("username=user&password=plain-secret&access_token=token-value");
        String error = redactor.text("Request failed with Authorization: Bearer abc.def.ghi");

        Assert.assertFalse(form.contains("plain-secret"));
        Assert.assertFalse(form.contains("token-value"));
        Assert.assertFalse(error.contains("abc.def.ghi"));
    }
}
