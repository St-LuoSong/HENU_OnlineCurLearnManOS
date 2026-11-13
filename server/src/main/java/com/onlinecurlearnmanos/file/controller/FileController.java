package com.onlinecurlearnmanos.file.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.file.dto.FileAssetResponse;
import com.onlinecurlearnmanos.file.dto.FilePreviewResponse;
import com.onlinecurlearnmanos.file.entity.FileAsset;
import com.onlinecurlearnmanos.file.preview.FilePreviewService;
import com.onlinecurlearnmanos.file.service.FileAssetService;
import com.onlinecurlearnmanos.file.service.FilePurpose;
import com.onlinecurlearnmanos.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件资产：上传、元数据、下载、内联内容、在线预览与删除。
 * 所有读取、下载、预览都先经过权限判定，不暴露任何物理路径。
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileAssetService fileAssetService;
    private final FilePreviewService filePreviewService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileAssetResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "PLATFORM_RESOURCE") String purpose,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                fileAssetService.upload(principal, file, FilePurpose.parse(purpose))
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<FileAssetResponse> metadata(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(fileAssetService.metadata(id, principal));
    }

    @GetMapping("/{id}/preview")
    public ApiResponse<FilePreviewResponse> preview(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(filePreviewService.preview(id, principal));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        FileAsset asset = fileAssetService.requireReadable(id, principal);
        fileAssetService.markDownloaded(id);
        return stream(asset, fileAssetService.openStream(asset), true);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        FileAsset asset = fileAssetService.requireReadable(id, principal);
        return stream(asset, fileAssetService.openStream(asset), false);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        fileAssetService.delete(id, principal);
        return ApiResponse.success(null);
    }

    private ResponseEntity<InputStreamResource> stream(
            FileAsset asset,
            InputStream inputStream,
            boolean attachment
    ) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(asset.getMimeType());
        } catch (RuntimeException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(asset.getOriginalName(), attachment))
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(asset.getSizeBytes()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, must-revalidate")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(inputStream));
    }

    /**
     * 同时给出 ASCII 回退名与 RFC 5987 的 UTF-8 文件名，兼容各浏览器并避免响应头注入。
     */
    private String disposition(String originalName, boolean attachment) {
        String safeName = originalName == null ? "file" : originalName;
        String ascii = safeName.replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return (attachment ? "attachment" : "inline")
                + "; filename=\"" + ascii + "\""
                + "; filename*=UTF-8''" + encoded;
    }
}
