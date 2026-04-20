package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.document.mapper.DocumentVersionMapper;
import com.ragadmin.server.document.support.ChunkVectorizationService;
import com.ragadmin.server.document.support.DocumentVectorizationProperties;
import com.ragadmin.server.document.support.DocumentVectorizationStrategyResolver;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.task.mapper.TaskStepRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.document.Document;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentParseProcessorTest {

    @Mock
    private DocumentParseTaskMapper documentParseTaskMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentVersionMapper documentVersionMapper;

    @Mock
    private ChunkMapper chunkMapper;

    @Mock
    private DocumentContentExtractor documentContentExtractor;

    @Mock
    private DefaultDocumentCleaner documentCleaner;

    @Mock
    private TaskStepRecordMapper taskStepRecordMapper;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private ChunkVectorizationService chunkVectorizationService;

    @Mock
    private DocumentVectorizationStrategyResolver strategyResolver;

    @Spy
    @InjectMocks
    private DocumentParseProcessor documentParseProcessor;

    @Test
    void shouldSkipTaskWhenTaskStatusChanged() {
        doThrow(new IllegalStateException("任务不存在或状态已变更"))
                .when(documentParseProcessor)
                .markRunning(13L);

        documentParseProcessor.processSingleTask(13L);

        verify(documentParseProcessor, never()).markFailed(eq(13L), any());
        verify(documentParseTaskMapper, never()).selectById(13L);
    }

    @Test
    void shouldSplitDocumentsByProviderStrategyAndKeepMetadata() {
        String content = """
                %s

                %s

                %s
                """.formatted("a".repeat(220), "b".repeat(220), "c".repeat(220));

        List<DocumentParseProcessor.ChunkDraft> chunks = documentParseProcessor.splitIntoChunks(
                List.of(new Document(content, Map.of("pageNo", 3, "readerType", "TIKA", "parseMode", "TEXT"))),
                new DocumentVectorizationProperties.StrategyProperties(1, 400, 80)
        );

        assertEquals(3, chunks.size());
        assertEquals(220, chunks.getFirst().text().length());
        assertEquals(302, chunks.get(1).text().length());
        assertEquals(302, chunks.get(2).text().length());
        assertEquals(3, chunks.getFirst().metadata().get("pageNo"));
        assertEquals("TIKA", chunks.getFirst().metadata().get("readerType"));
        assertEquals(0, chunks.getFirst().metadata().get("chunkIndex"));
        assertEquals(3, chunks.getFirst().metadata().get("totalChunks"));
    }

    @Test
    void shouldNotBreakWordsInOverlap() {
        String para1 = "Java后端开发经验，主力方向为企业级业务系统、微服务与中间件能力建设。" +
                "熟悉Spring Boot、Spring Cloud、MyBatis Plus等主流框架。" +
                "近一年开始负责AI应用落地，基于Spring AI Alibaba搭建企业RAG系统。" +
                "掌握Redis、Kafka、RabbitMQ等中间件，以及Docker、Jenkins等工程化工具。";
        String para2 = "# 工作经历\n\n江阴众和电力仪表有限公司 · AI 全栈工程师\n2024-08 - 2026-03\n" +
                "负责公司信息化与智能化建设，先后主导交付电表质量售后系统与HR人力资源管理系统。";
        String para3 = "# 技能栈\n\n后端 (7年): Java, Spring Boot/Cloud, MyBatis Plus, 微服务架构设计\n" +
                "AI应用落地(近1年): Spring AI, RAG, Agent, Embedding, Prompt Engineering\n" +
                "中间件: Redis, Kafka, RabbitMQ\n前端协同: Vue3, React, JavaScript";

        String content = para1 + "\n\n" + para2 + "\n\n" + para3;

        List<DocumentParseProcessor.ChunkDraft> chunks = documentParseProcessor.splitIntoChunks(
                List.of(new Document(content, Map.of())),
                new DocumentVectorizationProperties.StrategyProperties(1, 200, 80)
        );

        assertTrue(chunks.size() > 1);
        for (int i = 1; i < chunks.size(); i++) {
            String text = chunks.get(i).text();
            String overlapPart = text.split("\n\n")[0];
            if (!overlapPart.isBlank()) {
                char first = overlapPart.charAt(0);
                assertTrue(Character.isLetterOrDigit(first) || first == '#' || first == '（' || first == '(',
                        "切片 " + i + " 的重叠前缀不应从单词中间断开，实际开头: \"" + overlapPart.substring(0, Math.min(20, overlapPart.length())) + "\"");
            }
        }
    }

    @Test
    void shouldSnapToWordBoundaryInOverlap() {
        String para1 = "MongoDB Redis Kafka RabbitMQ Docker Jenkins GitLab Prometheus Grafana Nginx Linux " +
                "SpringBoot SpringCloud MyBatis Vue3 React JavaScript TypeScript PostgreSQL MySQL";
        String para2 = "# 工作经历\n\n江阴众和电力仪表有限公司 AI全栈工程师";
        String para3 = "负责公司信息化建设，先后主导交付电表质量售后系统与HR管理系统。" +
                "掌握Redis Kafka等中间件，以及Docker Jenkins等工程化工具。";
        String content = para1 + "\n\n" + para2 + "\n\n" + para3;

        List<DocumentParseProcessor.ChunkDraft> chunks = documentParseProcessor.splitIntoChunks(
                List.of(new Document(content, Map.of())),
                new DocumentVectorizationProperties.StrategyProperties(1, 200, 80)
        );

        assertTrue(chunks.size() > 1);
        for (int i = 1; i < chunks.size(); i++) {
            String text = chunks.get(i).text();
            String overlapPart = text.split("\n\n")[0];
            if (!overlapPart.isBlank()) {
                assertFalse(overlapPart.startsWith("goDB") || overlapPart.startsWith("is") || overlapPart.startsWith("eus"),
                        "重叠前缀不应从单词中间断开，实际开头: \"" + overlapPart.substring(0, Math.min(30, overlapPart.length())) + "\"");
                char first = overlapPart.charAt(0);
                assertTrue(Character.isUpperCase(first) || Character.isDigit(first) || first == '#' || Character.isLetter(first),
                        "重叠前缀的首字符应是合法的词首字符，实际: " + first);
            }
        }
    }
}
