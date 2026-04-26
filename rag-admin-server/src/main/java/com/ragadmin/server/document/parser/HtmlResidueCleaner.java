package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Order(12)
public class HtmlResidueCleaner implements DocumentCleanerStep {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(
            "</?(?:div|span|p|br\\s*/?|b|i|em|strong|a\\b[^>]*|font\\b[^>]*|center|table|tr|td|th|tbody|thead|ul|ol|li|h[1-6]|img\\b[^>]*|section|article|header|footer|nav|main|figure|figcaption|blockquote|pre|code|sub|sup|small|big|u|s|hr\\s*/?)[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile(" {2,}");

    @Override
    public boolean supports(DocumentCleanContext context) {
        return true;
    }

    @Override
    public List<Document> clean(List<Document> documents, DocumentCleanContext context) {
        List<Document> cleaned = new ArrayList<>();
        for (Document document : documents) {
            if (document == null || !document.isText()) {
                continue;
            }
            String cleanedText = cleanText(document.getText());
            if (!StringUtils.hasText(cleanedText)) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
            metadata.put("htmlResidueCleaned", Boolean.TRUE);
            cleaned.add(new Document(document.getId(), cleanedText, metadata));
        }
        return cleaned;
    }

    String cleanText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String result = text;
        result = decodeHtmlEntities(result);
        result = HTML_TAG_PATTERN.matcher(result).replaceAll("");
        result = MULTI_SPACE_PATTERN.matcher(result).replaceAll(" ");
        return result;
    }

    private String decodeHtmlEntities(String text) {
        String result = text;
        result = result.replace("&nbsp;", " ");
        result = result.replace("&amp;", "&");
        result = result.replace("&lt;", "<");
        result = result.replace("&gt;", ">");
        result = result.replace("&quot;", "\"");
        result = result.replace("&#39;", "'");
        return result;
    }
}
