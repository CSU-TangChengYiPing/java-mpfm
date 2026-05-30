package com.mpfm.backend.application.driver.webdav;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

class WebdavDriverUtilTests {

    @Test
    void normalizePathShouldMapBlankToDotAndKeepLeadingSlash() {
        assertThat(WebdavDriverUtil.normalizePath("")).isEqualTo(".");
        assertThat(WebdavDriverUtil.normalizePath("a/b")).isEqualTo("/a/b");
    }

    @Test
    void joinShouldAppendChildUnderParent() {
        assertThat(WebdavDriverUtil.join(".", "demo.txt")).isEqualTo("/demo.txt");
        assertThat(WebdavDriverUtil.join("/base", "folder")).isEqualTo("/base/folder");
    }

    @Test
    void resolveShouldKeepWebdavRootPath() {
        WebdavDriverUtil.DavConnection connection = new WebdavDriverUtil.DavConnection(
                HttpClient.newHttpClient(),
                URI.create("https://dav.jianguoyun.com/dav/"),
                null);
        URI resolved = WebdavDriverUtil.resolve(connection, ".");
        assertThat(resolved.toString()).isEqualTo("https://dav.jianguoyun.com/dav/");
    }
}
