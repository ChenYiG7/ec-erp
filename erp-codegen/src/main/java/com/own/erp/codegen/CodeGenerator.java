package com.own.erp.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : CRUD 脚手架代码生成器(开发工具,不属于任何运行时依赖)。
 *
 *     输入:docs/sql/01_schema_init.sql 中指定表的 DDL(权威建表脚本,免连库、可复现)。
 *     输出:目标业务模块内的 Entity / Mapper / Service / Controller / Response / Query / SaveRequest / ServiceTest 八件套,
 *          风格对齐项目既有写法(plain @Service 整域收口、Result 包装、@Tag/@Operation 中文、
 *          PageQuery 钳制分页、API 模型收口 docs/07 §1:入参 query/Query+command/SaveRequest(CQRS 读写分包) 出参 response/Response、entity 不出 Service 层、
 *          显式 from/toEntity 映射禁反射拷贝、Mockito 单测);
 *          模型可变性分级(docs/07 §1【2026-09-04】):Response/SaveRequest 产 record+@Builder(from 用 builder 命名传参防错位),
 *          Query 保持 class(record 不能继承 PageQuery,继承例外),
 *          Entity 默认 @Data+@Builder+双构造(@Data 供 MP 反射映射与读改写/回填 setter,@Builder 供纯构造装配位,toEntity 产 builder 链);可选打印 sys_menu 菜单注册 SQL。
 *
 *     用法(项目根目录执行):
 *     <pre>
 *     mvn -q -pl erp-codegen compile exec:java \
 *       -Dexec.args="table=inventory module=inventory path=/api/inventory/inventories todoId=7"
 *     </pre>
 *     中文参数(如 nameZh)建议用 -D 系统属性传(exec:java 与 Maven 同 JVM,直接可读):
 *     <pre>
 *     mvn -q -pl erp-codegen compile exec:java -Dtable=inventory -Dmodule=inventory -DnameZh=库存
 *     </pre>
 *
 *     参数:
 *       table      必填,01_schema_init.sql 中的表名
 *       module     必填,目标模块名(= 包名段,如 inventory / warehouse)
 *       path       可选,REST 路径,默认 /api/&lt;module&gt;/&lt;复数kebab&gt;
 *       nameZh     可选,中文组名(@Tag 等),默认取表 COMMENT 去掉括号说明
 *       todoId     可选,在 Service 留 TODO(#编号) 业务规则占位(复杂逻辑必须人工补,docs/07 §1)
 *       menuParent 可选,传了则打印 sys_menu 菜单注册 SQL(人工核对 ID 后执行)
 *       force      可选,true=覆盖已存在文件(默认存在即跳过)
 *       parts      可选,逗号分隔生成件子集(entity/mapper/service/query/response/saveRequest/controller/test),默认全部
 *       readOnly   可选,true=系统写入表只读模式:不产 SaveRequest,Service/Controller/Test 只渲染读侧(写入口留 TODO 槽位),
 *                  与 parts 含 saveRequest 显式冲突时报错退出
 *
 *     守卫:目标模块 pom 缺 web/validation/MP 三件套/test 依赖时报错退出(先补 pom 再生成);
 *          生成器只产骨架,业务规则一律留 TODO(编号),禁无编号裸 TODO(docs/07 §1)。
 *          列缺 COMMENT:标准列(id/created_at/updated_at)自动补默认中文注释;业务列打印警告清单
 *          (实体字段将无释义,应按 add-table 流程补 DDL 后重新生成),不硬失败。
 *
 *     生成物一律带文件头(@author / @Date / @Description=类职责),位于最后一个 import 之后并与类注释合并,
 *     渲染见 fileHeader()——全仓唯一真相源,新建文件与存量文件头格式一致。
 */
public final class CodeGenerator {

    private record Col(String name, String type, String comment) {
    }

    /** 文件头作者默认值(-Dauthor=xxx 覆盖) */
    static final String DEFAULT_AUTHOR = "chenyi";

    /** 本次运行的作者/日期(文件头用);默认 chenyi / 今天 */
    private static String author = DEFAULT_AUTHOR;
    private static String date;

    /**
     * 文件头【全仓唯一真相源】:新建文件与存量文件格式一致的保证(存量已于 2026-09-03 补齐)。
     *
     * 位置:最后一个 import 之后、类声明/类级注解之前,与类注释合并成一块(不另起、不写包名);
     * 无类注释的文件(如 Mapper)@Description 回退填包名;多行描述续行缩进 4 空格(javadoc 块标签惯例)。
     */
    static String fileHeader(String headerAuthor, String headerDate, String fallbackDesc, List<String> descLines) {
        List<String> desc = descLines == null || descLines.isEmpty() ? List.of(fallbackDesc) : descLines;
        StringBuilder sb = new StringBuilder("/**\n");
        sb.append(" * @author : ").append(headerAuthor).append('\n');
        sb.append(" * @Date : ").append(headerDate).append('\n');
        sb.append(" * @Description : ").append(desc.get(0)).append('\n');
        for (int i = 1; i < desc.size(); i++) {
            String d = desc.get(i);
            sb.append(d.isEmpty() ? " *\n" : " *     " + d + "\n");
        }
        return sb.append(" */\n").toString();
    }

    /** 本模块内目标包的文件头:com.own.erp.<module><suffix>;descLines 为空时 @Description 回退填包名 */
    private static String headerOf(String module, String suffix, String... descLines) {
        return fileHeader(author, date, "com.own.erp." + module + suffix, List.of(descLines));
    }

    /** yyyy/M/d(与既有文件头一致,月日不补零) */
    static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/M/d"));
    }

    private static final Pattern TABLE_BLOCK = Pattern.compile(
            "CREATE TABLE IF NOT EXISTS (\\w+)\\s*\\((.*?)\\r?\\n\\)\\s*COMMENT\\s*'([^']*)'\\s*;",
            Pattern.DOTALL);
    private static final Pattern COL_LINE = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z]+\\s*\\([^)]*\\)?|[A-Za-z]+)\\s*(.*)$");
    private static final Pattern COMMENT_EXTRACT = Pattern.compile("COMMENT '([^']*)'");

    /** 生成前检查目标模块 pom 必须携带的依赖(缺了生成物编译不过) */
    private static final String[] REQUIRED_DEPS = {
            "spring-boot-starter-web",
            "spring-boot-starter-validation",
            "mybatis-plus-spring-boot4-starter",
            "mybatis-plus-extension",
            "mybatis-plus-jsqlparser",
            "spring-boot-starter-test",
    };

    public static void main(String[] args) throws IOException {
        Map<String, String> cfg = readConfig(args);
        String table = required(cfg, "table");
        String module = required(cfg, "module");
        boolean force = "true".equalsIgnoreCase(cfg.getOrDefault("force", "false"));
        boolean readOnly = "true".equalsIgnoreCase(cfg.getOrDefault("readOnly", "false"));
        Set<String> parts = resolveParts(cfg, readOnly);
        author = cfg.getOrDefault("author", DEFAULT_AUTHOR);
        date = cfg.getOrDefault("date", today());

        Path root = locateProjectRoot();
        Path schema = root.resolve("docs").resolve("sql").resolve("01_schema_init.sql");
        String ddl = Files.readString(schema);

        Matcher tm = TABLE_BLOCK.matcher(ddl);
        String body = null;
        String tableComment = "";
        while (tm.find()) {
            if (tm.group(1).equals(table)) {
                body = tm.group(2);
                tableComment = tm.group(3);
                break;
            }
        }
        if (body == null) {
            throw fail("01_schema_init.sql 中找不到表:" + table);
        }

        List<Col> cols = parseColumns(body);
        if (cols.isEmpty()) {
            throw fail("表 " + table + " 未解析出任何列,请检查 DDL 格式");
        }
        List<String> noComment = cols.stream()
                .filter(c -> c.comment().isBlank() && defaultColumnComment(c.name()).isEmpty())
                .map(Col::name)
                .toList();
        if (!noComment.isEmpty()) {
            System.out.println("[codegen] [警告] 表 " + table + " 以下列缺 COMMENT,实体字段将无中文释义,"
                    + "请按 add-table 流程补 01_schema_init.sql 后重新生成:" + noComment);
        }

        String nameZh = cfg.containsKey("nameZh") ? cfg.get("nameZh") : defaultNameZh(tableComment);
        String path = cfg.getOrDefault("path", defaultPath(module, table));
        String entity = toPascal(table);
        String var = toCamelLower(entity);
        boolean hasNameField = cols.stream().anyMatch(c -> c.name().equals("name"));
        String todoId = cfg.get("todoId");

        Path moduleDir = root.resolve("erp-" + module);
        if (!Files.isDirectory(moduleDir)) {
            throw fail("模块目录不存在:" + moduleDir);
        }
        checkModulePom(moduleDir.resolve("pom.xml"));

        System.out.println("[codegen] 表=" + table + " 实体=" + entity + " 模块=erp-" + module
                + " 路径=" + path + " force=" + force
                + (readOnly ? " readOnly=true" : "") + " parts=" + parts);

        String pkg = "com.own.erp." + module;
        String pkgDir = "src/main/java/" + pkg.replace('.', '/');
        int written = 0;
        if (parts.contains("entity")) {
            written += writeFile(moduleDir, pkgDir + "/entity/" + entity + ".java",
                    renderEntity(table, tableComment, entity, module, cols), force);
        }
        if (parts.contains("mapper")) {
            written += writeFile(moduleDir, pkgDir + "/mapper/" + entity + "Mapper.java",
                    renderMapper(entity, module), force);
        }
        if (parts.contains("service")) {
            written += writeFile(moduleDir, pkgDir + "/service/" + entity + "Service.java",
                    renderService(entity, var, module, nameZh, table, todoId, readOnly), force);
        }
        if (parts.contains("query")) {
            written += writeFile(moduleDir, pkgDir + "/request/query/" + entity + "Query.java",
                    renderQuery(entity, module, nameZh), force);
        }
        if (parts.contains("response")) {
            written += writeFile(moduleDir, pkgDir + "/response/" + entity + "Response.java",
                    renderResponse(entity, module, nameZh, cols), force);
        }
        if (parts.contains("saveRequest")) {
            written += writeFile(moduleDir, pkgDir + "/request/command/" + entity + "SaveRequest.java",
                    renderSaveRequest(entity, module, nameZh, cols), force);
        }
        if (parts.contains("controller")) {
            written += writeFile(moduleDir, pkgDir + "/controller/" + entity + "Controller.java",
                    renderController(entity, var, module, nameZh, path, readOnly), force);
        }
        if (parts.contains("test")) {
            written += writeFile(moduleDir, "src/test/java/" + pkg.replace('.', '/') + "/service/" + entity + "ServiceTest.java",
                    renderTest(entity, var, module, nameZh, hasNameField, readOnly), force);
        }

        if (cfg.containsKey("menuParent")) {
            printMenuSql(nameZh, module, table, path, cfg.get("menuParent"));
        }
        System.out.println("[codegen] 完成:新写入 " + written + " 个文件"
                + (force ? "" : "(已存在的文件跳过,加 force=true 可覆盖)"));
    }

    // ---- 配置 ----

    private static Map<String, String> readConfig(String[] args) {
        Map<String, String> cfg = new HashMap<>();
        // 1) -Dexec.args="k=v k2=v2" 形式
        String argLine = System.getProperty("exec.args", "");
        for (String token : argLine.split("\\s+")) {
            int i = token.indexOf('=');
            if (i > 0) {
                cfg.put(token.substring(0, i), token.substring(i + 1));
            }
        }
        // 2) -Dk=v 系统属性形式(exec:java 与 Maven 同 JVM);显式 args 优先
        for (String key : new String[]{"table", "module", "path", "nameZh", "todoId", "menuParent", "force", "author", "date", "parts", "readOnly"}) {
            String v = System.getProperty(key);
            if (v != null && !v.isBlank()) {
                cfg.putIfAbsent(key, v);
            }
        }
        return cfg;
    }

    private static String required(Map<String, String> cfg, String key) {
        String v = cfg.get(key);
        if (v == null || v.isBlank()) {
            throw fail("缺少必填参数 " + key + "(用法见 erp-codegen/README.md)");
        }
        return v;
    }

    /** 生成件全集(顺序即默认生成顺序) */
    private static final List<String> PART_KEYS = List.of(
            "entity", "mapper", "service", "query", "response", "saveRequest", "controller", "test");

    /**
     * 解析生成件集合:parts=entity,mapper 取子集(未知件报错);
     * readOnly=true(系统写入表只读)默认剔除 saveRequest,与显式传入的 saveRequest 冲突时报错退出。
     */
    private static Set<String> resolveParts(Map<String, String> cfg, boolean readOnly) {
        Set<String> parts = new LinkedHashSet<>();
        if (cfg.containsKey("parts")) {
            for (String p : cfg.get("parts").split(",")) {
                String k = p.trim();
                if (!PART_KEYS.contains(k)) {
                    throw fail("未知生成件 '" + k + "',可选:" + PART_KEYS);
                }
                parts.add(k);
            }
            if (readOnly && parts.contains("saveRequest")) {
                throw fail("readOnly=true(系统写入表只读)与显式传入的 saveRequest 冲突:从 parts 去除 saveRequest 或去掉 readOnly");
            }
        } else {
            parts.addAll(PART_KEYS);
            if (readOnly) {
                parts.remove("saveRequest");   // 系统写入表只读:默认剔除写侧入参
            }
        }
        return parts;
    }

    /** 从当前目录向上找 docs/sql/01_schema_init.sql,兼容在项目根或模块目录内执行 */
    static Path locateProjectRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("docs").resolve("sql").resolve("01_schema_init.sql");
            if (Files.exists(candidate)) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw fail("找不到 docs/sql/01_schema_init.sql,请在项目内执行");
    }

    // ---- DDL 解析 ----

    private static List<Col> parseColumns(String body) {
        List<Col> cols = new ArrayList<>();
        for (String raw : body.split("\r?\n")) {
            Col col = parseColumn(raw);
            if (col != null) {
                cols.add(col);
            }
        }
        return cols;
    }

    private static Col parseColumn(String raw) {
        String line = raw.trim();
        if (line.endsWith(",")) {
            line = line.substring(0, line.length() - 1);
        }
        if (line.isEmpty() || line.startsWith("--")) {
            return null;
        }
        String upper = line.toUpperCase(Locale.ROOT);
        if (upper.startsWith("PRIMARY KEY") || upper.startsWith("UNIQUE KEY")
                || upper.startsWith("KEY ") || upper.startsWith("INDEX") || upper.startsWith("CONSTRAINT")) {
            return null;
        }
        Matcher m = COL_LINE.matcher(line);
        if (!m.find()) {
            throw fail("无法解析列定义行:" + raw);
        }
        String name = m.group(1);
        String type = m.group(2).replaceAll("\\s+", "");
        String rest = m.group(3) == null ? "" : m.group(3);
        String comment = "";
        Matcher cm = COMMENT_EXTRACT.matcher(rest);
        if (cm.find()) {
            comment = cm.group(1);
        }
        return new Col(name, type, comment);
    }

    /** 标准列的默认中文注释(DDL 未写 COMMENT 时的兜底);业务列不猜,缺了走警告清单 */
    private static String defaultColumnComment(String column) {
        return switch (column) {
            case "id" -> "主键";
            case "created_at" -> "创建时间";
            case "updated_at" -> "更新时间";
            default -> "";
        };
    }

    private static String javaType(String sqlType) {
        String base = sqlType.toUpperCase(Locale.ROOT);
        if (base.startsWith("BIGINT")) {
            return "Long";
        }
        if (base.startsWith("TINYINT") || base.startsWith("SMALLINT") || base.startsWith("MEDIUMINT")
                || base.equals("INT") || base.startsWith("INTEGER") || base.startsWith("BIT") || base.startsWith("BOOL")) {
            return "Integer";
        }
        if (base.startsWith("DECIMAL") || base.startsWith("NUMERIC")) {
            return "BigDecimal";
        }
        if (base.startsWith("FLOAT") || base.startsWith("DOUBLE") || base.equals("REAL")) {
            throw fail("列类型 " + sqlType + " 禁用 float/double(docs/07 §6.1),请改 DECIMAL 后再生成");
        }
        if (base.startsWith("DATETIME") || base.startsWith("TIMESTAMP")) {
            return "LocalDateTime";
        }
        if (base.equals("DATE")) {
            return "LocalDate";
        }
        if (base.startsWith("VARCHAR") || base.startsWith("CHAR") || base.contains("TEXT")
                || base.startsWith("JSON") || base.startsWith("ENUM") || base.startsWith("SET")) {
            return "String";
        }
        throw fail("未识别的列类型 " + sqlType + ",请在 CodeGenerator.java 的 javaType() 扩展映射");
    }

    // ---- 命名 ----

    /** inventory → Inventory;shop_order_item → ShopOrderItem */
    static String toPascal(String table) {
        StringBuilder sb = new StringBuilder();
        for (String part : table.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /** Inventory → inventory */
    static String toCamelLower(String pascal) {
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /** inventory → inventories;warehouse → warehouses;shop_order_item → shop-order-items */
    static String pluralKebab(String table) {
        String[] parts = table.split("_");
        String last = parts[parts.length - 1];
        if (last.endsWith("y") && last.length() > 1 && !isVowel(last.charAt(last.length() - 2))) {
            last = last.substring(0, last.length() - 1) + "ies";
        } else if (last.endsWith("s") || last.endsWith("x") || last.endsWith("z")
                || last.endsWith("ch") || last.endsWith("sh")) {
            last = last + "es";
        } else {
            last = last + "s";
        }
        parts[parts.length - 1] = last;
        return String.join("-", parts);
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }

    /** 表 COMMENT '库存(只能经 inventory_flow 变动)' → 库存 */
    private static String defaultNameZh(String tableComment) {
        int i = tableComment.indexOf('(');
        String s = i > 0 ? tableComment.substring(0, i) : tableComment;
        return s.isBlank() ? "待命名" : s.trim();
    }

    private static String defaultPath(String module, String table) {
        return "/api/" + module + "/" + pluralKebab(table);
    }

    // ---- 守卫 ----

    private static void checkModulePom(Path pom) {
        if (!Files.exists(pom)) {
            throw fail("找不到 " + pom);
        }
        try {
            String pomText = Files.readString(pom);
            List<String> missing = new ArrayList<>();
            for (String dep : REQUIRED_DEPS) {
                if (!pomText.contains("<artifactId>" + dep + "</artifactId>")) {
                    missing.add(dep);
                }
            }
            if (!missing.isEmpty()) {
                throw fail("目标模块 pom 缺少依赖(参照 erp-goods/pom.xml 补齐后再生成):" + missing);
            }
        } catch (IOException e) {
            throw fail("读取 pom 失败:" + pom + " - " + e.getMessage());
        }
    }

    // ---- 模板渲染 ----

    /** 字段/组件公共首部:缩进 + javadoc(DDL 缺 COMMENT 时走标准列兜底注释) */
    private static String chunkHead(Col c, String indent) {
        String comment = c.comment().isBlank() ? defaultColumnComment(c.name()) : c.comment();
        return comment.isBlank() ? indent : indent + "/** " + comment + " */\n" + indent;
    }

    /** entity 字段块:javadoc + @TableId(仅 id)+ @TableLogic(仅 deleted)+ private 类型名(entity 模板用) */
    private static List<String> fieldChunks(List<Col> cols) {
        List<String> chunks = new ArrayList<>();
        for (Col c : cols) {
            String ann = "";
            if (c.name().equals("id")) {
                ann = "@TableId(type = IdType.AUTO)\n    ";
            } else if (c.name().equals("deleted")) {
                // 逻辑删除统一口径(TODO#7):0=正常,删时置主键 id,配合唯一键含 deleted 删后同键可重建
                ann = "@TableLogic(value = \"0\", delval = \"id\")\n    ";
            }
            chunks.add(chunkHead(c, "    ") + ann + "private " + javaType(c.type()) + " " + toCamel(c.name()) + ";");
        }
        return chunks;
    }

    /** record 组件块:javadoc + 裸类型名,组件间逗号由调用方 join(Response/SaveRequest 模板用) */
    private static List<String> componentChunks(List<Col> cols) {
        List<String> chunks = new ArrayList<>();
        for (Col c : cols) {
            chunks.add(chunkHead(c, "        ") + javaType(c.type()) + " " + toCamel(c.name()));
        }
        return chunks;
    }

    /** 字段列表涉及的 java.time/BigDecimal import(空返回空串,调用方自行接续换行) */
    private static String collectImports(List<Col> cols) {
        boolean needBd = cols.stream().anyMatch(c -> javaType(c.type()).equals("BigDecimal"));
        boolean needLd = cols.stream().anyMatch(c -> javaType(c.type()).equals("LocalDate"));
        boolean needLdt = cols.stream().anyMatch(c -> javaType(c.type()).equals("LocalDateTime"));
        StringBuilder imports = new StringBuilder();
        if (needBd) {
            imports.append("import java.math.BigDecimal;\n");
        }
        if (needLd) {
            imports.append("import java.time.LocalDate;\n");
        }
        if (needLdt) {
            imports.append("import java.time.LocalDateTime;\n");
        }
        return imports.isEmpty() ? "" : "\n" + imports;
    }

    /** createdAt → SetCreatedAt / GetCreatedAt(setter/getter 命名) */
    private static String accessor(String camelField) {
        return Character.toUpperCase(camelField.charAt(0)) + camelField.substring(1);
    }

    private static String renderEntity(String table, String tableComment, String entity,
                                       String module, List<Col> cols) {
        String comment = tableComment.isBlank() ? table : tableComment;
        String extraImports = collectImports(cols);
        if (cols.stream().anyMatch(c -> c.name().equals("deleted"))) {
            String logicImport = "import com.baomidou.mybatisplus.annotation.TableLogic;\n";
            extraImports = extraImports.isBlank() ? "\n" + logicImport : extraImports + logicImport;
        }
        return ENTITY_TEMPLATE
                .replace("__HEADER__", headerOf(module, ".entity", comment + "(" + table + ")"))
                .replace("__MODULE__", module)
                .replace("__EXTRA_IMPORTS__", extraImports)
                .replace("__TABLE__", table)
                .replace("__ENTITY__", entity)
                .replace("__FIELDS__", String.join("\n\n", fieldChunks(cols)));
    }

    /** SaveRequest 的 import 行(SERVICE/CONTROLLER/TEST 模板共用;readOnly 域不渲染) */
    private static String commandImport(String module, String entity) {
        return "import com.own.erp." + module + ".request.command." + entity + "SaveRequest;\n";
    }

    private static String renderQuery(String entity, String module, String nameZh) {
        return QUERY_TEMPLATE
                .replace("__HEADER__", headerOf(module, ".request.query",
                        nameZh + "分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)",
                        "过滤条件字段按业务在此补(pageNo/pageSize 已由 PageQuery 提供并钳制 ≤500)"))
                .replace("__MODULE__", module)
                .replace("__ENTITY__", entity)
                .replace("__NAME_ZH__", nameZh);
    }

    /** Response:record+@Builder 全字段(含 id/created_at/updated_at,读侧常要展示;剔除 deleted 服务端管理列)+ 显式 from(entity) builder 链映射 */
    private static String renderResponse(String entity, String module, String nameZh, List<Col> cols) {
        List<Col> visible = cols.stream()
                .filter(c -> !"deleted".equals(c.name()))
                .toList();
        StringBuilder mappings = new StringBuilder();
        for (Col c : visible) {
            String field = toCamel(c.name());
            mappings.append("                .").append(field)
                    .append("(entity.get").append(accessor(field)).append("())\n");
        }
        return RESPONSE_TEMPLATE
                .replace("__HEADER__", headerOf(module, ".response",
                        nameZh + "对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)",
                        "record+@Builder(模型可变性分级 docs/07 §1):读侧不可变;from 用 builder 命名传参防相邻同类型字段错位",
                        "生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进组件即编译期封死"))
                .replace("__MODULE__", module)
                .replace("__ENTITY__", entity)
                .replace("__NAME_ZH__", nameZh)
                .replace("__EXTRA_IMPORTS__", collectImports(visible))
                .replace("__FIELDS__", String.join(",\n\n", componentChunks(visible)))
                .replace("__MAPPINGS__", mappings.toString());
    }

    /** 写侧入参:record+@Builder,剔除 id/created_at/updated_at/deleted(服务端管理),其余全含 + 显式 toEntity() builder 链映射 */
    private static String renderSaveRequest(String entity, String module, String nameZh, List<Col> cols) {
        List<Col> writable = cols.stream()
                .filter(c -> !"id".equals(c.name())
                        && !"created_at".equals(c.name())
                        && !"updated_at".equals(c.name())
                        && !"deleted".equals(c.name()))
                .toList();
        StringBuilder mappings = new StringBuilder();
        for (Col c : writable) {
            String field = toCamel(c.name());
            mappings.append("                .").append(field)
                    .append("(").append(field).append(")\n");
        }
        return SAVE_REQUEST_TEMPLATE
                .replace("__HEADER__", headerOf(module, ".request.command",
                        nameZh + "写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)",
                        "record+@Builder(模型可变性分级 docs/07 §1);toEntity 用 entity builder 链一次成型(纯构造位,docs/07 §1 分级①)",
                        "生成器已剔除 id/created_at/updated_at/deleted(服务端管理列);其余服务端管理列(如 merchant_id)按业务人工删减",
                        "校验注解(@NotNull/@Size 等)随业务约束逐步补,Controller 侧 @Valid 已就位(docs/07 §7)",
                        "含敏感字段(凭证/密码)的 record 必须手写 toString 脱敏——record 自动 toString 无法排除组件(docs/07 §1)"))
                .replace("__MODULE__", module)
                .replace("__ENTITY__", entity)
                .replace("__NAME_ZH__", nameZh)
                .replace("__EXTRA_IMPORTS__", collectImports(writable))
                .replace("__FIELDS__", String.join(",\n\n", componentChunks(writable)))
                .replace("__MAPPINGS__", mappings.toString());
    }

    /** name → name;created_at → createdAt */
    private static String toCamel(String column) {
        StringBuilder sb = new StringBuilder();
        for (String part : column.split("_")) {
            if (sb.isEmpty()) {
                sb.append(part);
            } else {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /** Service 文件头 @Description 的 TODO 续行(无 todoId 时调用方不追加) */
    private static String todoDescLine(String todoId) {
        return "TODO(#" + todoId + "): 业务规则(唯一性/引用校验/状态机等)在此人工补齐,禁只生成不校验(docs/07 §1)";
    }

    private static String todoMethodLine(String todoId) {
        return todoId == null ? "" : "        // TODO(#" + todoId + "): 落库/删除前业务校验在此补齐(docs/07 §1)\n";
    }

    private static String renderMapper(String entity, String module) {
        return MAPPER_TEMPLATE
                .replace("__HEADER__", headerOf(module, ".mapper"))
                .replace("__MODULE__", module)
                .replace("__ENTITY__", entity);
    }

    private static String renderService(String entity, String var, String module,
                                        String nameZh, String table, String todoId, boolean readOnly) {
        List<String> desc = new ArrayList<>(List.of(
                nameZh + "服务:" + table + " 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)",
                readOnly
                        ? "系统写入表:不开放人工 CRUD 写接口,对外仅只读查询;entity 不出本层,出参 response/XxxResponse、读入参 query/XxxQuery"
                        : "API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest(CQRS 分包),出参 response/XxxResponse,entity 不出本层",
                "本类为脚手架骨架,复杂规则必须人工补齐(生成器不实现业务)"));
        if (todoId != null) {
            desc.add(readOnly
                    ? "TODO(#" + todoId + "): 系统写入唯一入口(同步 upsert/落库,幂等靠表唯一键)在此人工补齐,禁旁路 insert(docs/07 §1)"
                    : todoDescLine(todoId));
        }
        return SERVICE_TEMPLATE
                .replace("__HEADER__", fileHeader(author, date, "com.own.erp." + module + ".service", desc))
                .replace("__MODULE__", module)
                .replace("__ENTITY__", entity)
                .replace("__VAR__", var)
                .replace("__COMMAND_IMPORT__", readOnly ? "" : commandImport(module, entity))
                .replace("__WRITE_METHODS__", readOnly ? "" : serviceWriteMethods(entity, var, todoId));
    }

    /** Service 写侧三方法(save/update/delete);readOnly 域不渲染,首行空行与上方 getById 分隔 */
    private static String serviceWriteMethods(String entity, String var, String todoId) {
        String todo = todoMethodLine(todoId);
        return "\n"
                + "    /** 新增,返回自增ID */\n"
                + "    public Long save(" + entity + "SaveRequest request) {\n"
                + todo
                + "        " + entity + " " + var + " = request.toEntity();\n"
                + "        " + var + "Mapper.insert(" + var + ");\n"
                + "        return " + var + ".getId();\n"
                + "    }\n"
                + "\n"
                + "    /** 更新(MP 忽略 null 可部分更新;id 只认路径参数) */\n"
                + "    public void update(Long id, " + entity + "SaveRequest request) {\n"
                + todo
                + "        " + entity + " " + var + " = request.toEntity();\n"
                + "        " + var + ".setId(id);\n"
                + "        " + var + "Mapper.updateById(" + var + ");\n"
                + "    }\n"
                + "\n"
                + "    /** 删除(一期硬删) */\n"
                + "    public void delete(Long id) {\n"
                + todo
                + "        " + var + "Mapper.deleteById(id);\n"
                + "    }\n";
    }

    private static String renderController(String entity, String var, String module,
                                           String nameZh, String path, boolean readOnly) {
        return CONTROLLER_TEMPLATE
                .replace("__HEADER__", headerOf(module, ".controller",
                        nameZh + (readOnly ? "查询" : "管理") + ":" + entity + " 域整域收口,一律走 " + entity + "Service(docs/07 §2.1)",
                        readOnly
                                ? "系统写入表:仅查询接口,写入口在 Service(见其类注释 TODO 指引),不开放人工写接口"
                                : "API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest(CQRS 分包),出参 response/XxxResponse,entity 不出 Service 层"))
                .replace("__MODULE__", module)
                .replace("__ENTITY__", entity)
                .replace("__VAR__", var)
                .replace("__NAME_ZH__", nameZh)
                .replace("__PATH__", path)
                .replace("__TAG_DESC__", readOnly ? nameZh + "查询(系统写入表,只读)" : nameZh + " CRUD 与分页查询")
                .replace("__COMMAND_IMPORT__", readOnly ? "" : commandImport(module, entity))
                .replace("__VALID_IMPORT__", readOnly ? "" : "import jakarta.validation.Valid;\n")
                .replace("__WRITE_WEB_IMPORTS_A__", readOnly ? "" : "import org.springframework.web.bind.annotation.DeleteMapping;\n")
                .replace("__WRITE_WEB_IMPORTS_B__", readOnly ? "" : """
                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.PutMapping;
                        import org.springframework.web.bind.annotation.RequestBody;
                        """)
                .replace("__WRITE_ENDPOINTS__", readOnly ? "" : controllerWriteEndpoints(entity, var, nameZh));
    }

    /** Controller 写侧三接口(create/update/delete);readOnly 域不渲染,首行空行与上方详情接口分隔 */
    private static String controllerWriteEndpoints(String entity, String var, String nameZh) {
        return "\n"
                + "    @Operation(summary = \"新增" + nameZh + "\")\n"
                + "    @PostMapping\n"
                + "    public Result<Long> create(@Valid @RequestBody " + entity + "SaveRequest request) {\n"
                + "        return Result.ok(" + var + "Service.save(request));\n"
                + "    }\n"
                + "\n"
                + "    @Operation(summary = \"更新" + nameZh + "\", description = \"MP updateById 忽略 null 字段,可部分更新;id 只认路径参数\")\n"
                + "    @PutMapping(\"/{id}\")\n"
                + "    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody " + entity + "SaveRequest request) {\n"
                + "        " + var + "Service.update(id, request);\n"
                + "        return Result.ok();\n"
                + "    }\n"
                + "\n"
                + "    @Operation(summary = \"删除" + nameZh + "\", description = \"一期硬删\")\n"
                + "    @DeleteMapping(\"/{id}\")\n"
                + "    public Result<Void> delete(@PathVariable Long id) {\n"
                + "        " + var + "Service.delete(id);\n"
                + "        return Result.ok();\n"
                + "    }\n";
    }

    private static String renderTest(String entity, String var, String module,
                                     String nameZh, boolean hasNameField, boolean readOnly) {
        return TEST_TEMPLATE
                .replace("__HEADER__", headerOf(module, ".service",
                        entity + "Service 单测(AIR:mock Mapper,不依赖数据库)"))
                .replace("__MODULE__", module)
                .replace("__ENTITY__", entity)
                .replace("__VAR__", var)
                .replace("__NAME_ZH__", nameZh)
                .replace("__COMMAND_IMPORT__", readOnly ? "" : commandImport(module, entity))
                .replace("__CAPTOR_IMPORT__", readOnly ? "" : "import org.mockito.ArgumentCaptor;\n")
                .replace("__VERIFY_IMPORT__", readOnly ? "" : "import static org.mockito.Mockito.verify;\n")
                .replace("__WRITE_TESTS__", readOnly ? "" : testWriteMethods(entity, var, nameZh, hasNameField));
    }

    /** 单测写侧三用例(save/update/delete);readOnly 域不渲染;SaveRequest 为 record,构造走 builder(docs/07 §1) */
    private static String testWriteMethods(String entity, String var, String nameZh, boolean hasNameField) {
        String nameSet = hasNameField ? "                .name(\"冒烟" + nameZh + "\")\n" : "";
        String nameAssert = hasNameField ? "        assertEquals(\"冒烟" + nameZh + "\", captor.getValue().getName());\n" : "";
        return "\n"
                + "    @Test\n"
                + "    void saveMapsRequestAndInserts() {\n"
                + "        " + entity + "SaveRequest request = " + entity + "SaveRequest.builder()\n"
                + nameSet
                + "                .build();\n"
                + "        " + var + "Service.save(request);\n"
                + "        ArgumentCaptor<" + entity + "> captor = ArgumentCaptor.forClass(" + entity + ".class);\n"
                + "        verify(" + var + "Mapper).insert(captor.capture());\n"
                + nameAssert
                + "    }\n"
                + "\n"
                + "    @Test\n"
                + "    void updateSetsIdFromPathAndDelegates() {\n"
                + "        " + var + "Service.update(9L, " + entity + "SaveRequest.builder().build());\n"
                + "        ArgumentCaptor<" + entity + "> captor = ArgumentCaptor.forClass(" + entity + ".class);\n"
                + "        verify(" + var + "Mapper).updateById(captor.capture());\n"
                + "        assertEquals(9L, captor.getValue().getId());\n"
                + "    }\n"
                + "\n"
                + "    @Test\n"
                + "    void deleteDelegatesToMapper() {\n"
                + "        " + var + "Service.delete(1L);\n"
                + "        verify(" + var + "Mapper).deleteById(1L);\n"
                + "    }\n";
    }

    private static void printMenuSql(String nameZh, String module, String table, String path, String menuParent) {
        String menuPath = path.startsWith("/api") ? path.substring(4) : path;
        System.out.println();
        System.out.println("[codegen] 菜单注册 SQL(核对 id 不与现有冲突后手工执行):");
        System.out.println("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, perm_key, path, component, icon, sort) VALUES");
        System.out.println("(<下一可用ID>, " + menuParent + ", '" + nameZh + "', 2, '" + module
                + ":list', '" + menuPath + "', '" + module + "/" + pluralKebab(table) + "/index', NULL, 1);");
    }

    private static int writeFile(Path moduleDir, String relative, String content, boolean force) {
        Path target = moduleDir.resolve(relative);
        if (Files.exists(target) && !force) {
            System.out.println("[codegen] [跳过] 已存在(--force 覆盖):" + relative);
            return 0;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            System.out.println("[codegen] [生成] " + relative);
            return 1;
        } catch (IOException e) {
            throw fail("写入失败:" + target + " - " + e.getMessage());
        }
    }

    private static IllegalStateException fail(String msg) {
        return new IllegalStateException("[codegen] " + msg);
    }

    // ---- 模板(占位符 __X__;生成物风格 = 项目定版:plain @Service 整域收口、record+@Builder 模型、@Tag/@Operation 中文) ----

    private static final String ENTITY_TEMPLATE = """
            package com.own.erp.__MODULE__.entity;

            import com.baomidou.mybatisplus.annotation.IdType;
            import com.baomidou.mybatisplus.annotation.TableId;
            import com.baomidou.mybatisplus.annotation.TableName;
            import lombok.AllArgsConstructor;
            import lombok.Builder;
            import lombok.Data;
            import lombok.NoArgsConstructor;
            __EXTRA_IMPORTS__
            __HEADER__/**
             * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
             * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
             */
            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            @TableName("__TABLE__")
            public class __ENTITY__ {

            __FIELDS__
            }
            """;

    private static final String MAPPER_TEMPLATE = """
            package com.own.erp.__MODULE__.mapper;

            import com.baomidou.mybatisplus.core.mapper.BaseMapper;
            import com.own.erp.__MODULE__.entity.__ENTITY__;

            __HEADER__public interface __ENTITY__Mapper extends BaseMapper<__ENTITY__> {
            }
            """;

    private static final String SERVICE_TEMPLATE = """
            package com.own.erp.__MODULE__.service;

            import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
            import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
            import com.own.erp.__MODULE__.entity.__ENTITY__;
            import com.own.erp.__MODULE__.mapper.__ENTITY__Mapper;
            import com.own.erp.__MODULE__.request.query.__ENTITY__Query;
            __COMMAND_IMPORT__import com.own.erp.__MODULE__.response.__ENTITY__Response;
            import lombok.RequiredArgsConstructor;
            import org.springframework.stereotype.Service;

            __HEADER__@Service
            @RequiredArgsConstructor
            public class __ENTITY__Service {

                private final __ENTITY__Mapper __VAR__Mapper;

                /** 分页查询(默认按 id 倒序;过滤条件在 __ENTITY__Query 加字段后在此补 Wrapper 条件) */
                public Page<__ENTITY__Response> page(__ENTITY__Query query) {
                    Page<__ENTITY__> result = __VAR__Mapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                            new LambdaQueryWrapper<__ENTITY__>().orderByDesc(__ENTITY__::getId));
                    Page<__ENTITY__Response> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
                    responsePage.setRecords(result.getRecords().stream().map(__ENTITY__Response::from).toList());
                    return responsePage;
                }

                /** 详情,出参 Response;不存在返回 null */
                public __ENTITY__Response getById(Long id) {
                    __ENTITY__ __VAR__ = __VAR__Mapper.selectById(id);
                    return __VAR__ == null ? null : __ENTITY__Response.from(__VAR__);
                }
            __WRITE_METHODS__}
            """;

    private static final String CONTROLLER_TEMPLATE = """
            package com.own.erp.__MODULE__.controller;

            import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
            import com.own.erp.common.api.Result;
            import com.own.erp.__MODULE__.request.query.__ENTITY__Query;
            __COMMAND_IMPORT__import com.own.erp.__MODULE__.response.__ENTITY__Response;
            import com.own.erp.__MODULE__.service.__ENTITY__Service;
            import io.swagger.v3.oas.annotations.Operation;
            import io.swagger.v3.oas.annotations.tags.Tag;
            __VALID_IMPORT__import lombok.RequiredArgsConstructor;
            __WRITE_WEB_IMPORTS_A__import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PathVariable;
            __WRITE_WEB_IMPORTS_B__import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            __HEADER__@Tag(name = "__NAME_ZH__", description = "__TAG_DESC__")
            @RestController
            @RequestMapping("__PATH__")
            @RequiredArgsConstructor
            public class __ENTITY__Controller {

                private final __ENTITY__Service __VAR__Service;

                @Operation(summary = "分页查询__NAME_ZH__")
                @GetMapping
                public Result<Page<__ENTITY__Response>> page(__ENTITY__Query query) {
                    return Result.ok(__VAR__Service.page(query));
                }

                @Operation(summary = "__NAME_ZH__详情", description = "不存在返回 null data")
                @GetMapping("/{id}")
                public Result<__ENTITY__Response> get(@PathVariable Long id) {
                    return Result.ok(__VAR__Service.getById(id));
                }
            __WRITE_ENDPOINTS__}
            """;

    private static final String TEST_TEMPLATE = """
            package com.own.erp.__MODULE__.service;

            import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
            import com.own.erp.__MODULE__.entity.__ENTITY__;
            import com.own.erp.__MODULE__.mapper.__ENTITY__Mapper;
            import com.own.erp.__MODULE__.request.query.__ENTITY__Query;
            __COMMAND_IMPORT__import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.Test;
            __CAPTOR_IMPORT__
            import java.util.List;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertNull;
            import static org.mockito.ArgumentMatchers.any;
            import static org.mockito.Mockito.doReturn;
            import static org.mockito.Mockito.mock;
            __VERIFY_IMPORT__import static org.mockito.Mockito.when;

            __HEADER__class __ENTITY__ServiceTest {

                private __ENTITY__Mapper __VAR__Mapper;
                private __ENTITY__Service __VAR__Service;

                @BeforeEach
                void setUp() {
                    __VAR__Mapper = mock(__ENTITY__Mapper.class);
                    __VAR__Service = new __ENTITY__Service(__VAR__Mapper);
                }
            __WRITE_TESTS__
                @Test
                void getByIdMapsToResponseAndReturnsNullWhenMissing() {
                    __ENTITY__ __VAR__ = new __ENTITY__();
                    __VAR__.setId(1L);
                    when(__VAR__Mapper.selectById(1L)).thenReturn(__VAR__);
                    assertEquals(1L, __VAR__Service.getById(1L).id());
                    assertNull(__VAR__Service.getById(404L));
                }

                @Test
                void pageMapsRecordsToResponse() {
                    __ENTITY__ __VAR__ = new __ENTITY__();
                    __VAR__.setId(2L);
                    Page<__ENTITY__> page = new Page<>(1, 10);
                    page.setRecords(List.of(__VAR__));
                    doReturn(page).when(__VAR__Mapper).selectPage(any(), any());
                    assertEquals(2L, __VAR__Service.page(new __ENTITY__Query()).getRecords().get(0).id());
                }
            }
            """;

    private static final String QUERY_TEMPLATE = """
            package com.own.erp.__MODULE__.request.query;

            import com.own.erp.common.api.PageQuery;
            import lombok.Data;
            import lombok.EqualsAndHashCode;

            __HEADER__/**
             * 保持 class(模型可变性分级 docs/07 §1 的继承例外):record 不能继承类,
             * 而 XxxQuery 必须继承 PageQuery 的钳制分页——可变性豁免,后续 PageQuery 重构再议
             */
            @Data
            @EqualsAndHashCode(callSuper = true)
            public class __ENTITY__Query extends PageQuery {
            }
            """;

    private static final String RESPONSE_TEMPLATE = """
            package com.own.erp.__MODULE__.response;

            import com.own.erp.__MODULE__.entity.__ENTITY__;
            import lombok.Builder;
            __EXTRA_IMPORTS__
            __HEADER__@Builder
            public record __ENTITY__Response(

            __FIELDS__

            ) {

                /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);builder 命名传参防相邻同类型字段错位 */
                public static __ENTITY__Response from(__ENTITY__ entity) {
                    return __ENTITY__Response.builder()
            __MAPPINGS__                .build();
                }
            }
            """;

    private static final String SAVE_REQUEST_TEMPLATE = """
            package com.own.erp.__MODULE__.request.command;

            import com.own.erp.__MODULE__.entity.__ENTITY__;
            import lombok.Builder;
            __EXTRA_IMPORTS__
            __HEADER__@Builder
            public record __ENTITY__SaveRequest(

            __FIELDS__

            ) {

                /** 请求 → 实体显式逐字段映射(禁反射拷贝);entity builder 链命名传参(与 from 同风格,防错位) */
                public __ENTITY__ toEntity() {
                    return __ENTITY__.builder()
            __MAPPINGS__                .build();
                }
            }
            """;
}
