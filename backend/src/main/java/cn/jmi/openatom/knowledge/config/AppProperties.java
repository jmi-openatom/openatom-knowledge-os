package cn.jmi.openatom.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    Security security,
    Oidc oidc,
    Storage storage,
    Search search,
    Ai ai) {

  public record Security(boolean devMode) {}

  public record Oidc(String issuer, String introspectionUri, String userInfoUri) {}

  public record Storage(
      String provider,
      String localRoot,
      String endpoint,
      String accessKey,
      String secretKey,
      String bucket) {}

  public record Search(String endpoint, String index) {}

  public record Ai(String provider, String baseUrl, String apiKey, String model) {}
}
