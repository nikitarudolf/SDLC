package by.bsuir.labqueue.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudentCsvParserTest {

    @Test
    void parsesHeaderSeparatorsAndErrors() {
        String csv = "﻿ФИО;e-mail\n"
                + "Иванов Иван Иванович;ivanov@mail.by\n"
                + "\n"
                + "Петров Пётр,petrov@mail.by\n"
                + "Сидоров;не-почта\n"
                + "одно поле\n";
        StudentCsvParser.Result result = StudentCsvParser.parse(csv);
        assertThat(result.rows()).extracting(StudentCsvParser.Row::email)
                .containsExactly("ivanov@mail.by", "petrov@mail.by");
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0)).startsWith("Строка 5");
    }
}
