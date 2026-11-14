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

    List<Row> selectWhere(Condition c) {
        List<Row> res = new ArrayList<>();
        for (Row r : rows) {
            if (c == null || c.matches(r, this)) res.add(r);
        }
        return res;
    }
    void printWhere(Condition c, List<String> cols) {
        List<Row> res = selectWhere(c);
        List<Integer> idx = new ArrayList<>();
        if (cols == null) {
            for (int i = 0; i < columns.size(); i++) idx.add(i);
        } else {
            for (String cn : cols) {
                int k = columnIndex(cn);
                if (k < 0) throw new IllegalArgumentException("нет такого столбца: " + cn);
                idx.add(k);
            }
        }
        int n = idx.size();
        int[] w = new int[n];
        for (int a = 0; a < n; a++) w[a] = columns.get(idx.get(a)).name.length();
        for (Row r : res) {
            for (int j = 0; j < n; j++) {
                Object v = r.values.get(idx.get(j));
                String s = v == null ? "null" : String.valueOf(v);
                if (s.length() > w[j]) w[j] = s.length();
            }
        }
        StringBuilder h = new StringBuilder();
        for (int i = 0; i < n; i++) {
            h.append(pad(columns.get(idx.get(i)).name, w[i]));
            if (i < n - 1) h.append(" | ");
        }
        System.out.println(h);
        for (int k = 0; k < h.length(); k++) System.out.print('-');
        System.out.println();
        for (Row r : res) {
            StringBuilder q = new StringBuilder();
            for (int j = 0; j < n; j++) {
                Object v = r.values.get(idx.get(j));
                String s = v == null ? "null" : String.valueOf(v);
                q.append(pad(s, w[j]));
                if (j < n - 1) q.append(" | ");
            }
            System.out.println(q);
        }
    }
    int update(Condition c, String col, Object val) {
        int k = columnIndex(col);
        if(k < 0) throw new IllegalArgumentException("нет такого столбца: " + col);
        int n = 0;
        for (Row r : rows) {
            if (c == null || c.matches(r, this)) {
                r.values.set(k, val);
                n++;
            }
        }
        return n;
    }
    int deleteWhere(Condition c) {
        int d = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (c == null || c.matches(rows.get(i), this)) {
                rows.remove(i);
                d++;
            }
        }
        return d;
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
        printWhere(null, null);
    }

    static String pad(String s, int w) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < w) b.append(' ');
        return b.toString();
    }


}
enum QueryType { INSERT, SELECT, UPDATE, DELETE }

class Query {
    QueryType type;
    String tableName;
    List<Object> values;
    List<String> selectCols;
    Condition where;
    String setCol;
    Object setValue;

    Query(QueryType type, String tableName) {
        this.type = type;
        this.tableName = tableName;
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
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();

        String up = s.toUpperCase();
        if (up.startsWith("INSERT")) return parseInsert(s);

        Condition cond = null;
        String body = s;
        Matcher wm = Pattern.compile("(?i)\\bWHERE\\b").matcher(s);
        if (wm.find()) {
            body = s.substring(0, wm.start()).trim();
            cond = parseCondition(s.substring(wm.end()).trim());
        }
        if (up.startsWith("SELECT")) return parseSelect(body, cond);
        if (up.startsWith("UPDATE")) return parseUpdate(body, cond);
        if (up.startsWith("DELETE")) return parseDelete(body, cond);
        return null;
    }

    Query parseInsert(String s) {
        Matcher m = Pattern.compile("(?i)^INSERT\\s+INTO\\s+(\\w+)\\s*(?:\\(([^)]*)\\)\\s*)?VALUES\\s*\\((.*)\\)$").matcher(s);
        if (!m.matches()) return null;
        List<String> raw = splitValues(m.group(3));
        List<Object> vals = new ArrayList<>();
        if (m.group(2) == null) {
            for (int j = 0; j < raw.size(); j++) {
                String sv = unquote(raw.get(j));
                if (j < t.columns.size()) vals.add(t.columns.get(j).type.parse(sv));
                else vals.add(sv);
            }
        } else {
            List<String> colNames = splitValues(m.group(2));
            if (colNames.size() != raw.size()) throw new IllegalArgumentException("кол-во значений не совпадает с числом столбцов");
            for (int i = 0; i < t.columns.size(); i++) vals.add(null);
            for (int j = 0; j < colNames.size(); j++) {
                String cn = colNames.get(j).trim();
                int k = t.columnIndex(cn);
                if (k < 0) throw new IllegalArgumentException("нет такого столбца: " + cn);
                vals.set(k, t.columns.get(k).type.parse(unquote(raw.get(j))));
            }
        }
        Query q = new Query(QueryType.INSERT, m.group(1));
        q.values = vals;
        return q;
    }
    Query parseSelect(String body, Condition cond) {
        Matcher m = Pattern.compile("(?i)^SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)$").matcher(body);
        if (!m.matches()) return null;
        List<String> cols = null;
        String cp = m.group(1).trim();
        if (!cp.equals("*")) {
            cols = new ArrayList<>();
            for (String c : splitValues(cp)) {
                String cn = c.trim();
                if (t.columnIndex(cn) < 0) throw new IllegalArgumentException("нет такого столбца: " + cn);
                cols.add(cn);
            }
        }
        Query q = new Query(QueryType.SELECT, m.group(2));
        q.selectCols = cols;
        q.where = cond;
        return q;
    }
    Query parseUpdate(String body, Condition cond) {
        Matcher m = Pattern.compile("(?i)^UPDATE\\s+(\\w+)\\s+SET\\s+(\\w+)\\s*=\\s*(.+)$").matcher(body);
        if (!m.matches()) return null;
        String cn = m.group(2);
        int k = t.columnIndex(cn);
        if (k < 0) throw new IllegalArgumentException("нет такого столбца: " + cn);
        Query q = new Query(QueryType.UPDATE, m.group(1));
        q.setCol = cn;
        q.setValue = t.columns.get(k).type.parse(unquote(m.group(3).trim()));
        q.where = cond;
        return q;
    }
    Query parseDelete(String body, Condition cond) {
        Matcher m = Pattern.compile("(?i)^DELETE\\s+FROM\\s+(\\w+)$").matcher(body);
        if (!m.matches()) return null;
        Query q = new Query(QueryType.DELETE, m.group(1));
        q.where = cond;
        return q;
    }
    
    Condition parseCondition(String s) {
        Matcher m = Pattern.compile("^(\\w+)\\s*(>=|<=|!=|=|>|<)\\s*(.+)$").matcher(s.trim());
        if (!m.matches()) throw new IllegalArgumentException("ошибка в условии");
        String cn = m.group(1);
        int k = t.columnIndex(cn);
        if (k < 0) throw new IllegalArgumentException("нет такого столбца: " + cn);
        Object v;
        try {
            v = t.columns.get(k).type.parse(unquote(m.group(3).trim()));
        } catch (Exception e) {
            throw new IllegalArgumentException("ошибка в условии: " + m.group(3).trim());
        }
        return new Condition(cn, m.group(2), v);
    }

    static List<String> splitValues(String s) {
        List<String> res = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') inQ = !inQ;
            if (ch == ',' && !inQ) { // запятая внутри кавычек не считается
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

class Condition {
    String column;
    String op;
    Object value;

    Condition(String column, String op, Object value) {
        this.column = column;
        this.op = op;
        this.value = value;
    }
    boolean matches(Row r, Table t) {
        int idx = t.columnIndex(column);
        Object x = r.values.get(idx);
        if (x == null || value == null) return false; // null ни с чем не сравниваем
        if (op.equals("=")) return x.equals(value);
        if (op.equals("!=")) return !x.equals(value);
        if (!(x instanceof Comparable)) return false;
        int cmp = ((Comparable) x).compareTo(value);
        if (op.equals(">")) return cmp > 0;
        if (op.equals("<")) return cmp < 0;
        if (op.equals(">=")) return cmp >= 0;
        if (op.equals("<=")) return cmp <= 0;
        return false;
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
                switch (ans.type) {
                    case INSERT:
                        t.insert(ans.values);
                        System.out.println("запись добавлена");
                        t.save(file);
                        break;
                    case SELECT:
                        t.printWhere(ans.where, ans.selectCols);
                        break;
                    case UPDATE:
                        int n = t.update(ans.where, ans.setCol, ans.setValue);
                        System.out.println("обновлено: " + n);
                        t.save(file);
                        break;
                    case DELETE:
                        int d = t.deleteWhere(ans.where);
                        System.out.println("удалено: " + d);
                        t.save(file);
                        break;
                }
                // System.out.println("проверка");
            } catch (Exception e) {
                System.out.println("ошибка: " + e.getMessage());
            }
        }

    }
}
