package com.ragadmin.server.document.parser;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecursiveFallbackStrategyTest {

    private final RecursiveFallbackStrategy strategy = new RecursiveFallbackStrategy();

    private ChunkContext context(int maxChunkChars, int overlapChars) {
        return new ChunkContext(null, DocumentSignals.empty(), new ChunkStrategyProperties(maxChunkChars, overlapChars, 50));
    }

    @Test
    void shouldAlwaysSupport() {
        assertTrue(strategy.supports(context(800, 120)));
    }

    @Test
    void shouldSplitDocumentsAndKeepMetadata() {
        String content = """
                %s

                %s

                %s
                """.formatted("a".repeat(220), "b".repeat(220), "c".repeat(220));

        List<ChunkDraft> chunks = strategy.chunk(
                List.of(new Document(content, Map.of("pageNo", 3, "readerType", "TIKA", "parseMode", "TEXT"))),
                context(400, 80)
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

        List<ChunkDraft> chunks = strategy.chunk(
                List.of(new Document(content, Map.of())),
                context(200, 80)
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

        List<ChunkDraft> chunks = strategy.chunk(
                List.of(new Document(content, Map.of())),
                context(200, 80)
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
