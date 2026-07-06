package com.pixplaze.api.web.configuration.json;

import tools.jackson.databind.JsonNode;

import java.util.Map;

public class MinecraftJsonConverter {

    // Карта стандартных именованных цветов Minecraft в их классические коды
    private static final Map<String, String> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", "0"), Map.entry("dark_blue", "1"), Map.entry("dark_green", "2"), Map.entry("dark_aqua", "3"),
            Map.entry("dark_red", "4"), Map.entry("dark_purple", "5"), Map.entry("gold", "6"), Map.entry("gray", "7"),
            Map.entry("dark_gray", "8"), Map.entry("blue", "9"), Map.entry("green", "a"), Map.entry("aqua", "b"),
            Map.entry("red", "c"), Map.entry("light_purple", "d"), Map.entry("yellow", "e"), Map.entry("white", "f")
    );

    // Внутренний неизменяемый класс для передачи контекста стилей вниз по дереву рекурсии
    private static class StyleContext {
        final String color;
        final boolean bold;
        final boolean italic;
        final boolean underlined;
        final boolean strikethrough;
        final boolean obfuscated;

        StyleContext(String color, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated) {
            this.color = color;
            this.bold = bold;
            this.italic = italic;
            this.underlined = underlined;
            this.strikethrough = strikethrough;
            this.obfuscated = obfuscated;
        }

        // Создает новый контекст на основе текущего и переопределенных свойств узла JSON
        StyleContext merge(JsonNode node) {
            String nextColor = node.has("color") ? node.get("color").asText() : this.color;

            // Если цвет изменился, то по правилам Minecraft все текстовые модификаторы сбрасываются
            boolean baseBold = (nextColor != null && !nextColor.equals(this.color)) ? false : this.bold;
            boolean baseItalic = (nextColor != null && !nextColor.equals(this.color)) ? false : this.italic;
            boolean baseUnderlined = (nextColor != null && !nextColor.equals(this.color)) ? false : this.underlined;
            boolean baseStrikethrough = (nextColor != null && !nextColor.equals(this.color)) ? false : this.strikethrough;
            boolean baseObfuscated = (nextColor != null && !nextColor.equals(this.color)) ? false : this.obfuscated;

            return new StyleContext(
                    nextColor,
                    node.has("bold") ? node.get("bold").asBoolean() : baseBold,
                    node.has("italic") ? node.get("italic").asBoolean() : baseItalic,
                    node.has("underlined") ? node.get("underlined").asBoolean() : baseUnderlined,
                    node.has("strikethrough") ? node.get("strikethrough").asBoolean() : baseStrikethrough,
                    node.has("obfuscated") ? node.get("obfuscated").asBoolean() : baseObfuscated
            );
        }
    }

    public static String convertToLegacy(tools.jackson.databind.JsonNode rootNode) {
        StringBuilder sb = new StringBuilder();
        // Начальный контекст: без цвета, все стили выключены
        StyleContext initialContext = new StyleContext(null, false, false, false, false, false);

        processNode(rootNode, initialContext, sb);
        return sb.toString();
    }

    private static void processNode(JsonNode node, StyleContext parentContext, StringBuilder sb) {
        if (node == null || node.isMissingNode()) return;

        // 1. Наследуем и переопределяем стили для текущего узла
        StyleContext currentContext = parentContext.merge(node);

        // 2. Обрабатываем текст текущего узла
        if (node.has("text")) {
            String text = node.get("text").asText();

            // Генерируем префиксы только если в узле действительно есть текст
            if (!text.isEmpty()) {
                appendStylePrefixes(currentContext, sb);
                sb.append(text);
            }
        }

        // 3. Рекурсивно обходим массив "extra", если он существует
        if (node.has("extra") && node.get("extra").isArray()) {
            for (JsonNode extraNode : node.get("extra")) {
                processNode(extraNode, currentContext, sb);
            }
        }
    }

    private static void appendStylePrefixes(StyleContext context, StringBuilder sb) {
        // Добавляем цвет
        if (context.color != null) {
            if (context.color.startsWith("#")) {
                // Преобразование HEX: #9DFF00 -> &x&9&d&f&f&0&0
                sb.append("&x");
                for (char c : context.color.substring(1).toLowerCase().toCharArray()) {
                    sb.append("&").append(c);
                }
            } else if (NAMED_COLORS.containsKey(context.color)) {
                sb.append("&").append(NAMED_COLORS.get(context.color));
            }
        }

        // Добавляем модификаторы шрифта
        if (context.bold) sb.append("&l");
        if (context.italic) sb.append("&o");
        if (context.underlined) sb.append("&n");
        if (context.strikethrough) sb.append("&m");
        if (context.obfuscated) sb.append("&k");
    }
}
