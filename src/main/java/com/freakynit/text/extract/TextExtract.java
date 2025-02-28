package com.freakynit.text.extract;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class TextExtract {
    private static final Set<String> SKIPPED_TAGS = new HashSet<>(Arrays.asList(
            "img", "video", "audio", "script", "style", "nav", "button", "aside",
            "iframe", "embed", "object", "param", "link",
            "base", "track", "source", "noscript", "wbr"));

    // Set of heading tags
    private static final Set<String> HEADING_TAGS = new HashSet<>(Arrays.asList(
            "h1", "h2", "h3", "h4", "h5", "h6"));

    // Mapping of HTML tags to Markdown formatting
    private static final Map<String, String[]> MARKDOWN_FORMATTING = new HashMap<>();
    static {
        // Format: {prefix, suffix}
        MARKDOWN_FORMATTING.put("strong", new String[]{"**", "**"});
        MARKDOWN_FORMATTING.put("b", new String[]{"**", "**"});
        MARKDOWN_FORMATTING.put("em", new String[]{"*", "*"});
        MARKDOWN_FORMATTING.put("i", new String[]{"*", "*"});
        MARKDOWN_FORMATTING.put("code", new String[]{"`", "`"});
        MARKDOWN_FORMATTING.put("pre", new String[]{"```\n", "\n```"});
        MARKDOWN_FORMATTING.put("blockquote", new String[]{"> ", ""});
        MARKDOWN_FORMATTING.put("a", new String[]{"[", "]"});
    }

    // A simple Node class representing an element.
    public static class Node {
        public String tag;      // HTML tag name (e.g., "div", "p", etc.)
        public String text;     // Text content (if any)
        public List<Node> children; // Child nodes

        // Default constructor for Jackson.
        public Node() {
            this.children = new ArrayList<>();
        }

        public Node(String tag) {
            this.tag = tag;
            this.children = new ArrayList<>();
        }
    }

    /**
     * Parses an HTML string into a tree of Nodes using jsoup.
     */
    public static Node parseHtml(String html) {
        if (html == null || html.trim().isEmpty()) {
            throw new IllegalArgumentException("HTML string cannot be null or empty");
        }

        Node root = new Node("root");
        Document doc = Jsoup.parse(html);
        Element body = doc.body();
        if (body == null) {
            return root;
        }
        for (Element child : body.children()) {
            Node childNode = convertElement(child);
            if (childNode != null) {
                root.children.add(childNode);
            }
        }
        return root;
    }

    /**
     * Recursively converts a jsoup Element into a Node.
     * Skips elements in the SKIPPED_TAGS set.
     */
    private static Node convertElement(Element element) {
        String tag = element.tagName();
        if(SKIPPED_TAGS.contains(tag.toLowerCase())) {
            return null;
        }

        Node node = new Node(tag);
        String ownText = element.ownText().trim();
        if (!ownText.isEmpty()) {
            node.text = ownText;
        }


        // Process child elements
        for (Element child : element.children()) {
            Node childNode = convertElement(child);
            if (childNode != null) {
                node.children.add(childNode);
            }
        }
        return node;
    }

    /**
     * Prunes the tree so that only nodes that contain non‑empty text or a media URL are kept.
     * If a node is "empty" but has valid children, its valid children are promoted.
     * The returned list is the pruned children of the given node.
     */
    public static List<Node> pruneTree(Node node) {
        List<Node> pruned = new ArrayList<>();
        for (Node child : node.children) {
            pruned.addAll(pruneTreeHelper(child));
        }
        return pruned;
    }

    private static List<Node> pruneTreeHelper(Node node) {
        List<Node> prunedChildren = new ArrayList<>();
        for (Node child : node.children) {
            prunedChildren.addAll(pruneTreeHelper(child));
        }
        boolean hasText = (node.text != null && !node.text.trim().isEmpty());
        boolean isHeading = HEADING_TAGS.contains(node.tag.toLowerCase());

        // Keep nodes with text, media, heading tags, or special formatting
        if (hasText || isHeading || MARKDOWN_FORMATTING.containsKey(node.tag.toLowerCase())) {
            Node newNode = new Node(node.tag);
            newNode.text = hasText ? node.text.trim() : null;
            newNode.children = prunedChildren;
            //newNode.attributes = node.attributes;
            List<Node> list = new ArrayList<>();
            list.add(newNode);
            return list;
        } else {
            // Node itself is empty; promote its valid children.
            return prunedChildren;
        }
    }

    /**
     * Converts a list of pruned Nodes to a JSON string.
     */
    public static String nodeListToJson(List<Node> nodes) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodes);
    }

    /**
     * The renderer function takes the generated JSON string and outputs Markdown
     * while preserving the hierarchical structure and properly handling heading tags.
     */
    public static String renderToMarkdown(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Node> nodes = mapper.readValue(json, new TypeReference<List<Node>>() {});
        StringBuilder sb = new StringBuilder();
        for (Node node : nodes) {
            renderNodeMarkdown(node, sb, 0);
        }
        return sb.toString();
    }

    /**
     * Recursively renders a Node and its children as Markdown.
     * Properly renders heading tags and maintains formatting.
     */
    private static void renderNodeMarkdown(Node node, StringBuilder sb, int level) {
        if (node == null) return;

        String tagLower = node.tag.toLowerCase();

        // Handle heading tags (h1-h6)
        if (HEADING_TAGS.contains(tagLower)) {
            int headingLevel = Integer.parseInt(tagLower.substring(1));
            String headingMarker = String.join("", Collections.nCopies(headingLevel, "#"));

            if (node.text != null && !node.text.isEmpty()) {
                sb.append("\n\n").append(headingMarker).append(" ").append(node.text).append("\n\n");
            }

            // Render children of the heading
            for (Node child : node.children) {
                renderNodeMarkdown(child, sb, level);
            }
            return;
        }

        // Handle paragraphs with proper spacing
        if (tagLower.equals("p")) {
            if (node.text != null && !node.text.isEmpty()) {
                sb.append(node.text).append("\n\n");
            }

            // Process children with their formatting
            for (Node child : node.children) {
                renderNodeMarkdown(child, sb, level);
            }

            return;
        }

        // Handle special formatting tags
        if (MARKDOWN_FORMATTING.containsKey(tagLower)) {
            String[] formatting = MARKDOWN_FORMATTING.get(tagLower);

            if (node.text != null && !node.text.isEmpty()) {
                sb.append(formatting[0]).append(node.text);
                sb.append(formatting[1]);
                sb.append(" ");
            }

            // Process children with their formatting
            for (Node child : node.children) {
                renderNodeMarkdown(child, sb, level);
            }

            return;
        }

        // Handle lists (ul/ol/li)
        if (tagLower.equals("ul") || tagLower.equals("ol")) {
            // Process list items with appropriate indentation
            for (Node child : node.children) {
                renderNodeMarkdown(child, sb, level + 1);
            }
            sb.append("\n");
            return;
        }

        if (tagLower.equals("li")) {
            int displayLevel = Math.min(level, 6);
            String indent = String.join("", Collections.nCopies(Math.max(0, displayLevel - 1) * 2, " "));

            if (node.text != null && !node.text.isEmpty()) {
                sb.append(indent).append("- ").append(node.text).append("\n");
            } else {
                sb.append(indent).append("- ");
            }

            // Process child elements
            for (Node child : node.children) {
                renderNodeMarkdown(child, sb, level + 1);
            }

            return;
        }

        // Default case: render text content with indentation for other elements
        if (node.text != null && !node.text.isEmpty()) {
            int displayLevel = Math.min(level, 6);
            String indent = String.join("", Collections.nCopies(displayLevel * 2, " "));

            if (level > 0) {
                sb.append(indent).append("- ");
            }

            sb.append(node.text).append("\n");
        }

        // Process child elements
        for (Node child : node.children) {
            renderNodeMarkdown(child, sb, level + 1);
        }
    }
}
