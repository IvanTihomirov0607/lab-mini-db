import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

enum DataType {
    INT, DOUBLE, STRING, DATE, BOOLEAN;
    Object parse(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        switch (this) {
            case INT: return Integer.parseInt(s);
            case DOUBLE: return Double.parseDouble(s);
            case STRING: return s;
            case DATE: return LocalDate.parse(s);
            case BOOLEAN: return Boolean.parseBoolean(s);
        }

        return null; // сюда попасть нельзя, но без return не компилится
    }

}
class Column {
    String name;
    DataType type;

    Column(String name, DataType type) {
        this.name = name;
        this.type = type;

    }
}

class Row {
    List<Object> values;

    // копируем, чтобы снаружи не менялось
    Row(List<Object> vals){
        this.values = new ArrayList<>(vals);
    }
}
class Table {
    String name;
    List<Column> columns = new ArrayList<>();
    List<Row> rows = new ArrayList<>();

    Table(String name) {
        this.name = name;
    }

    void addColumn(String colName, DataType type) {
        columns.add(new Column(colName, type));
    }
    void addRow(List<Object> vals) {
        if(vals.size() != columns.size()){
            throw new IllegalArgumentException("кол-во значений не совпадает с числом столбцов");
        }
        rows.add(new Row(vals));
    }
    int columnIndex(String colName) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name.equals(colName)) return i;
        }
        return -1;
    }

    void save(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        for(int j=0;j<columns.size();j++){
            Column c = columns.get(j);
            if (j > 0) sb.append('|');
            sb.append(c.name).append(':').append(c.type);
        }
        sb.append('\n');
        for (Row r : rows) {
            for(int v=0;v<r.values.size();v++){
                if (v > 0) sb.append('|');
                Object o = r.values.get(v);
                // null уходит как пустая строка
                String res = o == null ? "" : o.toString();
                sb.append(res);
            }
            sb.append('\n');
        }
        Files.write(p, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static Table load(Path p) throws IOException {
        List<String> lines = Files.readAllLines(p);
        if (lines.isEmpty()) throw new IOException("файл пустой");

        //имя таблицы нигде не хранится, возьмём из имени файла
        String fn = p.getFileName().toString();
        String tName = fn.endsWith(".txt") ? fn.substring(0, fn.length() - 4) : fn;
        Table t = new Table(tName);

        // первая строка - схема
        String[] cols = lines.get(0).split("\\|");
        for (String c : cols) {
            String[] parts = c.split(":");
            t.addColumn(parts[0], DataType.valueOf(parts[1]));
        }

        for (int u = 1; u < lines.size(); u++) {
            if(lines.get(u).isEmpty()) continue;
            // -1 чтобы пустые значения тоже попали
            String[] parts = lines.get(u).split("\\|", -1);
            List<Object> vals = new ArrayList<>();
            for (int j = 0; j < t.columns.size(); j++) {
                vals.add(t.columns.get(j).type.parse(parts[j]));
            }
            t.addRow(vals);
        }
        return t;
    }

    void print() {
        int n = columns.size();
        int[] w = new int[n];
        for (int i = 0; i < n; i++) {
            w[i] = columns.get(i).name.length();
        }
        for (Row r : rows) {
            for (int j = 0; j < n; j++) {
                String s = r.values.get(j) == null ? "null" : String.valueOf(r.values.get(j));
                if (s.length() > w[j]) w[j] = s.length();
            }

        }

        StringBuilder h = new StringBuilder();
        for (int i = 0; i < n; i++) {
            h.append(pad(columns.get(i).name, w[i]));
            if (i < n - 1) h.append(" | ");
        }
        System.out.println(h);
        for (int k = 0; k < h.length(); k++) System.out.print('-');
        System.out.println();
        for (Row r : rows) {
            StringBuilder q = new StringBuilder();
            for (int j = 0; j < n; j++) {
                String s = r.values.get(j) == null ? "null" : String.valueOf(r.values.get(j));
                q.append(pad(s, w[j]));
                if (j < n - 1) q.append(" | ");
            }
            System.out.println(q);
        }

    }

    static String pad(String s, int w) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < w) b.append(' ');
        return b.toString();
    }
}
public class Main {
    public static void main(String[] args) throws IOException {
        Table cars = new Table("cars");
        cars.addColumn("id", DataType.INT);
        cars.addColumn("brand", DataType.STRING);
        cars.addColumn("price", DataType.DOUBLE);
        cars.addColumn("used", DataType.BOOLEAN);
        cars.addColumn("year", DataType.DATE);

        cars.addRow(Arrays.<Object>asList(1, "Toyota", 25000.0, false, LocalDate.of(2020, 1, 1)));
        cars.addRow(Arrays.<Object>asList(2, "BMW", 40000.0, true, LocalDate.of(2018, 5, 15)));

        cars.print();
        // System.out.println("проверка");
        Files.createDirectories(Paths.get("data"));
        cars.save(Paths.get("data/cars.txt"));

        Table loaded = Table.load(Paths.get("data/cars.txt"));
        System.out.println();
        loaded.print();

    }
}

