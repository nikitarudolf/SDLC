package by.bsuir.labqueue.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Разбор списка группы из .csv: строка «ФИО;e-mail» (разделитель «;» или «,»).
 * Пустые строки пропускаются, первая строка без «@» считается заголовком.
 */
public final class StudentCsvParser {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private StudentCsvParser() {
    }

    public static Result parse(String content) {
        List<Row> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (i == 0 && !line.contains("@")) {
                continue;
            }
            String[] parts = line.split("[;,]");
            if (parts.length != 2) {
                errors.add("Строка " + lineNo + ": нужно два поля «ФИО;e-mail»");
                continue;
            }
            String fullName = parts[0].trim().replaceAll("^\"|\"$", "");
            String email = parts[1].trim().replaceAll("^\"|\"$", "");
            if (fullName.isEmpty()) {
                errors.add("Строка " + lineNo + ": не указано ФИО");
            } else if (!EMAIL.matcher(email).matches()) {
                errors.add("Строка " + lineNo + ": некорректный e-mail «" + email + "»");
            } else {
                rows.add(new Row(lineNo, fullName, email));
            }
        }
        return new Result(rows, errors);
    }

    public record Row(int line, String fullName, String email) {
    }

    public record Result(List<Row> rows, List<String> errors) {
    }
}
