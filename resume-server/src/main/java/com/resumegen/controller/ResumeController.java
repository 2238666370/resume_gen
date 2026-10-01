package com.resumegen.controller;

import com.resumegen.common.ApiResponse;
import com.resumegen.common.PageResult;
import com.resumegen.dto.ResumeCreateRequest;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ResumeListItemVO;
import com.resumegen.dto.ResumeSnapshotVO;
import com.resumegen.dto.ResumeVersionDiffVO;
import com.resumegen.security.UserContext;
import com.resumegen.service.ResumeService;
import com.resumegen.service.ResumeSnapshotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private static final long MAX_SIZE = 100;

    private final ResumeService resumeService;
    private final ResumeSnapshotService snapshotService;

    public ResumeController(ResumeService resumeService, ResumeSnapshotService snapshotService) {
        this.resumeService = resumeService;
        this.snapshotService = snapshotService;
    }

    @GetMapping
    public ApiResponse<PageResult<ResumeListItemVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        return ApiResponse.ok(resumeService.list(UserContext.userId(), page, size, keyword));
    }

    @PostMapping
    public ApiResponse<ResumeDetailVO> create(@Valid @RequestBody ResumeCreateRequest req) {
        return ApiResponse.ok(resumeService.create(UserContext.userId(), req.getTitle(), req.getTemplateId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeDetailVO> get(@PathVariable String id) {
        return ApiResponse.ok(resumeService.get(UserContext.userId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ResumeDetailVO> update(@PathVariable String id,
                                              @RequestParam(required = false) Integer version,
                                              @Valid @RequestBody ResumeDTO dto) {
        return ApiResponse.ok(resumeService.update(UserContext.userId(), id, dto, version));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ResumeDetailVO> patch(@PathVariable String id,
                                             @RequestParam(required = false) Integer version,
                                             @RequestBody String body) {
        return ApiResponse.ok(resumeService.patch(UserContext.userId(), id, body, version));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        resumeService.delete(UserContext.userId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<ResumeSnapshotVO>> history(@PathVariable String id) {
        return ApiResponse.ok(snapshotService.list(UserContext.userId(), id));
    }

    @GetMapping("/{id}/history/{snapshotId}")
    public ApiResponse<ResumeSnapshotVO> historyDetail(@PathVariable String id, @PathVariable Long snapshotId) {
        return ApiResponse.ok(snapshotService.get(UserContext.userId(), id, snapshotId));
    }

    @GetMapping("/{id}/diff")
    public ApiResponse<ResumeVersionDiffVO> diff(@PathVariable String id,
                                                 @RequestParam Long from,
                                                 @RequestParam Long to) {
        return ApiResponse.ok(snapshotService.diff(UserContext.userId(), id, from, to));
    }

    @PostMapping("/{id}/rollback/{snapshotId}")
    public ApiResponse<ResumeDetailVO> rollback(@PathVariable String id, @PathVariable Long snapshotId) {
        Long userId = UserContext.userId();
        ResumeDTO content = snapshotService.getContent(userId, id, snapshotId);
        int currentVersion = resumeService.get(userId, id).getVersion();
        return ApiResponse.ok(resumeService.update(userId, id, content, currentVersion, "rollback"));
    }

    @GetMapping("/{id}/export.json")
    public ResponseEntity<ResumeDTO> exportJson(@PathVariable String id) {
        ResumeDTO dto = resumeService.exportJson(UserContext.userId(), id);
        String filename = (dto.getTitle() == null || dto.getTitle().isBlank()
                ? "resume" : dto.getTitle()) + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(dto);
    }

    @PostMapping("/import")
    public ApiResponse<ResumeDetailVO> importJson(@Valid @RequestBody ResumeDTO dto) {
        return ApiResponse.ok(resumeService.importJson(UserContext.userId(), dto));
    }
}