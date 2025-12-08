import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class Main {

    public static void main(String[] args) throws IOException {
        // Мапа для хранения констант, извлеченных из текста
        Map<String, Object> constants = new HashMap<>();
        // Результирующая мапа для вывода в YAML (LinkedHashMap сохраняет порядок)
        Map<String, Object> output = new LinkedHashMap<>();

        // Чтение пути к файлу с тестом из консоли
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Введите путь к файлу с тестом(пример:/Users/manulvenerable/Downloads/MyYamlProject/lib/tests/course.txt):");
        String filePath = console.readLine().trim();

        // Чтение содержимого файла в список строк
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip(); // Убираем пробелы в начале и конце
                // Игнорируем пустые строки и строки, начинающиеся с 'C' (возможно, комментарии)
                if (!line.isEmpty() && !line.startsWith("C")) {
                    lines.add(line);
                }
            }
        }

        // 1. Парсинг констант в формате "имя is значение"
        // Регулярное выражение для поиска строк вида: имя is {словарь} или имя is "строка" или имя is число
        Pattern pattern = Pattern.compile("^([_a-z]+) is (\\{[\\s\\S]*?\\}|\".*?\"|\\S+)$");
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // Если строка содержит {, но не заканчивается }, собираем многострочный словарь
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
                String name = m.group(1);       // Имя константы
                String valueStr = m.group(2).trim(); // Значение как строка
                // Парсим значение (словарь, число, строку и т.д.)
                Object val = parseValue(valueStr, constants);
                constants.put(name, val);
            }
        }

        // 2. Формируем output — выбираем только те константы, которые нужны для вывода:
        // словари, заканчивающиеся на _config или имеющие имя "course"
        for (String key : constants.keySet()) {
            Object val = constants.get(key);
            if (val instanceof Map || key.endsWith("_config") || key.equals("course")) {
                output.put(key, val);
            }
        }

        // 3. Настройки вывода YAML: блоковый стиль, отступы
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // Блочный стиль для читаемости
        options.setPrettyFlow(true);
        options.setIndent(2); // Отступ 2 пробела

        // Создаём объект Yaml с указанными настройками
        Yaml yaml = new Yaml(options);
        // Выводим результат в YAML формате, предварительно клонируя map, чтобы избежать ссылок
        System.out.println(yaml.dump(cloneMap(output)));
    }

    /**
     * Парсит строковое значение в соответствующий Java-объект.
     * @param s - входная строка
     * @param constants - уже известные константы (для подстановки через [имя])
     * @return Integer, Double, Boolean, String, Map<String, Object> или ссылка на существующую константу
     */
    private static Object parseValue(String s, Map<String, Object> constants) {
        s = s.trim();
        if (s.isEmpty()) return null;

        // Словарь (начинается с { и заканчивается })
        if (s.startsWith("{") && s.endsWith("}")) {
            return parseDict(s.substring(1, s.length() - 1), constants);
        }

        // Ссылка на другую константу в формате [имя_константы]
        if (s.startsWith("[") && s.endsWith("]")) {
            String cname = s.substring(1, s.length() - 1).trim();
            if (!constants.containsKey(cname))
                throw new RuntimeException("Неизвестная константа: " + cname);
            return constants.get(cname); // Возвращаем уже вычисленное значение
        }

        // Целое число
        if (s.matches("[+-]?\\d+")) return Integer.parseInt(s);

        // Дробное число (включая научную нотацию)
        if (s.matches("[+-]?\\d*\\.\\d+([eE][+-]?\\d+)?")) return Double.parseDouble(s);

        // Булево значение
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")) return Boolean.parseBoolean(s);

        // Строка в двойных кавычках
        if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);

        // Простая строка (без кавычек)
        return s;
    }

    /**
     * Парсит строку словаря вида "ключ1 = значение1, ключ2 = значение2, ..."
     * Поддерживает вложенные словари и многострочные значения.
     * @param text - строка с содержимым словаря (без внешних {})
     * @param constants - уже известные константы
     * @return Map<String, Object>
     */
    private static Map<String, Object> parseDict(String text, Map<String, Object> constants) {
        Map<String, Object> map = new LinkedHashMap<>();
        int level = 0; // Уровень вложенности для обработки вложенных словарей
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') level++;   // Увеличиваем уровень при встрече {
            if (c == '}') level--;   // Уменьшаем уровень при встрече }

            // Разделитель записей — запятая или перенос строки, но только на верхнем уровне
            if ((c == '\n' || c == ',') && level == 0) {
                addEntry(map, buf.toString(), constants);
                buf.setLength(0); // Очищаем буфер для следующей записи
            } else {
                buf.append(c);
            }
        }
        // Добавляем последнюю запись (после цикла)
        addEntry(map, buf.toString(), constants);
        return map;
    }

    /**
     * Добавляет одну пару ключ=значение в словарь.
     * @param map - целевой словарь
     * @param line - строка вида "ключ = значение"
     * @param constants - известные константы для подстановки
     */
    private static void addEntry(Map<String, Object> map, String line, Map<String, Object> constants) {
        line = line.trim();
        if (line.isEmpty()) return;
        int eq = line.indexOf('=');
        if (eq == -1) throw new RuntimeException("Ошибка словаря: " + line);
        String key = line.substring(0, eq).trim();
        String val = line.substring(eq + 1).trim();
        map.put(key, parseValue(val, constants));
    }

    /**
     * Рекурсивно клонирует Map, чтобы избежать ссылок в YAML-выводе.
     * SnakeYAML по умолчанию может выводить &id и *id ссылки, если одна и та же Map встречается несколько раз.
     * @param original - исходная Map
     * @return новая Map с теми же данными, но другими объектами
     */
    private static Map<String, Object> cloneMap(Map<String, Object> original) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                // Рекурсивное клонирование вложенных словарей
                value = cloneMap((Map<String, Object>) value);
            }
            copy.put(entry.getKey(), value);
        }
        return copy;
    }
}