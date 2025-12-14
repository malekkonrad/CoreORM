package pl.edu.agh.dp.core.util;

public class StringUtils {
    public static String convertCamelCaseToSnake(String input) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i == 0) {
                    result.append(Character.toLowerCase(c));
                } else {
                    result.append("_").append(Character.toLowerCase(c));
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
