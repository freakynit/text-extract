# Text-Extract

> WIP

A lightweight Java library to convert HTML content into LLM compatible markdown without the usual HTML bloat. This project focuses on extracting meaningful content, pruning unnecessary elements, and applying Markdown formatting to provide clean, structured output.

The content is first parsed and pruned into an intermediary json format, which is then rendered to Markdown. Other kinds of renderers can be added if needed.

> 1. **Status:** *Work in Progress*  
> 2. While it is already more than 80% efficient and correct compared to firecrawl, there is still a lot of scope for improvements and needed work.
> 3. Evaluated using LLM as judge (top 3 LLM's, on 10+ webpages from simple to complex)
> 4. Token efficiency is roughly 10% less than that of firecrawl, and rendered markdown formatting quality is roughly 20% less.

## Features

- **HTML Parsing:** Converts raw HTML into a structured Node tree.
- **Content Pruning:** Removes non-essential nodes (e.g., `<script>`, `<style>`, etc.) while preserving key content like headings and formatted text.
- **JSON Conversion:** Serializes the pruned Node tree to a readable JSON format.
- **Markdown Rendering:** Recursively converts the JSON structure into Markdown, handling headings, paragraphs, lists, and common formatting (bold, italic, code, blockquote).
- **Customizable Processing:** Easily extendable to support additional tags or formatting styles.

## Getting Started

### Prerequisites

- **Java 8+**
- **Maven** (or your preferred Java build tool)

### Installation

Simply include the [TextExtract.java](src/main/java/com/freakynit/text/extract/TextExtract.java) file in your source tree. You may also compile it into a JAR file and add it as a dependency.

If you are using Maven, add the necessary dependencies in your `pom.xml`. For Gradle, update your `build.gradle` accordingly.

This only has `jackson-databind` and `jsoup` as a dependencies.

## Usage Example

Here's a brief example to get you started:

```java
import com.freakynit.text.extract.TextExtract;

import java.util.List;

public class Example {
    public static void main(String[] args) {
        try {
            String htmlContent = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <title>Welcome Page</title>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <h1>Welcome</h1>\n" +
                    "    <p>This is a sample HTML content.</p>\n" +
                    "    \n" +
                    "    <h2>Resources</h2>\n" +
                    "    <ul>\n" +
                    "        <li><a href=\"#\">Resource 1 - Introduction to HTML</a></li>\n" +
                    "        <li><a href=\"#\">Resource 2 - CSS Basics</a></li>\n" +
                    "    </ul>\n" +
                    "    \n" +
                    "    <h2>Additional Resources</h2>\n" +
                    "    <ul>\n" +
                    "        <li><a href=\"#\">Resource 3 - JavaScript Essentials</a></li>\n" +
                    "        <li><a href=\"#\">Resource 4 - Web Development Guide</a></li>\n" +
                    "    </ul>\n" +
                    "</body>\n" +
                    "</html>";

            // Parse HTML to Node tree
            TextExtract.Node root = TextExtract.parseHtml(htmlContent);

            // Prune the parsed nodes list to keep only the nodes containing text or media
            List<TextExtract.Node> prunedNodes = TextExtract.pruneTree(root);

            // Convert the pruned node list to JSON
            String json = TextExtract.nodeListToJson(prunedNodes);

            // Render the JSON as Markdown
            String markdown = TextExtract.renderToMarkdown(json);
            System.out.println(markdown);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

Output
```markdown
## Resources

[Resource 1 - Introduction to HTML] [Resource 2 - CSS Basics] 

## Additional Resources

[Resource 3 - JavaScript Essentials] [Resource 4 - Web Development Guide]
```

> Works for complex real world webpages equally well.

## Contributing

Contributions, feedback, and feature requests are welcome! If you'd like to contribute:

- Fork the repository.
- Implement your changes.
- Submit a pull request or open an issue.

---

## License

This project is licensed under the MIT License. See the LICENSE file for details.
