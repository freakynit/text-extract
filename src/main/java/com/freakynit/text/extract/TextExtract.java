package com.freakynit.text.extract;

import java.util.*;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class TextExtract {
    // Expanded list of tags to skip
    private static final Set<String> SKIPPED_TAGS = new HashSet<>(Arrays.asList(
            "img", "video", "audio", "script", "style", "nav", "button", "aside",
            "iframe", "embed", "object", "param", "link", "base", "track", "source",
            "noscript", "wbr", "header", "footer", "form", "input", "select", "textarea",
            "label", "fieldset", "legend", "canvas", "svg", "map", "area", "meta",
            "title", "head", "html", "body"
    ));

    // Content area selectors (in priority order)
    private static final String[] CONTENT_SELECTORS = {
            "main", "article", "[role=main]", ".main-content", ".content",
            ".post-content", ".entry-content", ".article-content", "#content",
            "#main", ".container .content", ".page-content"
    };

    // Non-content class/id patterns
    private static final Pattern[] NON_CONTENT_PATTERNS = {
            Pattern.compile("(?i).*(nav|menu|sidebar|footer|header|banner|ad|advertisement|social|share|comment|related|recommendation).*"),
            Pattern.compile("(?i).*(cookie|popup|modal|overlay|promo|subscribe).*")
    };

    private static final Set<String> HEADING_TAGS = new HashSet<>(Arrays.asList(
            "h1", "h2", "h3", "h4", "h5", "h6"));

    private static final Map<String, String[]> MARKDOWN_FORMATTING = new HashMap<>();
    static {
        // Use lowercase keys to avoid case sensitivity issues
        MARKDOWN_FORMATTING.put("strong", new String[]{"**", "**"});
        MARKDOWN_FORMATTING.put("b", new String[]{"**", "**"});
        MARKDOWN_FORMATTING.put("em", new String[]{"*", "*"});
        MARKDOWN_FORMATTING.put("i", new String[]{"*", "*"});
        MARKDOWN_FORMATTING.put("code", new String[]{"`", "`"});
        MARKDOWN_FORMATTING.put("pre", new String[]{"``````"});
        MARKDOWN_FORMATTING.put("blockquote", new String[]{"> ", ""});
    }

    public static class Node {
        public String tag;
        public String text;
        public List<Node> children;
        public Map<String, String> attributes;

        public Node() {
            this.children = new ArrayList<>();
            this.attributes = new HashMap<>();
        }

        public Node(String tag) {
            this.tag = tag;
            this.children = new ArrayList<>();
            this.attributes = new HashMap<>();
        }
    }

    /**
     * Improved HTML parsing with main content detection
     */
    public static Node parseHtml(String html) {
        if (html == null || html.trim().isEmpty()) {
            throw new IllegalArgumentException("HTML string cannot be null or empty");
        }

        Document doc = Jsoup.parse(html);
        Element contentArea = findMainContentArea(doc);

        Node root = new Node("root");
        if (contentArea == null) {
            return root;
        }

        for (Element child : contentArea.children()) {
            Node childNode = convertElement(child);
            if (childNode != null) {
                root.children.add(childNode);
            }
        }
        return root;
    }

    /**
     * Find the main content area using various heuristics
     */
    private static Element findMainContentArea(Document doc) {
        // Try semantic selectors first
        for (String selector : CONTENT_SELECTORS) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                return elements.first();
            }
        }

        // Fallback: find element with highest content density
        Element body = doc.body();
        if (body == null) return null;

        Element bestCandidate = body;
        double bestScore = calculateContentScore(body);

        // Check direct children of body
        for (Element child : body.children()) {
            if (isNonContentElement(child)) continue;

            double score = calculateContentScore(child);
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = child;
            }
        }

        return bestCandidate;
    }

    /**
     * Calculate content density score for an element
     */
    private static double calculateContentScore(Element element) {
        if (element == null) return 0;

        String text = element.text();
        if (text.length() < 50) return 0; // Too short to be main content

        // Factors that increase score
        double score = text.length();

        // Bonus for paragraphs and content tags
        score += element.select("p").size() * 100;
        score += element.select("article, main, .content").size() * 200;

        // Penalty for navigation and non-content elements
        score -= element.select("nav, aside, footer, header").size() * 150;
        score -= element.select("script, style, form").size() * 200;

        // Text-to-tag ratio bonus
        int tagCount = element.select("*").size();
        if (tagCount > 0) {
            double textToTagRatio = (double) text.length() / tagCount;
            score += textToTagRatio * 10;
        }

        return Math.max(0, score);
    }

    /**
     * Check if element should be excluded from content
     */
    private static boolean isNonContentElement(Element element) {
        String className = element.className().toLowerCase();
        String id = element.id().toLowerCase();
        String combined = className + " " + id;

        for (Pattern pattern : NON_CONTENT_PATTERNS) {
            if (pattern.matcher(combined).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Node convertElement(Element element) {
        String tag = element.tagName().toLowerCase();
        if (SKIPPED_TAGS.contains(tag) || isNonContentElement(element)) {
            return null;
        }

        Node node = new Node(tag);

        // Store href for links
        if (tag.equals("a") && element.hasAttr("href")) {
            node.attributes.put("href", element.attr("href"));
        }

        // For elements that can contain mixed content (like paragraphs),
        // we need to preserve the order of text and child elements
        if (canContainMixedContent(tag)) {
            processMixedContent(element, node);
        } else {
            // For other elements, use the original logic
            String ownText = element.ownText().trim();
            if (!ownText.isEmpty()) {
                node.text = ownText;
            }

            for (Element child : element.children()) {
                Node childNode = convertElement(child);
                if (childNode != null) {
                    node.children.add(childNode);
                }
            }
        }

        return node;
    }

    private static boolean canContainMixedContent(String tag) {
        return Arrays.asList("p", "div", "span", "td", "th", "blockquote", "em", "strong", "b", "i").contains(tag);
    }

    private static void processMixedContent(Element element, Node parentNode) {
        // Process child nodes in order (both text nodes and elements)
        for (org.jsoup.nodes.Node child : element.childNodes()) {
            if (child instanceof org.jsoup.nodes.TextNode) {
                org.jsoup.nodes.TextNode textNode = (org.jsoup.nodes.TextNode) child;
                String text = textNode.text().trim();
                if (!text.isEmpty()) {
                    // Create a text node
                    Node textChild = new Node("text");
                    textChild.text = text;
                    parentNode.children.add(textChild);
                }
            } else if (child instanceof Element) {
                Element childElement = (Element) child;
                Node childNode = convertElement(childElement);
                if (childNode != null) {
                    parentNode.children.add(childNode);
                }
            }
        }
    }

    // Keep existing pruneTree methods...
    public static List<Node> pruneTree(Node node) {
        List<Node> pruned = new ArrayList<>();
        for (Node child : node.children) {
            pruned.addAll(pruneTreeHelper(child));
        }
        return pruned;
    }

    private static List<Node> pruneTreeHelper(Node node) {
        // Handle text nodes specially
        if ("text".equals(node.tag)) {
            if (node.text != null && !node.text.trim().isEmpty()) {
                Node textNode = new Node("text");
                textNode.text = node.text.trim();
                return Arrays.asList(textNode);
            }
            return new ArrayList<>();
        }

        List<Node> prunedChildren = new ArrayList<>();
        for (Node child : node.children) {
            prunedChildren.addAll(pruneTreeHelper(child));
        }

        boolean hasText = (node.text != null && !node.text.trim().isEmpty());
        boolean isHeading = HEADING_TAGS.contains(node.tag.toLowerCase());
        boolean isImportantTag = MARKDOWN_FORMATTING.containsKey(node.tag.toLowerCase())
                || isStructuralTag(node.tag);

        if (hasText || isHeading || isImportantTag || !prunedChildren.isEmpty()) {
            Node newNode = new Node(node.tag);
            newNode.text = hasText ? node.text.trim() : null;
            newNode.children = prunedChildren;
            newNode.attributes = node.attributes;
            return Arrays.asList(newNode);
        }

        return prunedChildren;
    }

    private static boolean isStructuralTag(String tag) {
        return Arrays.asList("div", "section", "p", "ul", "ol", "li", "blockquote").contains(tag.toLowerCase());
    }

    public static String nodeListToJson(List<Node> nodes) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodes);
    }

    /**
     * Improved Markdown rendering with consistent formatting
     */
    public static String renderToMarkdown(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Node> nodes = mapper.readValue(json, new TypeReference<List<Node>>() {});

        StringBuilder sb = new StringBuilder();
        MarkdownRenderer renderer = new MarkdownRenderer();

        for (Node node : nodes) {
            renderer.renderNode(node, sb, 0, false);
        }

        return cleanupMarkdown(sb.toString());
    }

    /**
     * Separate renderer class for cleaner logic
     */
    private static class MarkdownRenderer {

        public void renderNode(Node node, StringBuilder sb, int listDepth, boolean inList) {
            if (node == null) return;

            String tag = node.tag.toLowerCase();

            // Handle headings
            if (HEADING_TAGS.contains(tag)) {
                renderHeading(node, sb);
                return;
            }

            // Handle paragraphs
            if (tag.equals("p")) {
                renderParagraph(node, sb, listDepth, inList);
                return;
            }

            // Handle lists
            if (tag.equals("ul") || tag.equals("ol")) {
                renderList(node, sb, listDepth, tag.equals("ol"));
                return;
            }

            if (tag.equals("li")) {
                renderListItem(node, sb, listDepth);
                return;
            }

            // Handle formatted text
            if (MARKDOWN_FORMATTING.containsKey(tag.toLowerCase())) {
                renderFormattedText(node, sb, listDepth, inList);
                return;
            }

            // Handle links
            if (tag.equals("a")) {
                renderLink(node, sb);
                return;
            }

            // Handle blockquotes
            if (tag.equals("blockquote")) {
                renderBlockquote(node, sb);
                return;
            }

            // Default: render children
            for (Node child : node.children) {
                renderNode(child, sb, listDepth, inList);
            }
        }

        private void renderHeading(Node node, StringBuilder sb) {
            int level = Integer.parseInt(node.tag.substring(1));
            String marker = String.join("", Collections.nCopies(level, "#"));

            sb.append("\n\n").append(marker).append(" ");
            appendTextContent(node, sb);
            sb.append("\n\n");
        }

        private void renderParagraph(Node node, StringBuilder sb, int listDepth, boolean inList) {
            if (!inList) sb.append("\n");

            // Render children in order (now includes text nodes)
            for (Node child : node.children) {
                if ("text".equals(child.tag)) {
                    // This is a text node
                    sb.append(child.text);
                } else {
                    renderNode(child, sb, listDepth, inList);
                }
            }

            // No need to append node.text separately since it's now handled in children

            if (!inList) sb.append("\n\n");
        }

        private void renderList(Node node, StringBuilder sb, int listDepth, boolean ordered) {
            sb.append("\n");
            for (Node child : node.children) {
                renderNode(child, sb, listDepth, true);
            }
            sb.append("\n");
        }

        private void renderListItem(Node node, StringBuilder sb, int listDepth) {
            String indent = String.join("", Collections.nCopies(listDepth * 2, " "));
            sb.append(indent).append("- ");

            // Handle mixed content structure
            if (hasTextNodes(node)) {
                // New mixed content structure
                for (Node child : node.children) {
                    if ("text".equals(child.tag)) {
                        sb.append(child.text);
                    } else {
                        renderNode(child, sb, listDepth, true);
                    }
                }
            } else {
                // Traditional structure
                appendTextContent(node, sb);
            }

            sb.append("\n");
        }

        private boolean hasTextNodes(Node node) {
            return node.children.stream().anyMatch(child -> "text".equals(child.tag));
        }

        private String[] getMarkdownFormatting(String tag) {
            String[] formatting = MARKDOWN_FORMATTING.get(tag.toLowerCase());
            if (formatting == null || formatting.length < 2) {
                return new String[]{"", ""}; // Return empty strings as fallback
            }
            return formatting;
        }

        private void renderFormattedText(Node node, StringBuilder sb, int listDepth, boolean inList) {
            String[] formatting = getMarkdownFormatting(node.tag);
            sb.append(formatting[0]);
            appendTextContent(node, sb);
            sb.append(formatting[1]);
        }

        private void renderLink(Node node, StringBuilder sb) {
            sb.append("[");
            appendTextContent(node, sb);
            sb.append("]");

            String href = node.attributes.get("href");
            if (href != null && !href.isEmpty()) {
                sb.append("(").append(href).append(")");
            }
        }

        private void renderBlockquote(Node node, StringBuilder sb) {
            sb.append("\n> ");
            appendTextContent(node, sb);
            sb.append("\n\n");
        }

        private void appendTextContent(Node node, StringBuilder sb) {
            if (node.text != null && !node.text.isEmpty()) {
                sb.append(node.text);
            }

            for (Node child : node.children) {
                renderNode(child, sb, 0, false);
            }
        }
    }

    /**
     * Clean up the final markdown output
     */
    private static String cleanupMarkdown(String markdown) {
        return markdown
                .replaceAll("\n{3,}", "\n\n")  // Remove excessive newlines
                .replaceAll("(?m)^\\s+$", "")   // Remove whitespace-only lines
                .trim();
    }
}
