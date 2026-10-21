package com.automationanywhere.botcommand.aibrowser.agent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.microsoft.playwright.Page;

public class DomTreeExtractor {
    private static final Logger logger = LogManager.getLogger(DomTreeExtractor.class);

    private static final String HIGHLIGHT_SCRIPT = "(() => {\n" +
            "  // Remove any preexisting badges\n" +
            "  document.querySelectorAll('.ai-browser-badge').forEach(el => el.remove());\n" +
            "  document.querySelectorAll('[data-ai-id]').forEach(el => el.removeAttribute('data-ai-id'));\n" +
            "\n" +
            "  const selectors = [\n" +
            "    'a[href]', 'button', 'input', 'textarea', 'select',\n" +
            "    '[role=\"button\"]', '[role=\"link\"]', '[role=\"checkbox\"]',\n" +
            "    '[role=\"tab\"]', '[role=\"menuitem\"]', '[role=\"combobox\"]',\n" +
            "    '[role=\"searchbox\"]', '[contenteditable=\"true\"]'\n" +
            "  ];\n" +
            "\n" +
            "  const elements = Array.from(document.querySelectorAll(selectors.join(',')));\n" +
            "  let id = 1;\n" +
            "  const output = [];\n" +
            "\n" +
            "  for (const el of elements) {\n" +
            "    const rect = el.getBoundingClientRect();\n" +
            "    const style = window.getComputedStyle(el);\n" +
            "    if (rect.width <= 0 || rect.height <= 0 || style.visibility === 'hidden' || style.display === 'none' || style.opacity === '0') {\n"
            +
            "      continue;\n" +
            "    }\n" +
            "    // Must be somewhat within or near viewport\n" +
            "    if (rect.bottom < 0 || rect.top > window.innerHeight || rect.right < 0 || rect.left > window.innerWidth) {\n"
            +
            "      continue;\n" +
            "    }\n" +
            "\n" +
            "    el.setAttribute('data-ai-id', id.toString());\n" +
            "\n" +
            "    // Create visual overlay badge\n" +
            "    const badge = document.createElement('div');\n" +
            "    badge.className = 'ai-browser-badge';\n" +
            "    badge.textContent = id.toString();\n" +
            "    badge.style.position = 'fixed';\n" +
            "    badge.style.left = Math.max(0, rect.left) + 'px';\n" +
            "    badge.style.top = Math.max(0, rect.top) + 'px';\n" +
            "    badge.style.backgroundColor = '#FFD700';\n" +
            "    badge.style.color = '#000000';\n" +
            "    badge.style.fontSize = '11px';\n" +
            "    badge.style.fontWeight = 'bold';\n" +
            "    badge.style.padding = '1px 4px';\n" +
            "    badge.style.borderRadius = '3px';\n" +
            "    badge.style.border = '1px solid #B8860B';\n" +
            "    badge.style.zIndex = '2147483647';\n" +
            "    badge.style.pointerEvents = 'none';\n" +
            "    document.body.appendChild(badge);\n" +
            "\n" +
            "    const tag = el.tagName.toLowerCase();\n" +
            "    const text = (el.innerText || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.getAttribute('value') || '').trim().replace(/\\s+/g, ' ').substring(0, 80);\n"
            +
            "    const type = el.getAttribute('type') || '';\n" +
            "    let desc = `[${id}] <${tag}`; \n" +
            "    if (type) desc += ` type=\"${type}\"`;\n" +
            "    if (el.getAttribute('placeholder')) desc += ` placeholder=\"${el.getAttribute('placeholder')}\"`;\n" +
            "    if (el.getAttribute('aria-label')) desc += ` aria-label=\"${el.getAttribute('aria-label')}\"`;\n" +
            "    desc += `>${text ? ' \"' + text + '\"' : ''}</${tag}>`;\n" +
            "    output.push(desc);\n" +
            "    id++;\n" +
            "    if (id > 80) break; // Limit elements per screen for token efficiency\n" +
            "  }\n" +
            "\n" +
            "  return output.join('\\n');\n" +
            "})();";

    private static final String CLEANUP_SCRIPT = "(() => {\n" +
            "  document.querySelectorAll('.ai-browser-badge').forEach(el => el.remove());\n" +
            "})();";

    public static String highlightAndExtract(Page page) {
        try {
            Object result = page.evaluate(HIGHLIGHT_SCRIPT);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            logger.warn("Could not extract interactive elements: {}", e.getMessage());
            return "";
        }
    }

    public static void removeHighlights(Page page) {
        try {
            page.evaluate(CLEANUP_SCRIPT);
        } catch (Exception e) {
            logger.debug("Highlight cleanup skipped: {}", e.getMessage());
        }
    }
}
