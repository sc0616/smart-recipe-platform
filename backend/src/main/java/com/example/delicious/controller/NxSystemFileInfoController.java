package com.example.delicious.controller;

import cn.hutool.core.io.FileUtil;
import com.example.delicious.common.Result;
import com.example.delicious.common.exception.CustomException;
import com.example.delicious.entity.NxSystemFileInfo;
import com.example.delicious.service.NxSystemFileInfoService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import net.coobird.thumbnailator.Thumbnails;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/files")
public class NxSystemFileInfoController {

    @Value("${file.upload-path}")
    private String uploadPath;

    @Resource
    private NxSystemFileInfoService nxSystemFileInfoService;

    @PostConstruct
    public void init() {
        FileUtil.mkdir(uploadDir());
    }

    /**
     * 统一的路径拼接入口。
     * <p>
     * 使用 File 对象拼接而非字符串相加，因此配置项结尾带不带 "/" 都不会出错
     * （原实现 `uploadPath + fileName` 强依赖结尾斜杠，是易错点）。
     */
    private File resolveFile(String fileName) {
        return new File(uploadDir(), fileName);
    }

    private File uploadDir() {
        return new File(uploadPath);
    }

    @PostMapping("/upload")
    public Result<NxSystemFileInfo> upload(MultipartFile file) throws IOException {
        String originName = file.getOriginalFilename();
        String ext = FileUtil.extName(originName);
        String fileName = FileUtil.mainName(originName) + System.currentTimeMillis() + "." + ext;
        FileUtil.writeBytes(file.getBytes(), resolveFile(fileName));

        NxSystemFileInfo info = new NxSystemFileInfo();
        info.setOriginName(originName);
        info.setFileName(fileName);
        nxSystemFileInfoService.add(info);
        return Result.success(info);
    }

    @PostMapping("/image/upload")
    public Result<Map<String, String>> imageUpload(MultipartFile file) throws IOException {
        String originName = file.getOriginalFilename();
        String ext = FileUtil.extName(originName);
        String fileName = FileUtil.mainName(originName) + System.currentTimeMillis() + "." + ext;
        Thumbnails.of(file.getInputStream()).width(400).toFile(resolveFile(fileName));

        NxSystemFileInfo info = new NxSystemFileInfo();
        info.setOriginName(originName);
        info.setFileName(fileName);
        nxSystemFileInfoService.add(info);

        Map<String, String> map = new HashMap<>(2);
        map.put("id", String.valueOf(info.getId()));
        map.put("src", "/files/download/" + info.getId());
        map.put("title", originName);
        return Result.success(map);
    }

    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        NxSystemFileInfo info = nxSystemFileInfoService.findById(id);
        if (info == null) throw new CustomException("1001", "文件不存在");
        File target = resolveFile(info.getFileName());
        if (!target.exists()) throw new CustomException("1001", "文件已丢失");
        byte[] bytes = FileUtil.readBytes(target);
        response.reset();
        response.addHeader("Content-Disposition",
                "inline;filename=" + URLEncoder.encode(info.getOriginName(), StandardCharsets.UTF_8));
        response.addHeader("Content-Length", String.valueOf(bytes.length));
        // 按扩展名推断真实 MIME，避免 PNG 被当成 JPEG 返回
        response.setContentType(guessContentType(info.getFileName()));
        OutputStream out = new BufferedOutputStream(response.getOutputStream());
        out.write(bytes);
        out.flush();
    }

    private String guessContentType(String fileName) {
        String ext = FileUtil.extName(fileName);
        if (ext == null) return "application/octet-stream";
        switch (ext.toLowerCase()) {
            case "png":  return "image/png";
            case "gif":  return "image/gif";
            case "webp": return "image/webp";
            case "bmp":  return "image/bmp";
            case "svg":  return "image/svg+xml";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "pdf":  return "application/pdf";
            default:     return "application/octet-stream";
        }
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        NxSystemFileInfo info = nxSystemFileInfoService.findById(id);
        if (info == null) throw new CustomException("1001", "文件不存在");
        FileUtil.del(resolveFile(info.getFileName()));
        nxSystemFileInfoService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<NxSystemFileInfo> getById(@PathVariable Long id) {
        NxSystemFileInfo info = nxSystemFileInfoService.findById(id);
        if (info == null) throw new CustomException("1001", "文件不存在");
        return Result.success(info);
    }
}