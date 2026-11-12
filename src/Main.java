import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

enum DataType {
    INT, DOUBLE, STRING, DATE, BOOLEAN;
    Object parse(String s) 
    {
        if (s == null || s.trim().isEmpty()) return null;
        switch (this) {
            case INT: return Integer.parseInt(s);
            case DOUBLE: return Double.parseDouble(s);
            case STRING: return s;
            case DATE: return LocalDate.parse(s);
            case BOOLEAN: return Boolean.parseBoolean(s);}
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

    String primaryKey() {
        return columns.get(0).name; // первый столбец - это PK
    }

    void insert(List<Object> values) throws IllegalArgumentException {
        if (values.size() != columns.size()) {
            throw new IllegalArgumentException("кол-во значений не совпадает с числом столбцов");
        }
        Object pk = values.get(0);
        for (Row r : rows) {
            Object cur = r.values.get(0);
            if (cur == null ? pk == null : cur.equals(pk)) {
                throw new IllegalArgumentException("запись с таким PK уже есть: " + pk);
            }
        }
        addRow(values);
    }
    List<Row> selectAll() {
        return rows;
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
            w[i] = columns.get(i).name.length();}
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
enum QueryType { INSERT, SELECT }

class Query {
    QueryType type;
    String tableName;
    List<Object> values;

    Query(QueryType type, String tableName, List<Object> values) {
        this.type = type;
        this.tableName = tableName;
        this.values = values;
    }
    
}

class QueryParser
{
    Table t;
    QueryParser(Table t) {
        this.t = t;
    }
    Query parse(String query) {
        if (query == null) return null;
        String s = query.trim();
        if (s.isEmpty()) return null;
        Matcher m = Pattern.compile("(?i)^INSERT\\s+INTO\\s+(\\w+)\\s+VALUES\\s*\\((.*)\\)\\s*;?\\s*$").matcher(s);
        if (m.matches()) {
            List<String> raw = splitValues(m.group(2));
            List<Object> vals = new ArrayList<>();
            for (int j = 0; j < raw.size(); j++) {
                String sv = unquote(raw.get(j));
                if (j < t.columns.size()) vals.add(t.columns.get(j).type.parse(sv));
                else vals.add(sv);
            }
            return new Query(QueryType.INSERT, m.group(1), vals);
        }

        Matcher q = Pattern.compile("(?i)^SELECT\\s+\\*\\s+FROM\\s+(\\w+)\\s*;?\\s*$").matcher(s);
        if (q.matches()) return new Query(QueryType.SELECT, q.group(1), null);
        return null;
   
    }

    static List<String> splitValues(String s) {
        List<String> res = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') inQ = !inQ;
            if (ch == ',' && !inQ) { 
                res.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
            
        }
        res.add(cur.toString().trim());
        return res;
    }

    static String unquote(String s) {
        if (s.length() >= 2 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}

public class Main {

    public static void main(String[] args) throws IOException {
        Path file = Paths.get("data/cars.txt");
        Files.createDirectories(Paths.get("data"));

        Table t;
        if (Files.exists(file)) {
            t = Table.load(file);
        } else {
            System.out.println("файла нет, создаю новую таблицу");
            t = new Table("cars");
            t.addColumn("id", DataType.INT);
            t.addColumn("brand", DataType.STRING);
            t.addColumn("price", DataType.DOUBLE);
            t.addColumn("used", DataType.BOOLEAN);
            t.addColumn("year", DataType.DATE);
        }
        System.out.println("загружено записей: " + t.rows.size());

        QueryParser qp = new QueryParser(t);
        Scanner sc = new Scanner(System.in);
        while(true){
            System.out.print("SQL> ");
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                System.out.println("пока");
                break;
            }
            try {
                Query ans = qp.parse(line);
                if (ans == null) {
                    System.out.println("не понял команду, попробуй ещё");
                    continue;
                }
                if (ans.type == QueryType.INSERT) {
                    t.insert(ans.values);
                    System.out.println("запись добавлена");
                } else {
                    List<Row> res = t.selectAll();
                    t.print();
                }
                t.save(file);
            } catch (Exception e) {
                System.out.println("ошибка: " + e.getMessage());
            }
       
        }
    }
    
}
