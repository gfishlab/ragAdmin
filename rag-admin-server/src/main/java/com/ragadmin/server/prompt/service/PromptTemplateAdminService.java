package com.ragadmin.server.prompt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.prompt.dto.PromptTemplateResponse;
import com.ragadmin.server.prompt.dto.PromptTemplateUpdateRequest;
import com.ragadmin.server.prompt.entity.AiPromptTemplateEntity;
import com.ragadmin.server.prompt.mapper.AiPromptTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PromptTemplateAdminService {

    @Autowired
    private AiPromptTemplateMapper promptTemplateMapper;

    public PageResponse<PromptTemplateResponse> listTemplates(
            String templateCode,
            String capabilityType,
            String status,
            long pageNo,
            long pageSize
    ) {
        LambdaQueryWrapper<AiPromptTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(templateCode != null && !templateCode.isBlank(), AiPromptTemplateEntity::getTemplateCode, templateCode);
        wrapper.eq(capabilityType != null && !capabilityType.isBlank(), AiPromptTemplateEntity::getCapabilityType, capabilityType);
        wrapper.eq(status != null && !status.isBlank(), AiPromptTemplateEntity::getStatus, status);
        wrapper.orderByDesc(AiPromptTemplateEntity::getTemplateCode)
               .orderByDesc(AiPromptTemplateEntity::getVersionNo);

        Page<AiPromptTemplateEntity> page = promptTemplateMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        List<PromptTemplateResponse> records = page.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(records, pageNo, pageSize, page.getTotal());
    }

    public PromptTemplateResponse getTemplate(Long id) {
        AiPromptTemplateEntity entity = promptTemplateMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Prompt template not found: " + id);
        }
        return toResponse(entity);
    }

    @Transactional
    public PromptTemplateResponse updateTemplate(Long id, PromptTemplateUpdateRequest request) {
        AiPromptTemplateEntity current = promptTemplateMapper.selectById(id);
        if (current == null) {
            throw new IllegalArgumentException("Prompt template not found: " + id);
        }

        AiPromptTemplateEntity newVersion = new AiPromptTemplateEntity();
        newVersion.setTemplateCode(current.getTemplateCode());
        newVersion.setTemplateName(request.templateName());
        newVersion.setCapabilityType(current.getCapabilityType());
        newVersion.setPromptContent(request.promptContent());
        newVersion.setVersionNo(current.getVersionNo() + 1);
        newVersion.setStatus(request.status() != null ? request.status() : current.getStatus());
        newVersion.setDescription(request.description() != null ? request.description() : current.getDescription());
        newVersion.setCreatedAt(LocalDateTime.now());
        newVersion.setUpdatedAt(LocalDateTime.now());
        promptTemplateMapper.insert(newVersion);

        return toResponse(newVersion);
    }

    private PromptTemplateResponse toResponse(AiPromptTemplateEntity entity) {
        return new PromptTemplateResponse(
                entity.getId(),
                entity.getTemplateCode(),
                entity.getTemplateName(),
                entity.getCapabilityType(),
                entity.getPromptContent(),
                entity.getVersionNo(),
                entity.getStatus(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
