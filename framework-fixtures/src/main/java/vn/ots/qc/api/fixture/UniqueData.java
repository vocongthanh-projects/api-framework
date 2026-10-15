package vn.ots.qc.api.fixture;

import java.util.Locale;
import java.util.UUID;

public final class UniqueData {
  private UniqueData() {}

  public static String suffix() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toLowerCase(Locale.ROOT);
  }

  public static String value(String prefix) {
    return prefix + "-" + suffix();
  }

  public static String email(String prefix, String domain) {
    return prefix + "." + suffix() + "@" + domain;
  }
}
