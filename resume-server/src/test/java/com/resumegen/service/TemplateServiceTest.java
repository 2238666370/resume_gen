package com.resumegen.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import com.resumegen.common.PageResult;
import com.resumegen.dto.TemplateCreateRequest;
import com.resumegen.dto.TemplateUpdateRequest;
import com.resumegen.dto.TemplateVO;
import com.resumegen.entity.Template;
import com.resumegen.mapper.TemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private TemplateMapper templateMapper;

    private TemplateService service;

    @BeforeEach
    void setUp() {
        service = new TemplateService(templateMapper, new SchemaValidator(new ObjectMapper()));
    }

    private Template t(long id, String code) {
        Template t = new Template();
        t.setId(id);
        t.setCode(code);
        t.setName("测试");
        t.setType("official");
        t.setCategory("分类");
        t.setSortOrder(0);
        t.setStatus(1);
        return t;
    }

    private TemplateCreateRequest createReq(String code) {
        TemplateCreateRequest r = new TemplateCreateRequest();
        r.setCode(code);
        r.setName("模板名");
        return r;
    }

    @Test
    void listPublicReturnsOnlineTemplates() {
        when(templateMapper.selectList(any())).thenReturn(List.of(t(1L, "classic")));

        List<TemplateVO> list = service.listPublic();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo("1");
        assertThat(list.get(0).getCode()).isEqualTo("classic");
    }

    @Test
    void getByCodeReturnsVO() {
        when(templateMapper.selectOne(any())).thenReturn(t(2L, "modern"));

        TemplateVO vo = service.getByCode("modern");

        assertThat(vo.getCode()).isEqualTo("modern");
        assertThat(vo.getStatus()).isEqualTo(1);
    }

    @Test
    void getByCodeUnknownThrowsNotFound() {
        when(templateMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getByCode("nope"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void createSetsDefaultsAndInserts() {
        when(templateMapper.selectCount(any())).thenReturn(0L);

        TemplateVO vo = service.create(createReq("new-one"));

        assertThat(vo.getCode()).isEqualTo("new-one");
        assertThat(vo.getType()).isEqualTo("official");
        assertThat(vo.getStatus()).isEqualTo(1);

        ArgumentCaptor<Template> captor = ArgumentCaptor.forClass(Template.class);
        verify(templateMapper).insert(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("new-one");
        assertThat(captor.getValue().getType()).isEqualTo("official");
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        assertThat(captor.getValue().getSortOrder()).isEqualTo(0);
    }

    @Test
    void createDuplicateCodeThrowsConflict() {
        when(templateMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(createReq("classic")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);
    }

    @Test
    void updatePartiallyModifiesFields() {
        Template existing = t(3L, "minimal");
        existing.setStatus(1);
        when(templateMapper.selectById(3L)).thenReturn(existing);

        TemplateUpdateRequest req = new TemplateUpdateRequest();
        req.setName("极简");
        req.setStatus(0);

        TemplateVO vo = service.update(3L, req);

        assertThat(vo.getName()).isEqualTo("极简");
        assertThat(vo.getStatus()).isEqualTo(0);
        assertThat(existing.getCode()).isEqualTo("minimal");
        verify(templateMapper).updateById(existing);
    }

    @Test
    void deleteUnknownThrowsNotFound() {
        when(templateMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void adminListReturnsPage() {
        Page<Template> pg = new Page<>(1, 10);
        pg.setRecords(List.of(t(1L, "classic")));
        pg.setTotal(1);
        when(templateMapper.selectPage(any(), any())).thenReturn(pg);

        PageResult<TemplateVO> r = service.adminList(1, 10);

        assertThat(r.getTotal()).isEqualTo(1);
        assertThat(r.getRecords()).hasSize(1);
        assertThat(r.getRecords().get(0).getCode()).isEqualTo("classic");
    }
}