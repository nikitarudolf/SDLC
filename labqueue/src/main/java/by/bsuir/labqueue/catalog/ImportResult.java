package by.bsuir.labqueue.catalog;

import java.util.List;

/** Итог импорта списка группы: сколько создано, кто пропущен и какие строки с ошибками. */
public record ImportResult(String group, int created, List<String> skipped, List<String> errors) {
}
