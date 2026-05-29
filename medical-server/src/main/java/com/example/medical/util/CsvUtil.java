package com.example.medical.util;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CsvUtil {

    private CsvUtil() {}

    public static String toCsv(List<String> headers, List<List<String>> rows) {
        StringWriter sw = new StringWriter();
        sw.write(headers.stream().map(CsvUtil::escapeCsv).collect(Collectors.joining(",")));
        sw.write("\n");
        for (List<String> row : rows) {
            sw.write(row.stream().map(CsvUtil::escapeCsv).collect(Collectors.joining(",")));
            sw.write("\n");
        }
        return sw.toString();
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
