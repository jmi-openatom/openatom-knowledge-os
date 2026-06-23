package cn.jmi.openatom.knowledge.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RagControllerTest {
  @Autowired MockMvc mockMvc;

  @Test
  void returnsFallbackAnswerWhenKnowledgeBaseIsEmpty() throws Exception {
    mockMvc.perform(post("/api/rag/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\":\"如何策划迎新活动？\",\"scope\":\"活动策划\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").isString())
        .andExpect(jsonPath("$.sources").isArray());
  }

  @Test
  void formatReturnsOptimizedMarkdownString() throws Exception {
    mockMvc.perform(post("/api/rag/format")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"markdown\":\"###标题\\n-项目\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.markdown").isString());
  }
}
