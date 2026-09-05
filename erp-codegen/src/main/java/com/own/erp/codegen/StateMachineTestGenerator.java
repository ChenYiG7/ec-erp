package com.own.erp.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 状态机守卫测试生成器(开发工具,非运行时依赖;erp-codegen 第二工具,经 -Dcodegen.mainClass 切换)。
 *
 *     背景:"条件更新即守卫"状态机 Service(#10 采购/#11 发货/#12 售后已四连)的 Mockito 守卫测试高度同构,
 *     机械率 >85%(盘点见 2026-09-04 TODO.md record),按铁律 #8 沉淀成生成器,不再走 AI 手写。
 *
 *     输入:行式 spec 文件(键值头 + fixture 块 + action 行;语法见 erp-codegen/README.md,样板 erp-aftersale/testgen-aftersale.txt)。
 *     输出:目标模块 service/<Entity>StateMachineTest.java,守卫四类用例——
 *          ①cas 命中+脱靶合并式(thenReturn(1) 不抛 / thenReturn(0) assertThrows+消息 contains)
 *          ②单不存在(preRead stub 404,可选)
 *          ③尾参必填(required,空白实参 assertThrows + never() 副作用校验)
 *          ④成功路径 cas 实参核对(verify)。
 *     边界:跨域动账断言/发足判定/类型路由全量/多行造数不在射程,落类尾 TODO(编号) 人工槽位(禁裸 TODO,无 todoId 不许写 manual)。
 *
 *     用法(项目根目录执行):
 *     <pre>
 *     mvn -q -pl erp-codegen compile exec:java \
 *       -Dcodegen.mainClass=com.own.erp.codegen.StateMachineTestGenerator \
 *       -Dspec=erp-aftersale/testgen-aftersale.txt [-Dforce=true]
 *     </pre>
 *
 *     生成物存在即跳过(force=true 覆盖);生成后须跑 mvn -pl erp-&lt;module&gt; test 验证,
 *     新 TODO 编号须登记 TODO.md(与 CodeGenerator 同规约)。
 */
public final class StateMachineTestGenerator {

    /** 一行 action = 一个守卫动作的完整描述;stub/call 是逐字 Java 实参,生成器只包脚手架不解析语义 */
    private record Action(String method, String cas, String stub, String call, String fail,
                          String preRead, String missingMsg, String requiredMsg, boolean verify, String name) {
    }

    /** mock 依赖:simpleName=类简名,fqcn=完整 import,var=字段名(类名首字母小写) */
    private record Mock(String simpleName, String fqcn, String var) {
    }

    private static final String FLAGS = "STATUS|TYPE|FLOW";

    public static void main(String[] args) throws IOException {
        String specPath = option(args, "spec");
        if (specPath == null || specPath.isBlank()) {
            throw fail("缺少 -Dspec=<spec文件路径>(相对项目根,如 erp-aftersale/testgen-aftersale.txt)");
        }
        boolean force = "true".equalsIgnoreCase(option(args, "force"));
        String author = option(args, "author") != null ? option(args, "author") : CodeGenerator.DEFAULT_AUTHOR;
        String date = option(args, "date") != null ? option(args, "date")
                : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/M/d"));

        Path root = locateProjectRoot();
        Path specFile = root.resolve(specPath);
        if (!Files.isRegularFile(specFile)) {
            throw fail("spec 文件不存在:" + specFile);
        }
        Spec spec = parseSpec(Files.readAllLines(specFile), specPath);

        Path moduleDir = root.resolve("erp-" + spec.module);
        if (!Files.isDirectory(moduleDir)) {
            throw fail("模块目录不存在:" + moduleDir);
        }
        checkTestDep(moduleDir.resolve("pom.xml"));

        String outRelative = "src/test/java/com/own/erp/" + spec.module + "/service/"
                + spec.entity + "StateMachineTest.java";
        System.out.println("[testgen] 模块=erp-" + spec.module + " 实体=" + spec.entity
                + " 动作数=" + spec.actions.size() + " force=" + force);
        int written = writeFile(moduleDir, outRelative, render(spec, author, date), force);
        if (written > 0) {
            System.out.println("[testgen] 完成:验证 mvn -pl erp-" + spec.module + " test;"
                    + (spec.todoId != null ? " TODO(#" + spec.todoId + ") 已在生成物留人工槽位,编号须登记 TODO.md" : ""));
        }
    }

    // ---- spec 解析(行式:键值头 + fixture<< >> 块 + action 管道行;报错一律带行号,禁静默) ----

    private static final class Spec {
        String module;
        String entity;
        String var;
        String constClass;
        String todoId;
        String manual;
        final List<Mock> mocks = new ArrayList<>();
        final List<String> fixtures = new ArrayList<>();
        final List<Action> actions = new ArrayList<>();
    }

    private static Spec parseSpec(List<String> lines, String specFile) {
        Spec spec = new Spec();
        boolean inFixture = false;
        StringBuilder fixtureBuf = null;
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            int lineNo = i + 1;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (inFixture) {
                if (line.equals(">>")) {
                    inFixture = false;
                    String text = fixtureBuf.toString().stripTrailing();
                    if (text.isBlank()) {
                        throw fail(specFile + ":" + lineNo + " fixture 块为空");
                    }
                    spec.fixtures.add(text);
                    continue;
                }
                fixtureBuf.append(raw.stripTrailing()).append('\n');
                continue;
            }
            if (line.equals("fixture<<")) {
                inFixture = true;
                fixtureBuf = new StringBuilder();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw fail(specFile + ":" + lineNo + " 无法解析(应为 key=value / fixture<< / action=...):" + line);
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            switch (key) {
                case "module" -> spec.module = value;
                case "entity" -> spec.entity = value;
                case "var" -> spec.var = value;
                case "constClass" -> spec.constClass = value;
                case "todoId" -> spec.todoId = value;
                case "manual" -> spec.manual = value;
                case "mock" -> spec.mocks.add(parseMock(value, spec, lineNo, specFile));
                case "action" -> spec.actions.add(parseAction(value, lineNo, specFile));
                default -> throw fail(specFile + ":" + lineNo + " 未知键:" + key);
            }
        }
        if (inFixture) {
            throw fail(specFile + ": fixture 块未闭合(缺 >> 行)");
        }
        require(spec.module != null, "缺 module");
        require(spec.entity != null, "缺 entity");
        require(spec.var != null, "缺 var");
        require(!spec.mocks.isEmpty(), "至少一个 mock(第一个 = Service 构造器唯一依赖时按序追加)");
        require(!spec.actions.isEmpty(), "至少一行 action");
        if (spec.manual != null && spec.todoId == null) {
            throw fail("manual 需要 todoId 配合(禁无编号裸 TODO,docs/07 §1)");
        }
        // 重名守卫:默认方法名冲突(同 action 多行未给 name=)直接报错,不许生成编译不过的类
        Set<String> methodNames = new HashSet<>();
        for (Action a : spec.actions) {
            String merged = mergedName(a);
            if (!methodNames.add(merged)) {
                throw fail("测试方法名重复:" + merged + "(同动作多行须给 name= 区分)");
            }
            if (a.requiredMsg != null && !methodNames.add(a.method + "RejectsBlankResult")) {
                throw fail("测试方法名重复:" + a.method + "RejectsBlankResult(同动作至多一行 required)");
            }
        }
        return spec;
    }

    /** mock=<SimpleName>(默认本模块 mapper 包)或 mock=<完整fqcn>(含点号,跨包/跨模块均可) */
    private static Mock parseMock(String value, Spec spec, int lineNo, String specFile) {
        String fqcn = value.contains(".")
                ? value
                : "com.own.erp." + requireModule(spec, lineNo, specFile) + ".mapper." + value;
        String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        return new Mock(simpleName, fqcn, decapitalize(simpleName));
    }

    private static String requireModule(Spec spec, int lineNo, String specFile) {
        if (spec.module == null) {
            throw fail(specFile + ":" + lineNo + " mock 行出现在 module 之前(短名形式需要 module 推包)");
        }
        return spec.module;
    }

    /**
     * action=<方法> | cas=<mapper方法> | stub=<stub实参> | call=<调用实参> | fail=<消息前缀>
     * [ | preRead=<stub表达式> | missingMsg=<消息> | required=<消息> | verify | name=<后缀> ]
     */
    private static Action parseAction(String value, int lineNo, String specFile) {
        String[] parts = value.split("\\|");
        String method = parts[0].strip();
        require(!method.isEmpty(), lineNo, specFile, "action 缺方法名");
        String cas = null;
        String stub = null;
        String call = null;
        String failMsg = null;
        String preRead = null;
        String missingMsg = null;
        String requiredMsg = null;
        boolean verify = false;
        String name = null;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].strip();
            int eq = part.indexOf('=');
            if (eq <= 0) {
                if (part.equals("verify")) {
                    verify = true;
                    continue;
                }
                throw fail(specFile + ":" + lineNo + " action 段无法解析:" + part);
            }
            String k = part.substring(0, eq).strip();
            String v = part.substring(eq + 1).strip();
            switch (k) {
                case "cas" -> cas = v;
                case "stub" -> stub = v;
                case "call" -> call = v;
                case "fail" -> failMsg = v;
                case "preRead" -> preRead = v;
                case "missingMsg" -> missingMsg = v;
                case "required" -> requiredMsg = v;
                case "name" -> name = v;
                default -> throw fail(specFile + ":" + lineNo + " action 未知键:" + k);
            }
        }
        require(cas != null, lineNo, specFile, "cas=");
        require(stub != null, lineNo, specFile, "stub=");
        require(call != null, lineNo, specFile, "call=");
        require(failMsg != null, lineNo, specFile, "fail=");
        require(missingMsg == null || preRead != null, lineNo, specFile, "missingMsg 依赖 preRead(无前置读不存在'单不存在'路径)");
        return new Action(method, cas, stub, call, failMsg, preRead, missingMsg, requiredMsg, verify, name);
    }

    // ---- 渲染 ----

    private static String render(Spec spec, String author, String date) {
        String consts = spec.constClass == null ? "" : spec.constClass.substring(spec.constClass.lastIndexOf('.') + 1) + ".";
        StringBuilder sb = new StringBuilder(4096);
        sb.append("package com.own.erp.").append(spec.module).append(".service;\n\n");

        List<String> imports = new ArrayList<>();
        imports.add("com.own.erp.common.exception.BusinessException");
        imports.add("com.own.erp." + spec.module + ".entity." + spec.entity);
        for (Mock m : spec.mocks) {
            imports.add(m.fqcn());
        }
        if (spec.constClass != null) {
            imports.add(spec.constClass);
        }
        imports.add("org.junit.jupiter.api.BeforeEach");
        imports.add("org.junit.jupiter.api.Test");
        imports.add("static org.junit.jupiter.api.Assertions.assertThrows");
        imports.add("static org.junit.jupiter.api.Assertions.assertTrue");
        imports.add("static org.mockito.ArgumentMatchers.any");
        imports.add("static org.mockito.Mockito.mock");
        imports.add("static org.mockito.Mockito.never");
        imports.add("static org.mockito.Mockito.verify");
        imports.add("static org.mockito.Mockito.when");
        imports.stream().sorted().forEach(i -> sb.append("import ").append(i).append(";\n"));

        List<String> desc = List.of(
                spec.entity + "StateMachineTest 单测(状态机守卫,StateMachineTestGenerator 产出,spec 改后重跑再生成)",
                "覆盖守卫四类:cas 命中+脱靶合并式 / 单不存在 / 尾参必填 / 成功路径实参核对(AIR:mock Mapper,不依赖数据库)",
                "复杂编排断言(跨域动账/判定逻辑/多行造数)不在生成射程,见类尾 TODO 槽位");
        sb.append('\n').append(CodeGenerator.fileHeader(author, date, "com.own.erp." + spec.module, desc));
        sb.append("class ").append(spec.entity).append("StateMachineTest {\n\n");
        sb.append("    private static final Long ID = 1L;\n\n");
        for (Mock m : spec.mocks) {
            sb.append("    private ").append(m.simpleName()).append(' ').append(m.var()).append(";\n");
        }
        sb.append("    private ").append(spec.entity).append("Service ").append(spec.var).append("Service;\n\n");
        sb.append("    @BeforeEach\n    void setUp() {\n");
        for (Mock m : spec.mocks) {
            sb.append("        ").append(m.var()).append(" = mock(").append(m.simpleName()).append(".class);\n");
        }
        sb.append("        ").append(spec.var).append("Service = new ").append(spec.entity)
                .append("Service(");
        for (int i = 0; i < spec.mocks.size(); i++) {
            sb.append(i > 0 ? ", " : "").append(spec.mocks.get(i).var());
        }
        sb.append(");\n    }\n");
        for (String fixture : spec.fixtures) {
            sb.append('\n');
            for (String l : fixture.split("\n", -1)) {
                sb.append(l.isEmpty() ? "\n" : "    " + l + "\n");
            }
        }
        Set<String> requiredEmitted = new HashSet<>();
        for (Action a : spec.actions) {
            sb.append(renderGuardTest(spec, a, consts));
            if (a.requiredMsg != null && requiredEmitted.add(a.method)) {
                sb.append(renderRequiredTest(spec, a, consts));
            }
        }
        if (spec.manual != null) {
            sb.append("\n    // TODO(#").append(spec.todoId).append(") 人工补齐(生成器不覆盖):").append(spec.manual).append('\n');
        }
        sb.append("}\n");
        return sb.toString();
    }

    /** 守卫合并式 @Test:命中(+可选单不存在)+ 脱靶(thenReturn(0) → assertThrows+消息 contains) */
    private static String renderGuardTest(Spec spec, Action a, String consts) {
        String service = spec.var + "Service." + a.method;
        boolean hasPreRead = a.preRead != null;
        StringBuilder sb = new StringBuilder();
        sb.append('\n').append("    @Test\n    void ").append(mergedName(a)).append("() {\n");
        if (hasPreRead) {
            sb.append("        when(").append(firstMockVar(spec)).append(".selectById(ID)).thenReturn(")
                    .append(qualify(a.preRead, consts)).append(");\n");
        }
        String stub = qualify(a.stub, consts);
        String call = qualify(a.call, consts);
        sb.append("        when(").append(firstMockVar(spec)).append('.').append(a.cas).append('(')
                .append(args(stub)).append(")).thenReturn(1);\n");
        sb.append("        ").append(service).append('(').append(args(call)).append(");\n");
        if (a.verify) {
            sb.append("        verify(").append(firstMockVar(spec)).append(").").append(a.cas).append('(')
                    .append(args(stub)).append(");\n");
        }
        if (hasPreRead && a.missingMsg != null) {
            String missingStub = qualify(a.preRead, consts).replaceAll("\\bID\\b", "404L");
            String missingCall = qualify(a.call, consts).replaceAll("\\bID\\b", "404L");
            sb.append("\n        when(").append(firstMockVar(spec)).append(".selectById(404L)).thenReturn(null);\n");
            sb.append("        assertTrue(assertThrows(BusinessException.class, () -> ").append(service)
                    .append('(').append(args(missingCall)).append("))\n");
            sb.append("                .getMessage().contains(\"").append(a.missingMsg).append("\"));\n");
        }
        // 脱靶半段:无 preRead 用不同 id(2L)区分命中/未命中;有 preRead 同 id 重复 stub 覆盖
        String missId = hasPreRead ? "ID" : "2L";
        String missStub = hasPreRead ? stub : stub.replaceAll("\\bID\\b", "2L");
        String missCall = hasPreRead ? call : call.replaceAll("\\bID\\b", "2L");
        sb.append('\n');
        sb.append("        when(").append(firstMockVar(spec)).append('.').append(a.cas).append('(')
                .append(args(missStub)).append(")).thenReturn(0);\n");
        sb.append("        assertTrue(assertThrows(BusinessException.class, () -> ").append(service)
                .append('(').append(args(missCall)).append("))\n");
        sb.append("                .getMessage().contains(\"").append(a.fail).append("\"));\n");
        sb.append("    }\n");
        return sb.toString();
    }

    /** 必填用例:尾参换空白 → assertThrows + never()(any() 个数 = stub 元数) */
    private static String renderRequiredTest(Spec spec, Action a, String consts) {
        List<String> callArgs = topLevelSplit(qualify(a.call, consts));
        callArgs.set(callArgs.size() - 1, "\"  \"");
        StringBuilder anys = new StringBuilder();
        for (int i = 0; i < topLevelSplit(qualify(a.stub, consts)).size(); i++) {
            anys.append(i > 0 ? ", " : "").append("any()");
        }
        return "\n    @Test\n    void " + a.method + "RejectsBlankResult() {\n"
                + "        assertTrue(assertThrows(BusinessException.class, () -> " + spec.var + "Service."
                + a.method + "(" + String.join(", ", callArgs) + "))\n"
                + "                .getMessage().contains(\"" + a.requiredMsg + "\"));\n"
                + "        verify(" + firstMockVar(spec) + ", never())." + a.cas + "(" + anys + ");\n"
                + "    }\n";
    }

    private static String mergedName(Action a) {
        return a.method + (a.name == null ? "" : a.name) + "SucceedsOnCasHitAndFailsOnMiss";
    }

    private static String firstMockVar(Spec spec) {
        return spec.mocks.get(0).var();
    }

    // ---- 表达式加工:裸状态常量补前缀(引号内不动);长实参列表按顶层逗号折行 ----

    /** STATUS_X/TYPE_X/FLOW_X 裸 token → <Consts简名>.token;带引号字符串内不加工 */
    static String qualify(String expr, String consts) {
        if (consts.isEmpty()) {
            return expr;
        }
        StringBuilder out = new StringBuilder(expr.length() + 32);
        boolean inQuote = false;
        StringBuilder token = new StringBuilder();
        for (int i = 0; i <= expr.length(); i++) {
            char c = i < expr.length() ? expr.charAt(i) : ' ';
            if (inQuote) {
                out.append(c);
                if (c == '"') {
                    inQuote = false;
                }
                continue;
            }
            if (Character.isJavaIdentifierPart(c)) {
                token.append(c);
                continue;
            }
            flushToken(out, token, consts);
            if (c == '"') {
                inQuote = true;
            }
            if (i < expr.length()) {
                out.append(c);
            }
        }
        return out.toString().stripTrailing();
    }

    private static void flushToken(StringBuilder out, StringBuilder token, String consts) {
        if (!token.isEmpty()) {
            String t = token.toString();
            boolean bareConst = (t.startsWith("STATUS_") || t.startsWith("TYPE_") || t.startsWith("FLOW_"))
                    && !t.equals("ID");
            out.append(bareConst ? consts + t : t);
            token.setLength(0);
        }
    }

    /** 顶层逗号切分(引号内/括号深度内不切) */
    static List<String> topLevelSplit(String expr) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        int depth = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (inQuote) {
                cur.append(c);
                if (c == '"') {
                    inQuote = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inQuote = true;
                case '(' -> depth++;
                case ')' -> depth--;
                case ',' -> {
                    if (depth == 0) {
                        parts.add(cur.toString().strip());
                        cur.setLength(0);
                        continue;
                    }
                }
                default -> { }
            }
            cur.append(c);
        }
        String last = cur.toString().strip();
        if (!last.isEmpty()) {
            parts.add(last);
        }
        return parts;
    }

    /** 实参串:>60 字符按顶层逗号折行(续行 12 空格,对齐手写测试的换行风格) */
    private static String args(String expr) {
        if (expr.length() <= 60) {
            return expr;
        }
        return String.join(",\n                ", topLevelSplit(expr));
    }

    // ---- 基建:配置读取/路径/守卫/写文件(与 CodeGenerator 同规约) ----

    private static String option(String[] args, String key) {
        String fromArg = null;
        for (String a : args) {
            if (a.startsWith(key + "=")) {
                fromArg = a.substring(key.length() + 1);
            }
        }
        String fromProp = System.getProperty(key);
        return fromArg != null ? fromArg : fromProp;
    }

    /** 从 cwd 向上找含 docs/sql/01_schema_init.sql 的目录(exec:java 的 cwd=项目根,向上兜底子目录执行) */
    private static Path locateProjectRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("docs").resolve("sql").resolve("01_schema_init.sql"))) {
                return dir;
            }
        }
        throw fail("未定位到项目根(找 docs/sql/01_schema_init.sql),请在项目根目录执行");
    }

    private static void checkTestDep(Path pom) {
        try {
            if (!Files.readString(pom).contains("spring-boot-starter-test")) {
                throw fail("目标模块 pom 缺 spring-boot-starter-test,生成物编译不过(先补 pom):" + pom);
            }
        } catch (IOException e) {
            throw fail("读 pom 失败:" + pom + " - " + e.getMessage());
        }
    }

    private static int writeFile(Path moduleDir, String relative, String content, boolean force) {
        Path target = moduleDir.resolve(relative);
        if (Files.exists(target) && !force) {
            System.out.println("[testgen] [跳过] 已存在(--force 覆盖):" + relative);
            return 0;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            System.out.println("[testgen] [生成] " + relative);
            return 1;
        } catch (IOException e) {
            throw fail("写入失败:" + target + " - " + e.getMessage());
        }
    }

    private static String decapitalize(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static void require(boolean cond, String msg) {
        if (!cond) {
            throw fail(msg);
        }
    }

    private static void require(boolean cond, int lineNo, String specFile, String key) {
        require(cond, specFile + ":" + lineNo + " action 缺 " + key);
    }

    private static IllegalStateException fail(String msg) {
        return new IllegalStateException("[testgen] " + msg);
    }
}
