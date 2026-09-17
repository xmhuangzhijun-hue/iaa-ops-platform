package com.iaaops.ingestion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * 解析媒体后台导出的 .xlsx。
 *
 * 各家媒体的导出表头不统一，这里按别名认列：认得的列取值，认不得的列忽略。
 * 单行出错只跳过这一行并记下行号与列名，不让整份文件因为一行脏数据全部失败——
 * 但如果一行都没解析成功，那就是格式不对，整份判失败。
 */
@Component
public class WorkbookParser {

    /** 表头别名。键是内部字段，值是各家导出表里可能出现的列名（小写、去空格后比较）。 */
    private static final Map<String, List<String>> HEADERS = new LinkedHashMap<>();

    static {
        HEADERS.put("stat_date", List.of("日期", "时间", "date", "stat_date"));
        HEADERS.put("stat_hour", List.of("小时", "时段", "hour", "stat_hour"));
        HEADERS.put("account", List.of("账户", "账户名称", "广告主", "account"));
        HEADERS.put("campaign", List.of("推广计划", "计划名称", "计划", "campaign"));
        HEADERS.put("cost", List.of("消耗", "花费", "成本", "cost", "spend"));
        HEADERS.put("revenue", List.of("预估收益", "收益", "变现收益", "revenue"));
        HEADERS.put("impressions", List.of("曝光", "曝光量", "展现", "impressions"));
        HEADERS.put("clicks", List.of("点击", "点击量", "clicks"));
        HEADERS.put("launches", List.of("启动数", "启动", "launches"));
        HEADERS.put("callbacks", List.of("回传数", "回传", "callbacks"));
        HEADERS.put("conversions", List.of("转化数", "转化", "conversions"));
    }

    private static final List<String> REQUIRED = List.of("stat_date", "account", "cost");
    /** 错误只留前若干条：前几条足够定位问题，全量堆进任务记录只会把库撑大。 */
    private static final int MAX_ERRORS = 20;

    public record Result(List<ParsedRow> rows, List<Map<String, Object>> errors, boolean fatal) {

        public static Result failed(String message) {
            return new Result(List.of(), List.of(error(0, null, message)), true);
        }
    }

    public Result parse(byte[] content) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                return Result.failed("文件里没有数据表");
            }
            Map<String, Integer> columns = headerIndex(sheet.getRow(sheet.getFirstRowNum()));
            // 报给用户的是表头上的列名（日期 / 账户 / 消耗），不是内部字段名
            List<String> missing = REQUIRED.stream()
                    .filter(key -> !columns.containsKey(key))
                    .map(WorkbookParser::label)
                    .toList();
            if (!missing.isEmpty()) {
                return Result.failed("缺少必需的列：" + String.join("、", missing));
            }
            return readRows(sheet, columns);
        } catch (IOException | RuntimeException exception) {
            // POI 对非 xlsx 会抛各种运行时异常，这里统一成"文件读不了"，不把内部异常透给调用方
            return Result.failed("文件无法解析，请确认是媒体后台导出的 .xlsx");
        }
    }

    private Result readRows(Sheet sheet, Map<String, Integer> columns) {
        List<ParsedRow> rows = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null || isBlank(row, columns)) {
                continue;
            }
            try {
                rows.add(toRow(row, columns));
            } catch (RowError error) {
                if (errors.size() < MAX_ERRORS) {
                    errors.add(error(index + 1, error.column, error.getMessage()));
                }
            }
        }
        if (rows.isEmpty()) {
            errors.add(error(0, null, "没有解析出任何有效数据行"));
            return new Result(rows, errors, true);
        }
        return new Result(rows, errors, false);
    }

    private ParsedRow toRow(Row row, Map<String, Integer> columns) {
        LocalDate date = date(row, columns, "stat_date");
        String account = text(row, columns, "account");
        if (account == null || account.isBlank()) {
            throw new RowError("账户", "账户为空");
        }
        return new ParsedRow(date, hour(row, columns), account.trim(), text(row, columns, "campaign"),
                money(row, columns, "cost"), money(row, columns, "revenue"),
                count(row, columns, "impressions"), count(row, columns, "clicks"),
                count(row, columns, "launches"), count(row, columns, "callbacks"),
                count(row, columns, "conversions"));
    }

    private static Map<String, Integer> headerIndex(Row header) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        if (header == null) {
            return columns;
        }
        for (int cursor = header.getFirstCellNum(); cursor < header.getLastCellNum(); cursor++) {
            final int index = cursor;
            String label = normalize(stringValue(header.getCell(index)));
            if (label.isEmpty()) {
                continue;
            }
            HEADERS.forEach((key, aliases) -> {
                if (!columns.containsKey(key) && aliases.stream().anyMatch(alias -> normalize(alias).equals(label))) {
                    columns.put(key, index);
                }
            });
        }
        return columns;
    }

    private static boolean isBlank(Row row, Map<String, Integer> columns) {
        return columns.values().stream().allMatch(index -> stringValue(row.getCell(index)).isBlank());
    }

    private LocalDate date(Row row, Map<String, Integer> columns, String key) {
        Cell cell = cell(row, columns, key);
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String raw = stringValue(cell).trim();
        if (raw.isEmpty()) {
            throw new RowError("日期", "日期为空");
        }
        try {
            return LocalDate.parse(raw.replace('/', '-').substring(0, Math.min(10, raw.length())));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException exception) {
            throw new RowError("日期", "日期无法识别：" + raw);
        }
    }

    private Short hour(Row row, Map<String, Integer> columns) {
        Cell cell = cell(row, columns, "stat_hour");
        String raw = stringValue(cell).trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            // "13:00" 这种写法取前面的小时
            short value = Short.parseShort(raw.contains(":") ? raw.substring(0, raw.indexOf(':')) : raw);
            if (value < 0 || value > 23) {
                throw new RowError("小时", "小时超出 0-23：" + raw);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new RowError("小时", "小时无法识别：" + raw);
        }
    }

    private BigDecimal money(Row row, Map<String, Integer> columns, String key) {
        String raw = stringValue(cell(row, columns, key)).trim().replace(",", "").replace("¥", "");
        if (raw.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw).setScale(2, java.math.RoundingMode.HALF_EVEN);
        } catch (NumberFormatException exception) {
            throw new RowError(label(key), "金额无法识别：" + raw);
        }
    }

    private long count(Row row, Map<String, Integer> columns, String key) {
        String raw = stringValue(cell(row, columns, key)).trim().replace(",", "");
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return new BigDecimal(raw).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new RowError(label(key), "数量无法识别：" + raw);
        }
    }

    private static String label(String key) {
        return HEADERS.get(key).getFirst();
    }

    private static Cell cell(Row row, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        return index == null ? null : row.getCell(index);
    }

    private static String text(Row row, Map<String, Integer> columns, String key) {
        String value = stringValue(cell(row, columns, key)).trim();
        return value.isEmpty() ? null : value;
    }

    private static String stringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : new BigDecimal(Double.toString(cell.getNumericCellValue())).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    static Map<String, Object> error(int row, String column, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("row", row);
        error.put("column", column);
        error.put("message", message);
        return error;
    }

    /** 行内错误：带上列名，便于在界面上定位。 */
    private static final class RowError extends RuntimeException {

        private final String column;

        private RowError(String column, String message) {
            super(message);
            this.column = column;
        }
    }
}
