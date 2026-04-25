package com.ragadmin.server.prompt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.prompt.entity.AiPromptTemplateEntity;
import com.ragadmin.server.prompt.mapper.AiPromptTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
public class PromptTemplateSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateSeeder.class);

    @Autowired
    private AiPromptTemplateMapper promptTemplateMapper;

    @Override
    public void run(String... args) throws Exception {
        Long count = promptTemplateMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }

        log.info("Seeding prompt templates from classpath...");
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:prompts/**/*.st");

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || !filename.endsWith(".st")) {
                continue;
            }

            String templateCode = filename.substring(0, filename.length() - 3);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            String path = resource.getURL().toString();
            String capabilityType = inferCapabilityType(path);
            String templateName = deriveTemplateName(templateCode);

            AiPromptTemplateEntity entity = new AiPromptTemplateEntity();
            entity.setTemplateCode(templateCode);
            entity.setTemplateName(templateName);
            entity.setCapabilityType(capabilityType);
            entity.setPromptContent(content);
            entity.setVersionNo(1);
            entity.setStatus("ENABLED");
            entity.setDescription("Auto-seeded from " + filename);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            promptTemplateMapper.insert(entity);
        }

        log.info("Seeded {} prompt templates.", resources.length);
    }

    private String inferCapabilityType(String path) {
        if (path.contains("ai/chat/")) {
            return "CHAT";
        }
        if (path.contains("ai/retrieval/")) {
            return "RETRIEVAL";
        }
        return null;
    }

    private String deriveTemplateName(String templateCode) {
        String[] parts = templateCode.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1))
                  .append(' ');
            }
        }
        return sb.toString().trim();
    }
}
