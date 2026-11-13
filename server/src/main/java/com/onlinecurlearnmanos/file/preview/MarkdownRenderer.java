package com.onlinecurlearnmanos.file.preview;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 面向预览场景的最小 Markdown 渲染器。
 *
 * <p>安全模型是「先整体转义，再套白名单标签」：源文本里的 {@code < > & "} 全部先转义成实体，
 * 之后只由服务端插入 {@code strong / em / code / a / li / h1-h6 / blockquote / pre} 等固定标签，
 * 因此用户无法通过 Markdown 注入脚本或事件属性，无需额外的 HTML 清洗库。</p>
 */
@Component
public class MarkdownRenderer {

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]{1,200})\\]\\(([^)\\s]{1,500})\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]{1,500})\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*([^*\\n]{1,300})\\*(?!\\*)");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]{1,300})`");

    public String render(String markdown) {
        List<String> lines = List.of(markdown.replace("\r\n", "\n").replace('\r', '\n')
                .split("\n", -1));
        StringBuilder html = new StringBuilder(markdown.length() + 64);
        List<String> paragraph = new ArrayList<>();
        boolean inCode = false;
        StringBuilder code = new StringBuilder();
        String listTag = null;

        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();
            if (line.stripLeading().startsWith("```")) {
                if (inCode) {
                    html.append("<pre><code>").append(escape(code.toString())).append("</code></pre>");
                    code.setLength(0);
                    inCode = false;
                } else {
                    flushParagraph(html, paragraph);
                    listTag = closeList(html, listTag);
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                code.append(line).append('\n');
                continue;
            }
            if (line.isBlank()) {
                flushParagraph(html, paragraph);
                listTag = closeList(html, listTag);
                continue;
            }
            String heading = headingLevel(line);
            if (heading != null) {
                flushParagraph(html, paragraph);
                listTag = closeList(html, listTag);
                int level = heading.length();
                html.append("<h").append(level).append('>')
                        .append(inline(escape(line.substring(level).trim())))
                        .append("</h").append(level).append('>');
                continue;
            }
            if (line.matches("^\\s*([-*_])\\1{2,}\\s*$")) {
                flushParagraph(html, paragraph);
                listTag = closeList(html, listTag);
                html.append("<hr />");
                continue;
            }
            if (line.startsWith("> ")) {
                flushParagraph(html, paragraph);
                listTag = closeList(html, listTag);
                html.append("<blockquote>").append(inline(escape(line.substring(2))))
                        .append("</blockquote>");
                continue;
            }
            String marker = listMarker(line);
            if (marker != null) {
                flushParagraph(html, paragraph);
                String wanted = marker.equals("ol") ? "ol" : "ul";
                if (listTag == null || !listTag.equals(wanted)) {
                    listTag = closeList(html, listTag);
                    html.append('<').append(wanted).append('>');
                    listTag = wanted;
                }
                html.append("<li>")
                        .append(inline(escape(line.substring(marker.equals("ol")
                                ? line.indexOf('.') + 1
                                : 2).trim())))
                        .append("</li>");
                continue;
            }
            if (paragraph.isEmpty()) {
                listTag = closeList(html, listTag);
            }
            paragraph.add(line);
        }
        if (inCode) {
            html.append("<pre><code>").append(escape(code.toString())).append("</code></pre>");
        }
        flushParagraph(html, paragraph);
        closeList(html, listTag);
        return html.toString();
    }

    private String headingLevel(String line) {
        int level = 0;
        while (level < line.length() && level < 6 && line.charAt(level) == '#') {
            level++;
        }
        if (level == 0) {
            return null;
        }
        if (level == line.length() || line.charAt(level) != ' ') {
            return null;
        }
        return "#".repeat(level);
    }

    private String listMarker(String line) {
        if (line.startsWith("- ") || line.startsWith("* ")) {
            return "ul";
        }
        return line.matches("^\\d{1,3}\\.\\s+.*$") ? "ol" : null;
    }

    private void flushParagraph(StringBuilder html, List<String> paragraph) {
        if (paragraph.isEmpty()) {
            return;
        }
        html.append("<p>").append(inline(escape(String.join("\n", paragraph)).replace("\n", "<br />")))
                .append("</p>");
        paragraph.clear();
    }

    private String closeList(StringBuilder html, String listTag) {
        if (listTag != null) {
            html.append("</").append(listTag).append('>');
        }
        return null;
    }

    private String inline(String escaped) {
        String result = INLINE_CODE.matcher(escaped).replaceAll("<code>$1</code>");
        result = BOLD.matcher(result).replaceAll("<strong>$1</strong>");
        result = ITALIC.matcher(result).replaceAll("<em>$1</em>");
        Matcher matcher = LINK.matcher(result);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String text = matcher.group(1);
            String url = matcher.group(2);
            String replacement = url.startsWith("http://") || url.startsWith("https://")
                    ? "<a href=\"" + url + "\" target=\"_blank\" rel=\"noopener noreferrer\">"
                    + text + "</a>"
                    : text;
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(current);
            }
        }
        return escaped.toString();
    }
}
