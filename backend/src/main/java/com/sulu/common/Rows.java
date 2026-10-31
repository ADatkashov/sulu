package com.sulu.common;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns snake_case SQL result rows into camelCase API maps with ISO-8601 time values. */
public final class Rows {
    private Rows() {}

    public static String camel(String s) {
        StringBuilder b = new StringBuilder();
        boolean up = false;
        for (char c : s.toCharArray()) {
            if (c == '_') up = true; else { b.append(up ? Character.toUpperCase(c) : c); up = false; }
        }
        return b.toString();
    }

    public static Map<String, Object> row(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> {
            Object val = v;
            if (v instanceof Timestamp t) val = t.toInstant();
            else if (v instanceof Date d) val = d.toLocalDate().toString();
            else if (v instanceof Time t) val = t.toLocalTime().toString().substring(0, 5);
            else if (v instanceof java.time.LocalTime lt) val = lt.toString().substring(0, 5);
            out.put(camel(k), val);
        });
        return out;
    }

    public static List<Map<String, Object>> rows(List<Map<String, Object>> in) {
        List<Map<String, Object>> out = new ArrayList<>(in.size());
        for (Map<String, Object> r : in) out.add(row(r));
        return out;
    }
}
