package com.resumegen.repository;

import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeListItemVO;

import java.util.List;

/**
 * 简历存储抽象：mysql | json 双实现，业务层只依赖本接口。
 */
public interface ResumeRepository {

    /** 新建简历，返回带 id 的详情。 */
    ResumeDetailVO create(Long userId, ResumeDTO dto);

    /** 读取详情（含服务端 id/version/updatedAt）；不存在抛出 NOT_FOUND。 */
    ResumeDetailVO get(Long userId, String resumeId);

    /** 整体覆盖更新；expectedVersion 不一致返回 false（乐观锁），不存在抛 NOT_FOUND。 */
    boolean update(Long userId, String resumeId, ResumeDTO dto, Integer expectedVersion);

    /** 删除；不存在返回 false。 */
    boolean delete(Long userId, String resumeId);

    /** 分页列表（page 从 1 开始；keyword 按标题模糊匹配，可空）。 */
    List<ResumeListItemVO> list(Long userId, long page, long size, String keyword);

    /** 计数（keyword 同 list 口径，可空）。 */
    long count(Long userId, String keyword);

    /** 按 id 列表批量读取（ES 检索命中回源用），保持入参顺序。 */
    List<ResumeListItemVO> listByIds(Long userId, List<String> ids);
}