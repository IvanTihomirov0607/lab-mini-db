import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
class Database {
    Map<String, Table> tables = new LinkedHashMap<>();
    Path folder; // папка data/

    Database(Path folder) {
        this.folder = folder;
    }

    void add(Table t) {
        tables.put(t.name, t);
    }

    Table get(String name) {
        return tables.get(name);
    }

    void drop(String name) {
        tables.remove(name);
        try {
            Files.deleteIfExists(folder.resolve(name + ".txt"));
        } catch (IOException e) {
            System.out.println("файл не удалился: " + e.getMessage());
        }
    }

    void saveAll() throws IOException {
        Files.createDirectories(folder);
        for (Table t : tables.values()) {
            t.save(folder.resolve(t.name + ".txt"));
        }
    }

    static Database loadAll(Path folder) throws IOException {
        Database db = new Database(folder);
        if (!Files.exists(folder))
        {
            Files.createDirectories(folder);
            return db;
        }
        File[] files = folder.toFile().listFiles();
        if (files == null) return db;
        Arrays.sort(files); // чтобы порядок таблиц не прыгал
        for (File f : files) {
            String n = f.getName();
            if (n.equals(".gitkeep")) continue;
            if (!n.endsWith(".txt")) continue;
            db.add(Table.load(f.toPath()));
        }
        return db;
    }
}

enum QueryType { INSERT, SELECT, UPDATE, DELETE, CREATE_TABLE, DROP_TABLE }

class Query {
    QueryType type;
    String tableName;
    List<Object> values;
    List<String> selectCols;
    List<Column> createCols;
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
    Database db;
    QueryParser(Database db) {
        this.db = db;
    }
    Query parse(String query) {
        if (query == null) return null;
        String s = query.trim();
        if (s.isEmpty()) return null;
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();

        String up = s.toUpperCase();
        if (up.startsWith("INSERT")) return parseInsert(s);
        if (up.startsWith("CREATE")) return parseCreate(s);
        if (up.startsWith("DROP")) return parseDrop(s);

        String body = s;
        String whereStr = null;
        Matcher wm = Pattern.compile("(?i)\\bWHERE\\b").matcher(s);
        if (wm.find()) {
            body = s.substring(0, wm.start()).trim();
            whereStr = s.substring(wm.end()).trim();
        }
        if (up.startsWith("SELECT")) return parseSelect(body, whereStr);
        if (up.startsWith("UPDATE")) return parseUpdate(body, whereStr);
        if (up.startsWith("DELETE")) return parseDelete(body, whereStr);
        return null;
    }
    Table getTable(String name) {
        Table x = db.get(name);
        if (x == null) throw new IllegalArgumentException("нет такой таблицы: " + name);
        return x;
    }

    Query parseCreate(String s) {
        Matcher m = Pattern.compile("(?i)^CREATE\\s+TABLE\\s+(\\w+)\\s*\\(([^)]+)\\)$").matcher(s);
        if (!m.matches()) return null;
        String name = m.group(1);
        if (db.get(name) != null) throw new IllegalArgumentException("таблица уже есть: " + name);
        List<Column> cs = new ArrayList<>();
        for (String part : m.group(2).split(",")) {
            String[] w = part.trim().split("\\s+");
            if (w.length != 2) throw new IllegalArgumentException("плохое описание столбца: " + part.trim());
            DataType dt;
            try {
                dt = DataType.valueOf(w[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("неизвестный тип: " + w[1]);
            }
            cs.add(new Column(w[0], dt));
        }
        Query q = new Query(QueryType.CREATE_TABLE, name);
        q.createCols = cs;
        return q;
    }

    Query parseDrop(String s) {
        Matcher m = Pattern.compile("(?i)^DROP\\s+TABLE\\s+(\\w+)$").matcher(s);
        if (!m.matches()) return null;
        String name = m.group(1);
        if (db.get(name) == null) throw new IllegalArgumentException("нет такой таблицы: " + name);
        return new Query(QueryType.DROP_TABLE, name);
    }

    Query parseInsert(String s) {
        Matcher m = Pattern.compile("(?i)^INSERT\\s+INTO\\s+(\\w+)\\s*(?:\\(([^)]*)\\)\\s*)?VALUES\\s*\\((.*)\\)$").matcher(s);
        if (!m.matches()) return null;
        Table t = getTable(m.group(1));
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
    Query parseSelect(String body, String whereStr) {
        Matcher m = Pattern.compile("(?i)^SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)$").matcher(body);
        if (!m.matches()) return null;
        Table t = getTable(m.group(2));
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
        q.where = parseCondition(whereStr, t);
        return q;
    }
    Query parseUpdate(String body, String whereStr) {
        Matcher m = Pattern.compile("(?i)^UPDATE\\s+(\\w+)\\s+SET\\s+(\\w+)\\s*=\\s*(.+)$").matcher(body);
        if (!m.matches()) return null;
        Table t = getTable(m.group(1));
        String cn = m.group(2);
        int k = t.columnIndex(cn);
        if (k < 0) throw new IllegalArgumentException("нет такого столбца: " + cn);
        Query q = new Query(QueryType.UPDATE, m.group(1));
        q.setCol = cn;
        q.setValue = t.columns.get(k).type.parse(unquote(m.group(3).trim()));
        q.where = parseCondition(whereStr, t);
        return q;
    }
    Query parseDelete(String body, String whereStr) {
        Matcher m = Pattern.compile("(?i)^DELETE\\s+FROM\\s+(\\w+)$").matcher(body);
        if (!m.matches()) return null;
        Table t = getTable(m.group(1));
        Query q = new Query(QueryType.DELETE, m.group(1));
        q.where = parseCondition(whereStr, t);
        return q;
    }

    Condition parseCondition(String s, Table t) {
        if (s == null) return null;
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
        Path folder = Paths.get("data");
        Database db = Database.loadAll(folder);

        if (db.tables.isEmpty()) {
            System.out.println("данных нет, создаю таблицы");
            Table cars = new Table("cars");
            cars.addColumn("id", DataType.INT);
            cars.addColumn("brand", DataType.STRING);
            cars.addColumn("price", DataType.DOUBLE);
            cars.addColumn("used", DataType.BOOLEAN);
            cars.addColumn("year", DataType.DATE);
            db.add(cars);

            // car_id у owners ссылается на id в cars, просто храним число
            Table owners = new Table("owners");
            owners.addColumn("id", DataType.INT);
            owners.addColumn("name", DataType.STRING);
            owners.addColumn("car_id", DataType.INT);
            db.add(owners);

            db.saveAll();
        }
        System.out.println("загружено таблиц: " + db.tables.size());

        QueryParser qp = new QueryParser(db);
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
                Table t = db.get(ans.tableName);
                switch (ans.type) {
                    case INSERT:
                        t.insert(ans.values);
                        System.out.println("запись добавлена");
                        db.saveAll();
                        break;
                    case SELECT:
                        t.printWhere(ans.where, ans.selectCols);
                        break;
                    case UPDATE:
                        int n = t.update(ans.where, ans.setCol, ans.setValue);
                        System.out.println("обновлено: " + n);
                        db.saveAll();
                        break;
                    case DELETE:
                        int d = t.deleteWhere(ans.where);
                        System.out.println("удалено: " + d);
                        db.saveAll();
                        break;
                    case CREATE_TABLE:
                        Table nt = new Table(ans.tableName);
                        for (Column c : ans.createCols) nt.addColumn(c.name, c.type);
                        db.add(nt);
                        System.out.println("таблица создана");
                        db.saveAll();
                        break;
                    case DROP_TABLE:
                        db.drop(ans.tableName);
                        System.out.println("таблица удалена");
                        break;
                }
                // System.out.println("проверка");
            } catch (Exception e) {
                System.out.println("ошибка: " + e.getMessage());
            }
        }

    }
}
