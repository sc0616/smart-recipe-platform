package com.example.delicious.agent.knowledge;

import com.example.delicious.agent.config.AgentProperties;
import com.example.delicious.agent.llm.OpenAiCompatibleClient;
import com.example.delicious.entity.ClassifyInfo;
import com.example.delicious.entity.FoodsMaterialInfo;
import com.example.delicious.entity.FoodsMenuInfo;
import com.example.delicious.service.ClassifyInfoService;
import com.example.delicious.service.FoodsMaterialInfoService;
import com.example.delicious.service.FoodsMenuInfoService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 菜谱知识库（RAG：Retrieval-Augmented Generation）。
 * <p>
 * 启动时把数据库中的菜谱、食材、分类切片为知识条目；检索时：
 * <ul>
 *   <li>配置了向量模型 → 语义检索（Embedding + 余弦相似度）；</li>
 *   <li>未配置 → 关键词检索（汉字二元组 + 英文词），保证零依赖也可用。</li>
 * </ul>
 * 语义检索失败会自动回退到关键词检索，因此不会因为向量服务异常而中断对话。
 */
@Service
public class RecipeKnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(RecipeKnowledgeBase.class);

    private final FoodsMenuInfoService foodsMenuInfoService;
    private final FoodsMaterialInfoService foodsMaterialInfoService;
    private final ClassifyInfoService classifyInfoService;
    private final OpenAiCompatibleClient client;
    private final AgentProperties props;

    /** 知识切片（内存态，规模与站内数据同阶） */
    private final List<String> chunks = new CopyOnWriteArrayList<>();
    /** 与 chunks 一一对应的向量；为 null 表示尚未向量化 */
    private volatile List<float[]> chunkEmbeddings = null;
    private final Object embedLock = new Object();

    public RecipeKnowledgeBase(FoodsMenuInfoService foodsMenuInfoService,
                               FoodsMaterialInfoService foodsMaterialInfoService,
                               ClassifyInfoService classifyInfoService,
                               OpenAiCompatibleClient client,
                               AgentProperties props) {
        this.foodsMenuInfoService = foodsMenuInfoService;
        this.foodsMaterialInfoService = foodsMaterialInfoService;
        this.classifyInfoService = classifyInfoService;
        this.client = client;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        try {
            reindex();
        } catch (Exception e) {
            log.warn("知识库初始化失败（不影响启动）：{}", e.getMessage());
        }
    }

    /** 重建知识切片。菜谱/食材/分类发生变更后可调用以刷新。 */
    public synchronized void reindex() {
        List<String> rebuilt = new ArrayList<>();

        for (ClassifyInfo c : classifyInfoService.findAll()) {
            rebuilt.add("菜谱分类【" + safe(c.getName()) + "】：" + safe(c.getDescription()));
        }
        for (FoodsMaterialInfo m : foodsMaterialInfoService.findAll()) {
            rebuilt.add("食材【" + safe(m.getName()) + "】：" + safe(m.getDescription()));
        }
        for (FoodsMenuInfo r : foodsMenuInfoService.findAll()) {
            StringBuilder sb = new StringBuilder();
            sb.append("菜谱【").append(safe(r.getName())).append("】");
            if (isNotBlank(r.getTips())) {
                sb.append("，小贴士：").append(r.getTips());
            }
            if (isNotBlank(r.getDescription())) {
                sb.append("，做法：").append(r.getDescription());
            }
            if (isNotBlank(r.getUserName())) {
                sb.append("，发布者：").append(r.getUserName());
            }
            rebuilt.add(sb.toString());
        }

        chunks.clear();
        chunks.addAll(rebuilt);
        chunkEmbeddings = null;
        log.info("菜谱知识库已重建，共 {} 条知识切片（检索模式：{}）",
                chunks.size(), client.embeddingEnabled() ? "语义+关键词" : "关键词");
    }

    public int chunkCount() {
        return chunks.size();
    }

    /** 使用配置中的 topK 检索。 */
    public List<String> retrieve(String query) {
        return retrieve(query, props.getRagTopK());
    }

    /** 检索与 query 最相关的知识切片。 */
    public List<String> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>();
        }
        if (chunks.isEmpty()) {
            reindex();
        }
        if (client.embeddingEnabled()) {
            return semanticRetrieve(query, topK);
        }
        return keywordRetrieve(query, topK);
    }

    private List<String> semanticRetrieve(String query, int topK) {
        try {
            ensureEmbeddings();
            List<float[]> embeddings = chunkEmbeddings;
            if (embeddings == null || embeddings.isEmpty()) {
                return keywordRetrieve(query, topK);
            }
            List<float[]> queryVec = client.embed(List.of(query));
            if (queryVec.isEmpty()) {
                return keywordRetrieve(query, topK);
            }
            float[] q = queryVec.get(0);

            List<Double> scores = new ArrayList<>();
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < embeddings.size(); i++) {
                indexes.add(i);
                scores.add(cosine(q, embeddings.get(i)));
            }
            List<Integer> order = new ArrayList<>(indexes);
            order.sort(Comparator.comparingDouble((Integer i) -> scores.get(i)).reversed());

            List<String> out = new ArrayList<>();
            int limit = Math.min(topK, order.size());
            for (int i = 0; i < limit; i++) {
                Double s = scores.get(order.get(i));
                if (s != null && s > 0.15) {
                    out.add(chunks.get(order.get(i)));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("语义检索失败，回退关键词检索：{}", e.getMessage());
            return keywordRetrieve(query, topK);
        }
    }

    private void ensureEmbeddings() throws Exception {
        if (chunkEmbeddings == null) {
            synchronized (embedLock) {
                if (chunkEmbeddings == null) {
                    log.info("开始为 {} 条知识切片生成向量...", chunks.size());
                    chunkEmbeddings = client.embed(new ArrayList<>(chunks));
                    log.info("知识切片向量化完成");
                }
            }
        }
    }

    private List<String> keywordRetrieve(String query, int topK) {
        List<Integer> order = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            order.add(i);
            scores.add(keywordScore(query, chunks.get(i)));
        }
        order.sort(Comparator.comparingDouble((Integer i) -> scores.get(i)).reversed());

        List<String> out = new ArrayList<>();
        int limit = Math.min(topK, order.size());
        for (int i = 0; i < limit; i++) {
            double s = scores.get(order.get(i));
            if (s > 0) {
                out.add(chunks.get(order.get(i)));
            }
        }
        return out;
    }

    /** 关键词打分：整串命中 > 二元组命中 > 单字/英文词命中。 */
    private double keywordScore(String query, String text) {
        if (query == null || query.isBlank() || text == null) {
            return 0;
        }
        String q = query.toLowerCase();
        String t = text.toLowerCase();
        double score = 0;

        if (t.contains(q)) {
            score += 6.0;
        }
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                continue;
            }
            if (t.indexOf(c) >= 0) {
                score += 0.5;
            }
            if (i + 1 < q.length()) {
                String bigram = q.substring(i, i + 2);
                if (t.contains(bigram)) {
                    score += 1.5;
                }
            }
        }
        for (String w : q.split("[^a-zA-Z0-9]+")) {
            if (w.length() >= 2 && t.contains(w)) {
                score += 2.0;
            }
        }
        return score;
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
