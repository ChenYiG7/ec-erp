package com.own.erp.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.command.KbTextUploadCommand;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiKbDocument;
import com.own.erp.ai.kb.KbDocumentService;
import com.own.erp.ai.kb.KbIngestService;
import com.own.erp.ai.request.query.AiKbDocumentQuery;
import com.own.erp.ai.response.AiKbChunkResponse;
import com.own.erp.ai.response.AiKbDocumentResponse;
import com.own.erp.common.api.Result;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : AI 客服知识库入口(#6 RAG V1):
 *     读侧(列表/详情/分块预览)登录即可;写侧(上传/粘贴/删除/重建)admin 双闸——
 *     知识库是全局语料(影响所有用户 chat 检索),非个人数据,同 #18 系统设置口径
 *     (@PreAuthorize hasRole('admin') + 前端菜单收口)。
 *     契约接口注入一律 @Lazy 断构造环(docs/07 §2.2)
 */
@Tag(name = "AI知识库", description = "AI 客服 RAG 知识库:文档接入(文件/粘贴文本)/分块预览/删除/索引重建;"
        + "读侧登录即可,写侧限 admin")
@RestController
@RequestMapping("/api/ai/kb")
public class KbController {

    /** V1 允许的文件后缀(纯文本系;PDF/DOCX 随 Tika reader 扩容再评估,TODO(#6)) */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "md", "markdown");

    private final KbIngestService ingestService;
    private final KbDocumentService documentService;
    private final CurrentUserApi currentUserApi;
    private final ErpAiProperties props;

    public KbController(KbIngestService ingestService,
                        KbDocumentService documentService,
                        @Lazy CurrentUserApi currentUserApi,
                        ErpAiProperties props) {
        this.ingestService = ingestService;
        this.documentService = documentService;
        this.currentUserApi = currentUserApi;
        this.props = props;
    }

    @Operation(summary = "文档分页", description = "过滤:标题模糊/状态;按 id 倒序;登录即可")
    @GetMapping("/documents")
    public Result<Page<AiKbDocumentResponse>> page(AiKbDocumentQuery query) {
        return Result.ok(documentService.page(query));
    }

    @Operation(summary = "文档详情", description = "不存在报'文档不存在'")
    @GetMapping("/documents/{id}")
    public Result<AiKbDocumentResponse> detail(@PathVariable("id") Long id) {
        return Result.ok(documentService.getById(id));
    }

    @Operation(summary = "分块预览", description = "按块序正序全量返回,截断展示归前端")
    @GetMapping("/documents/{id}/chunks")
    public Result<List<AiKbChunkResponse>> chunks(@PathVariable("id") Long id) {
        return Result.ok(documentService.listChunks(id));
    }

    @Operation(summary = "上传文件接入", description = ".txt/.md/.markdown,UTF-8;上限取 erp.ai.kb.max-file-bytes;"
            + "写入侧限 admin")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('admin')")
    public Result<AiKbDocumentResponse> upload(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "title", required = false) String title) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.getSize() > props.getKb().getMaxFileBytes()) {
            throw new BusinessException(StrUtil.format("文件超过大小上限({} 字节)", props.getKb().getMaxFileBytes()));
        }
        String fileName = file.getOriginalFilename();
        String extension = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("仅支持 " + String.join("/", ALLOWED_EXTENSIONS) + " 纯文本文件");
        }
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("文件读取失败,请确认 UTF-8 编码后重试");
        }
        AiKbDocument doc = ingestService.ingest(title, AiConsts.KB_SOURCE_UPLOAD, fileName, text,
                currentUserApi.currentUserId());
        return Result.ok(AiKbDocumentResponse.from(doc));
    }

    @Operation(summary = "粘贴文本接入", description = "标题可空(默认内容首行截断);写入侧限 admin")
    @PostMapping("/documents/text")
    @PreAuthorize("hasRole('admin')")
    public Result<AiKbDocumentResponse> uploadText(@Valid @RequestBody KbTextUploadCommand command) {
        AiKbDocument doc = ingestService.ingest(command.title(), AiConsts.KB_SOURCE_TEXT, null,
                command.content(), currentUserApi.currentUserId());
        return Result.ok(AiKbDocumentResponse.from(doc));
    }

    @Operation(summary = "删除文档", description = "先删向量后删正本;写入侧限 admin")
    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result<Void> delete(@PathVariable("id") Long id) {
        documentService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "重建向量索引", description = "清空索引按 ai_kb_chunk 正本(READY 文档)重嵌入;"
            + "换 embedding 模型/索引文件丢失后使用;写入侧限 admin")
    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('admin')")
    public Result<Integer> rebuild() {
        return Result.ok(documentService.rebuildAll());
    }

    /** 后缀小写归一;无后缀返回不可能命中的空串走统一拒绝 */
    private String extensionOf(String fileName) {
        if (StrUtil.isBlank(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
