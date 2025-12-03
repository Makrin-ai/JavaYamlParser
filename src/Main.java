import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class Main {

    public static void main(String[] args) throws IOException {
        Map<String, Object> constants = new HashMap<>();
        Map<String, Object> output = new LinkedHashMap<>();

        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Введите путь к файлу с тестом(пример:/Users/manulvenerable/Downloads/MyYamlProject/lib/tests/course.txt):");
        String filePath = console.readLine().trim();

        // Читаем файл
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (!line.isEmpty() && !line.startsWith("C")) {
                    lines.add(line);
                }
            }
        }

        // 1. Парсим константы "имя is значение"
        Pattern pattern = Pattern.compile("^([_a-z]+) is (\\{[\\s\\S]*?\\}|\".*?\"|\\S+)$");
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("{") && !line.endsWith("}")) {
                StringBuilder sb = new StringBuilder(line);
                while (!line.contains("}")) {
                    i++;
                    if (i >= lines.size()) break;
                    line = lines.get(i);
                    sb.append("\n").append(line);
                }
                line = sb.toString();
            }
            Matcher m = pattern.matcher(line);
            if (m.matches()) {
                String name = m.group(1);
                String valueStr = m.group(2).trim();
                Object val = parseValue(valueStr, constants);
                constants.put(name, val);
            }
        }

        // 2. Формируем output
        for (String key : constants.keySet()) {
            Object val = constants.get(key);
            if (val instanceof Map || key.endsWith("_config") || key.equals("course")) {
                output.put(key, val);
            }
        }

        // 3. Настройки YAML
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        Yaml yaml = new Yaml(options);
        System.out.println(yaml.dump(cloneMap(output))); // клонируем, чтобы не было ссылок
    }

    private static Object parseValue(String s, Map<String, Object> constants) {
        s = s.trim();
        if (s.isEmpty()) return null;

        // Словарь
        if (s.startsWith("{") && s.endsWith("}")) {
            return parseDict(s.substring(1, s.length() - 1), constants);
        }

        // Константа
        if (s.startsWith("[") && s.endsWith("]")) {
            String cname = s.substring(1, s.length() - 1).trim();
            if (!constants.containsKey(cname))
                throw new RuntimeException("Неизвестная константа: " + cname);
            return constants.get(cname);
        }

        // Целые числа
        if (s.matches("[+-]?\\d+")) return Integer.parseInt(s);

        // Дробные числа
        if (s.matches("[+-]?\\d*\\.\\d+([eE][+-]?\\d+)?")) return Double.parseDouble(s);

        // Логические
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")) return Boolean.parseBoolean(s);

        // Строка в кавычках
        if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);

        // Простая строка
        return s;
    }

    private static Map<String, Object> parseDict(String text, Map<String, Object> constants) {
        Map<String, Object> map = new LinkedHashMap<>();
        int level = 0;
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') level++;
            if (c == '}') level--;

            if ((c == '\n' || c == ',') && level == 0) {
                addEntry(map, buf.toString(), constants);
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        addEntry(map, buf.toString(), constants);
        return map;
    }

    private static void addEntry(Map<String, Object> map, String line, Map<String, Object> constants) {
        line = line.trim();
        if (line.isEmpty()) return;
        int eq = line.indexOf('=');
        if (eq == -1) throw new RuntimeException("Ошибка словаря: " + line);
        String key = line.substring(0, eq).trim();
        String val = line.substring(eq + 1).trim();
        map.put(key, parseValue(val, constants));
    }

    // Метод клонирования map, чтобы избежать ссылок в YAML
    private static Map<String, Object> cloneMap(Map<String, Object> original) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = cloneMap((Map<String, Object>) value);
            }
            copy.put(entry.getKey(), value);
        }
        return copy;
    }
}
