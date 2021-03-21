package edu.kpi.testcourse.rest.dto;

import java.util.Objects;

public class UrlCreateResponse {

  private String shorten;
  private String url;
  private String username;

  public UrlCreateResponse() {
  }

  public UrlCreateResponse(String shorten, String url, String username) {
    this.shorten = shorten;
    this.url = url;
    this.username = username;
  }

  public String getShorten() {
    return shorten;
  }

  public void setShorten(String shorten) {
    this.shorten = shorten;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UrlCreateResponse that = (UrlCreateResponse) o;
    return Objects.equals(shorten, that.shorten)
      && Objects.equals(url, that.url)
      && Objects.equals(username, that.username);
  }

  @Override
  public int hashCode() {
    return Objects.hash(shorten, url, username);
  }
}
