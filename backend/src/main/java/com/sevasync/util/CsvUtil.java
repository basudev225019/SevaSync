package com.sevasync.util;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Minimal RFC-4180 style CSV helper (one record per line). */
public final class CsvUtil {
    private CsvUtil() {
    }

    public static String join(String[] fields) {
        StringJoiner sj = new StringJoiner(",");
        for (String f : fields) {
            sj.add(escape(f));
        }
        return sj.toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        // records are line based, so line breaks are flattened
        String s = value.replace('\r', ' ').replace('\n', ' ');
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    public static String[] split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
