package com.example.delicious.agent.tools;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.delicious.agent.knowledge.RecipeKnowledgeBase;
import com.example.delicious.agent.llm.ToolDef;
import com.example.delicious.entity.ClassifyInfo;
import com.example.delicious.entity.FoodsMaterialInfo;
import com.example.delicious.entity.FoodsMenuInfo;
import com.example.delicious.service.AdminInfoService;
import com.example.delicious.service.ClassifyInfoService;
import com.example.delicious.service.FoodsMaterialInfoService;
import com.example.delicious.service.FoodsMenuInfoService;
import com.example.delicious.service.UserInfoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具集（Function Calling / Tools）。
 * <p>
 * 把原有业务能力（菜谱、食材、分类、统计、知识库）封装为模型可调用的工具：
 * 模型负责「决定调用哪个工具、传什么参数」，这里负责「真正执行并返回结构化结果」。
 * 工具执行结果统一以 JSON 字符串回传给模型，构成 ReAct 循环中的 Observation。
 */
@Service
public class RecipeTools {

    private final FoodsMenuInfoService foodsMenuInfoService;
    private final FoodsMaterialInfoService foodsMaterialInfoService;
    private final ClassifyInfoService classifyInfoService;
    private final AdminInfoService adminInfoService;
    private final UserInfoService userInfoService;
    private final RecipeKnowledgeBase knowledgeBase;
    private final ObjectMapper mapper = new ObjectMapper();

    public RecipeTools(FoodsMenuInfoService foodsMenuInfoService,
                       FoodsMaterialInfoService foodsMaterialInfoService,
                       ClassifyInfoService classifyInfoService,
                       AdminInfoService adminInfoService,
                       UserInfoService userInfoService,
                       RecipeKnowledgeBase knowledgeBase) {
        this.foodsMenuInfoService = foodsMenuInfoService;
        this.foodsMaterialInfoService = foodsMaterialInfoService;
        this.classifyInfoService = classifyInfoService;
        this.adminInfoService = adminInfoService;
        this.userInfoService = userInfoService;
        this.knowledgeBase = knowledgeBase;
    }

    // ==================== 工具定义 ====================

    /** 暴露给大模型的工具清单（OpenAI tools 协议）。 */
    public List<ToolDef> toolDefs() {
        List<ToolDef> defs = new ArrayList<>();

        defs.add(new ToolDef(
                "search_recipes",
                "按关键词或分类搜索站内菜谱。当用户想找某道菜、某类菜，或某个分类下的菜谱时调用。",
                objectSchema(props(
                        "keyword", stringSchema("搜索关键词，例如「红烧肉」「汤」；无明确关键词可不传"),
                        "classifyId", integerSchema("分类ID，取自 list_classifications；不筛选分类可不传"),
                        "pageNum", integerSchema("页码，从 1 开始，默认 1"),
                        "pageSize", integerSchema("每页条数，默认 5")
                ), List.of())
        ));

        defs.add(new ToolDef(
                "get_recipe_detail",
                "根据菜谱ID获取该菜谱的完整信息（含做法步骤与小贴士）。当用户问某道菜的具体做法时调用。",
                objectSchema(props(
                        "recipeId", integerSchema("菜谱ID，来自 search_recipes 的返回")
                ), List.of("recipeId"))
        ));

        defs.add(new ToolDef(
                "list_classifications",
                "列出站内所有菜谱分类及其描述。当用户想了解有哪些分类，或需要分类ID时调用。",
                objectSchema(props(), List.of())
        ));

        defs.add(new ToolDef(
                "search_materials",
                "按关键词搜索站内食材库，返回食材名称与介绍。",
                objectSchema(props(
                        "keyword", stringSchema("食材关键词，例如「土豆」「牛肉」；不传则返回全部")
                ), List.of())
        ));

        defs.add(new ToolDef(
                "recommend_recipes",
                "根据用户手头已有的食材推荐可做的菜谱。当用户说「我有XX和YY，能做什么菜」时调用。",
                objectSchema(props(
                        "ingredients", stringArraySchema("用户拥有的食材名称列表，例如 [\"土豆\",\"牛肉\"]")
                ), List.of("ingredients"))
        ));

        defs.add(new ToolDef(
                "get_site_stats",
                "获取本站统计数据：菜谱数、食材数、分类数、管理员数、注册用户数。",
                objectSchema(props(), List.of())
        ));

        defs.add(new ToolDef(
                "search_knowledge",
                "从本站知识库（菜谱资料、食材介绍、分类说明）中检索与问题最相关的资料，"
                        + "用于回答烹饪常识、食材搭配等知识性问题。",
                objectSchema(props(
                        "question", stringSchema("要检索的问题或关键词")
                ), List.of("question"))
        ));

        return defs;
    }

    // ==================== 工具执行（Observation） ====================

    /** 执行工具并返回 JSON 字符串结果。 */
    public String dispatch(String name, Map<String, Object> args) {
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        try {
            switch (name == null ? "" : name) {
                case "search_recipes":
                    return searchRecipes(safeArgs);
                case "get_recipe_detail":
                    return getRecipeDetail(safeArgs);
                case "list_classifications":
                    return listClassifications();
                case "search_materials":
                    return searchMaterials(safeArgs);
                case "recommend_recipes":
                    return recommendRecipes(safeArgs);
                case "get_site_stats":
                    return getSiteStats();
                case "search_knowledge":
                    return searchKnowledge(safeArgs);
                default:
                    return jsonError("未知工具：" + name);
            }
        } catch (Exception e) {
            return jsonError("工具执行失败：" + e.getMessage());
        }
    }

    private String searchRecipes(Map<String, Object> args) throws Exception {
        String keyword = str(args.get("keyword"));
        Long classifyId = longValue(args.get("classifyId"));
        int pageNum = intValue(args.get("pageNum"), 1);
        int pageSize = intValue(args.get("pageSize"), 5);

        Page<FoodsMenuInfo> page = foodsMenuInfoService.findPage(keyword, classifyId, pageNum, pageSize);
        List<Map<String, Object>> items = new ArrayList<>();
        for (FoodsMenuInfo m : page.getRecords()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.getId());
            o.put("name", m.getName());
            o.put("tips", m.getTips());
            o.put("classifyId", m.getClassifyId());
            o.put("userName", m.getUserName());
            o.put("description", truncate(m.getDescription(), 120));
            items.add(o);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotal());
        result.put("items", items);
        return mapper.writeValueAsString(result);
    }

    private String getRecipeDetail(Map<String, Object> args) throws Exception {
        Long id = longValue(args.get("recipeId"));
        if (id == null) {
            return jsonError("缺少 recipeId 参数");
        }
        FoodsMenuInfo m = foodsMenuInfoService.findById(id);
        if (m == null) {
            return jsonError("未找到 ID 为 " + id + " 的菜谱");
        }
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", m.getId());
        o.put("name", m.getName());
        o.put("tips", m.getTips());
        o.put("classifyId", m.getClassifyId());
        o.put("userName", m.getUserName());
        o.put("description", m.getDescription());
        o.put("fileId", m.getFileId());
        return mapper.writeValueAsString(o);
    }

    private String listClassifications() throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ClassifyInfo c : classifyInfoService.findAll()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", c.getId());
            o.put("name", c.getName());
            o.put("description", c.getDescription());
            items.add(o);
        }
        return mapper.writeValueAsString(items);
    }

    private String searchMaterials(Map<String, Object> args) throws Exception {
        String keyword = str(args.get("keyword"));
        String kw = keyword == null ? "" : keyword.toLowerCase();
        List<Map<String, Object>> items = new ArrayList<>();
        for (FoodsMaterialInfo m : foodsMaterialInfoService.findAll()) {
            String text = (safe(m.getName()) + " " + safe(m.getDescription())).toLowerCase();
            if (!kw.isBlank() && !text.contains(kw)) {
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.getId());
            o.put("name", m.getName());
            o.put("description", m.getDescription());
            o.put("fileId", m.getFileId());
            items.add(o);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", items.size());
        result.put("items", items);
        return mapper.writeValueAsString(result);
    }

    private String recommendRecipes(Map<String, Object> args) throws Exception {
        List<String> ingredients = toStringList(args.get("ingredients"));
        if (ingredients.isEmpty()) {
            return jsonError("请提供 ingredients（食材列表）");
        }
        List<Map<String, Object>> scored = new ArrayList<>();
        for (FoodsMenuInfo r : foodsMenuInfoService.findAll()) {
            String text = (safe(r.getName()) + " " + safe(r.getTips()) + " " + safe(r.getDescription())).toLowerCase();
            List<String> matched = new ArrayList<>();
            for (String ing : ingredients) {
                if (!ing.isBlank() && text.contains(ing.toLowerCase())) {
                    matched.add(ing);
                }
            }
            if (matched.isEmpty()) {
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", r.getId());
            o.put("name", r.getName());
            o.put("tips", r.getTips());
            o.put("matchedIngredients", matched);
            o.put("matchCount", matched.size());
            o.put("description", truncate(r.getDescription(), 120));
            scored.add(o);
        }
        scored.sort((a, b) -> Integer.compare(
                ((Number) b.get("matchCount")).intValue(),
                ((Number) a.get("matchCount")).intValue()));

        List<Map<String, Object>> top = scored.size() > 5 ? new ArrayList<>(scored.subList(0, 5)) : scored;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ingredients", ingredients);
        result.put("total", scored.size());
        result.put("items", top);
        return mapper.writeValueAsString(result);
    }

    private String getSiteStats() throws Exception {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("recipeCount", foodsMenuInfoService.findAll().size());
        stats.put("materialCount", foodsMaterialInfoService.findAll().size());
        stats.put("classifyCount", classifyInfoService.findAll().size());
        stats.put("adminCount", adminInfoService.findAll().size());
        stats.put("userCount", userInfoService.findAll().size());
        return mapper.writeValueAsString(stats);
    }

    private String searchKnowledge(Map<String, Object> args) throws Exception {
        String question = str(args.get("question"));
        if (question == null || question.isBlank()) {
            return jsonError("缺少 question 参数");
        }
        List<String> hits = knowledgeBase.retrieve(question);
        if (hits.isEmpty()) {
            return jsonError("知识库中未检索到与该问题相关的内容");
        }
        return mapper.writeValueAsString(hits);
    }

    // ==================== 离线模式的自然语言摘要 ====================

    /** 供离线规则模式使用：把菜谱搜索结果转成人类可读文本。 */
    public String humanSearchRecipes(String keyword, int limit) {
        try {
            Page<FoodsMenuInfo> page = foodsMenuInfoService.findPage(keyword, null, 1, Math.max(1, limit));
            if (page.getRecords().isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (FoodsMenuInfo m : page.getRecords()) {
                sb.append("• ").append(m.getName());
                if (isNotBlank(m.getTips())) {
                    sb.append("（").append(m.getTips()).append("）");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 供离线规则模式使用：把食材搜索结果转成人类可读文本。 */
    public String humanSearchMaterials(String keyword, int limit) {
        try {
            String kw = keyword == null ? "" : keyword.toLowerCase();
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (FoodsMaterialInfo m : foodsMaterialInfoService.findAll()) {
                String text = (safe(m.getName()) + " " + safe(m.getDescription())).toLowerCase();
                if (!kw.isBlank() && !text.contains(kw)) {
                    continue;
                }
                sb.append("• ").append(m.getName());
                if (isNotBlank(m.getDescription())) {
                    sb.append("：").append(truncate(m.getDescription(), 40));
                }
                sb.append("\n");
                if (++count >= Math.max(1, limit)) {
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 供离线规则模式使用：分类列表摘要。 */
    public String humanListClassifications() {
        try {
            StringBuilder sb = new StringBuilder();
            for (ClassifyInfo c : classifyInfoService.findAll()) {
                sb.append("• ").append(c.getName());
                if (isNotBlank(c.getDescription())) {
                    sb.append("：").append(c.getDescription());
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 供离线规则模式使用：站点统计摘要。 */
    public String humanSiteStats() {
        try {
            return "菜谱 " + foodsMenuInfoService.findAll().size()
                    + " 道、食材 " + foodsMaterialInfoService.findAll().size()
                    + " 种、分类 " + classifyInfoService.findAll().size()
                    + " 个、注册用户 " + userInfoService.findAll().size()
                    + " 人、管理员 " + adminInfoService.findAll().size() + " 人";
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== 工具参数 Schema 构造 ====================

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private static Map<String, Object> props(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> stringSchema(String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", desc);
        return m;
    }

    private static Map<String, Object> integerSchema(String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "integer");
        m.put("description", desc);
        return m;
    }

    private static Map<String, Object> stringArraySchema(String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "array");
        m.put("description", desc);
        m.put("items", stringSchema("食材名称"));
        return m;
    }

    // ==================== 通用工具方法 ====================

    private String jsonError(String message) {
        try {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("error", message);
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"error\":\"" + message + "\"}";
        }
    }

    private static List<String> toStringList(Object v) {
        List<String> out = new ArrayList<>();
        if (v == null) {
            return out;
        }
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !o.toString().isBlank()) {
                    out.add(o.toString().trim());
                }
            }
        } else {
            for (String s : v.toString().split("[,，、\\s]+")) {
                if (!s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? null : v.toString().trim();
    }

    private static int intValue(Object v, int defaultValue) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ignored) {
                // 使用默认值
            }
        }
        return defaultValue;
    }

    private static Long longValue(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v != null && !v.toString().isBlank()) {
            try {
                return Long.parseLong(v.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
