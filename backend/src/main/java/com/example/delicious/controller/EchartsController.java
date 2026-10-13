package com.example.delicious.controller;

import com.example.delicious.common.Result;
import com.example.delicious.entity.*;
import com.example.delicious.service.*;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/echarts")
public class EchartsController {

    @Resource
    private AdminInfoService adminInfoService;
    @Resource
    private UserInfoService userInfoService;
    @Resource
    private ClassifyInfoService classifyInfoService;
    @Resource
    private CollectInfoService collectInfoService;
    @Resource
    private PraiseInfoService praiseInfoService;

    @GetMapping("/get/{modelName}")
    public Result<List<Map<String, Object>>> getEchartsData(@PathVariable String modelName) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Integer> dataMap = new LinkedHashMap<>();
        String title = "";

        switch (modelName) {
            case "adminInfo" -> {
                title = "管理员";
                List<AdminInfo> list = adminInfoService.findAll();
                long male = list.stream().filter(x -> "男".equals(x.getSex())).count();
                dataMap.put("男", (int) male);
                dataMap.put("女", list.size() - (int) male);
            }
            case "userInfo" -> {
                title = "用户";
                List<UserInfo> list = userInfoService.findAll();
                long male = list.stream().filter(x -> "男".equals(x.getSex())).count();
                dataMap.put("男", (int) male);
                dataMap.put("女", list.size() - (int) male);
            }
            case "classifyInfo" -> {
                title = "菜谱分类";
                List<ClassifyInfo> list = classifyInfoService.findAll();
                for (ClassifyInfo item : list) {
                    dataMap.merge(item.getName(), 1, Integer::sum);
                }
            }
            case "collectInfo" -> {
                title = "收藏";
                List<CollectInfo> list = collectInfoService.findAll();
                for (CollectInfo item : list) {
                    dataMap.merge(item.getName(), 1, Integer::sum);
                }
            }
            case "praiseInfo" -> {
                title = "点赞";
                List<PraiseInfo> list = praiseInfoService.findAll();
                for (PraiseInfo item : list) {
                    dataMap.merge(item.getName(), 1, Integer::sum);
                }
            }
            default -> { return Result.success(result); }
        }

        result.add(buildPieData(title, dataMap));
        result.add(buildBarData(title, dataMap));
        return Result.success(result);
    }

    @GetMapping("/options")
    public Result<List<Map<String, String>>> getOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        String[][] items = {
            {"adminInfo", "管理员信息"},
            {"userInfo", "用户信息"},
            {"classifyInfo", "菜谱分类信息"},
            {"collectInfo", "收藏信息"},
            {"praiseInfo", "点赞信息"}
        };
        for (String[] item : items) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("value", item[0]);
            m.put("label", item[1]);
            options.add(m);
        }
        return Result.success(options);
    }

    private Map<String, Object> buildPieData(String name, Map<String, Integer> dataMap)     {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", "pie");
        Map<String, String> title = new HashMap<>();
        title.put("text", name + "信息");
        chart.put("title", title);
        List<Map<String, Object>> seriesData = new ArrayList<>();
        for (Map.Entry<String, Integer> e : dataMap.entrySet()) {
            Map<String, Object> d = new HashMap<>();
            d.put("name", e.getKey());
            d.put("value", e.getValue());
            seriesData.add(d);
        }
        chart.put("seriesData", seriesData);
        chart.put("legend", new ArrayList<>(dataMap.keySet()));
        return chart;
    }

    private Map<String, Object> buildBarData(String name, Map<String, Integer> dataMap) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", "bar");
        Map<String, String> title = new HashMap<>();
        title.put("text", name + "信息");
        chart.put("title", title);
        chart.put("xAxisData", new ArrayList<>(dataMap.keySet()));
        chart.put("seriesData", new ArrayList<>(dataMap.values()));
        chart.put("seriesName", name);
        return chart;
    }
}