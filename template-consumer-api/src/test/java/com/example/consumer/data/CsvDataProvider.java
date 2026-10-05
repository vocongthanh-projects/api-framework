package com.example.consumer.data;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.testng.annotations.DataProvider;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads named CSV rows from test resources for TestNG data-driven tests. */
public final class CsvDataProvider {
    private CsvDataProvider() { }

    @DataProvider(name = "sampleItems")
    public static Object[][] sampleItems() throws IOException {
        var input = CsvDataProvider.class.getResourceAsStream("/testdata/sample-items.csv");
        if (input == null) {
            throw new IllegalStateException("Missing test resource: /testdata/sample-items.csv");
        }

        List<Object[]> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            for (CSVRecord record : CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .get()
                    .parse(reader)) {
                rows.add(new Object[]{
                        record.get("case_name"),
                        record.get("item_name"),
                        Integer.parseInt(record.get("expected_status"))
                });
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("CSV contains no test cases: /testdata/sample-items.csv");
        }
        return rows.toArray(Object[][]::new);
    }
}
