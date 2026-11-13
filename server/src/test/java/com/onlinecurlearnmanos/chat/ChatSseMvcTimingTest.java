package com.onlinecurlearnmanos.chat;

import com.onlinecurlearnmanos.chat.controller.ChatController;
import com.onlinecurlearnmanos.chat.service.ChatService;
import com.onlinecurlearnmanos.common.web.RestAccessDeniedHandler;
import com.onlinecurlearnmanos.common.web.RestAuthenticationEntryPoint;
import com.onlinecurlearnmanos.config.SecurityConfig;
import com.onlinecurlearnmanos.security.JwtService;
import com.onlinecurlearnmanos.security.TokenRevocationService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 Spring MVC 链路上的 SSE 首包时序回归。
 *
 * <p>{@code ChatServiceSseTimingTest} 只覆盖服务内部「先写 meta、再派发异步任务」的顺序；
 * 本测试补上 DispatcherServlet、返回值处理器到 SseEmitter 落盘的整条链路，断言：</p>
 * <ol>
 *     <li>连接建立阶段（上游尚未产出任何增量）就已经把 meta 帧写进响应体；</li>
 *     <li>上游增量到达后逐段可见，done 帧一定排在最后；</li>
 *     <li>响应头具备禁止代理与浏览器缓冲的语义。</li>
 * </ol>
 */
@WebMvcTest(controllers = ChatController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ChatSseMvcTimingTest {

    private static final long UPSTREAM_DELAY_MILLIS = 500L;
    private static final String REQUEST_ID = "req-test-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private TokenRevocationService tokenRevocationService;

    @MockitoBean
    private ChatService chatService;

    @Test
    void metaIsFlushedAtConnectTimeBeforeAnyUpstreamDelta() throws Exception {
        CountDownLatch firstDeltaSent = new CountDownLatch(1);
        given(chatService.streamMessage(anyLong(), anyLong(), any()))
                .willAnswer(invocation -> streamingEmitter(firstDeltaSent));

        MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions/9/stream")
                        .with(authentication(principalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"content\":\"什么是导数\",\"retry\":false}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        String atConnect = result.getResponse().getContentAsString();
        assertThat(firstDeltaSent.getCount())
                .as("断言这一刻上游还没产出任何增量，因此可见的帧只能来自连接建立阶段")
                .isEqualTo(1L);
        assertThat(atConnect)
                .contains("event:meta")
                .contains(REQUEST_ID)
                .doesNotContain("event:delta")
                .doesNotContain("event:done");

        String completed = awaitContent(result, "event:done");
        assertThat(completed)
                .contains("event:meta")
                .contains("第一段")
                .contains("第二段")
                .contains("event:done");
        assertThat(completed.indexOf("event:meta"))
                .isLessThan(completed.indexOf("event:delta"));
        assertThat(completed.indexOf("event:delta"))
                .isLessThan(completed.indexOf("event:done"));
    }

    @Test
    void sseResponseDisablesProxyAndBrowserBuffering() throws Exception {
        given(chatService.streamMessage(anyLong(), anyLong(), any()))
                .willAnswer(invocation -> streamingEmitter(new CountDownLatch(0)));

        mockMvc.perform(post("/api/v1/chat/sessions/9/stream")
                        .with(authentication(principalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"你好\",\"retry\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andExpect(header().string("Cache-Control", containsString("no-transform")));
    }

    /**
     * 模拟 {@code ChatServiceImpl.startStream}：同步写出 meta 之后，才由上游异步产出增量。
     */
    private SseEmitter streamingEmitter(CountDownLatch firstDeltaSent) throws Exception {
        SseEmitter emitter = new SseEmitter(180_000L);
        Thread producer = new Thread(() -> {
            try {
                Thread.sleep(UPSTREAM_DELAY_MILLIS);
                emitter.send(SseEmitter.event().name("delta")
                        .data(Map.of("content", "第一段", "phase", "answer", "requestId", REQUEST_ID)));
                firstDeltaSent.countDown();
                Thread.sleep(UPSTREAM_DELAY_MILLIS);
                emitter.send(SseEmitter.event().name("delta")
                        .data(Map.of("content", "第二段", "phase", "answer", "requestId", REQUEST_ID)));
                emitter.send(SseEmitter.event().name("done")
                        .data(Map.of("messageId", 12L, "requestId", REQUEST_ID)));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        }, "sse-timing-producer");
        producer.setDaemon(true);
        producer.start();
        emitter.send(SseEmitter.event().name("meta")
                .data(Map.of("sessionId", 9L, "requestId", REQUEST_ID)));
        return emitter;
    }

    private String awaitContent(MvcResult result, String marker) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000L;
        String content = result.getResponse().getContentAsString();
        while (!content.contains(marker) && System.currentTimeMillis() < deadline) {
            Thread.sleep(30L);
            content = result.getResponse().getContentAsString();
        }
        return content;
    }

    private UsernamePasswordAuthenticationToken principalToken() {
        UserPrincipal principal = new UserPrincipal(
                1L, "2024001", "n/a", "学生", null, 1, List.of("STUDENT"));
        return new UsernamePasswordAuthenticationToken(
                principal, "n/a", principal.getAuthorities());
    }

    /**
     * 切片测试不加载 MyBatis 自动配置，但应用类带有 {@code @MapperScan}，
     * 这里提供一个不连数据库的 SqlSessionFactory，让 MapperFactoryBean 能完成初始化。
     */
    @TestConfiguration
    static class MapperSliceSupport {

        @Bean
        SqlSessionFactory sqlSessionFactory() {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setEnvironment(new Environment(
                    "slice-test",
                    new JdbcTransactionFactory(),
                    new UnpooledDataSource()
            ));
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }
    }
}

