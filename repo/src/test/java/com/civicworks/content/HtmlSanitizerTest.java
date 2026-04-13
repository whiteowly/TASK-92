package com.civicworks.content;

import com.civicworks.content.application.ContentService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sanitizer payload tests for {@link ContentService#sanitizeHtml(String)}.
 * The sanitizer is now an allowlist-based jsoup pipeline — these tests pin
 * down the high-risk payload classes that the previous regex strip missed.
 */
class HtmlSanitizerTest {

    private static String sanitize(String html) throws Exception {
        Method m = ContentService.class.getDeclaredMethod("sanitizeHtml", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, html);
    }

    @Test
    void stripsScriptTag() throws Exception {
        String out = sanitize("<p>Hi</p><script>alert('xss')</script>");
        assertFalse(out.toLowerCase().contains("<script"));
        assertFalse(out.contains("alert("));
        assertTrue(out.contains("<p>Hi</p>"));
    }

    @Test
    void stripsEventHandlerAttributes() throws Exception {
        String out = sanitize("<a href=\"https://example.com\" onclick=\"steal()\">x</a>");
        assertFalse(out.toLowerCase().contains("onclick"));
        assertFalse(out.contains("steal("));
        assertTrue(out.contains("href=\"https://example.com\""));
    }

    @Test
    void stripsJavascriptUriInHref() throws Exception {
        String out = sanitize("<a href=\"javascript:alert(1)\">click</a>");
        assertFalse(out.toLowerCase().contains("javascript:"));
    }

    @Test
    void stripsJavascriptUriInImgSrc() throws Exception {
        String out = sanitize("<img src=\"javascript:alert(1)\">");
        assertFalse(out.toLowerCase().contains("javascript:"));
    }

    @Test
    void preservesBenignFormatting() throws Exception {
        String out = sanitize("<p><strong>Hello</strong> <em>world</em></p>");
        assertTrue(out.contains("<strong>Hello</strong>"));
        assertTrue(out.contains("<em>world</em>"));
    }

    @Test
    void nullPassesThrough() throws Exception {
        assertNull(sanitize(null));
    }
}
