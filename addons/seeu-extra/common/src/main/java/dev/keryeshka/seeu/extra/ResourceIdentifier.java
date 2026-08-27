package dev.keryeshka.seeu.extra;

public final class ResourceIdentifier {
    private ResourceIdentifier() {
    }

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }

        int separator = value.indexOf(':');
        return separator > 0
                && separator == value.lastIndexOf(':')
                && separator < value.length() - 1
                && isValidNamespace(value.substring(0, separator))
                && isValidPath(value.substring(separator + 1));
    }

    public static boolean isValidNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return false;
        }
        for (int index = 0; index < namespace.length(); index++) {
            char character = namespace.charAt(index);
            if (!isLowercaseLetter(character)
                    && !isDigit(character)
                    && character != '_'
                    && character != '-'
                    && character != '.') {
                return false;
            }
        }
        return true;
    }

    public static String namespace(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid resource identifier: " + value);
        }
        return value.substring(0, value.indexOf(':'));
    }

    private static boolean isValidPath(String path) {
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            if (!isLowercaseLetter(character)
                    && !isDigit(character)
                    && character != '_'
                    && character != '-'
                    && character != '.'
                    && character != '/') {
                return false;
            }
        }
        return true;
    }

    private static boolean isLowercaseLetter(char character) {
        return character >= 'a' && character <= 'z';
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }
}
