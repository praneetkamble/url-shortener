package com.praneetkamble.urlshortener.web;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.praneetkamble.urlshortener.AbstractIntegrationTest;
import com.praneetkamble.urlshortener.service.UrlResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class UrlApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    CacheManager cacheManager;

    private MockHttpServletRequestBuilder create(String body, String clientIp) {
        return post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(r -> {
                    r.setRemoteAddr(clientIp);
                    return r;
                });
    }

    private String createCode(String url) throws Exception {
        String response = mvc.perform(create("{\"url\":\"" + url + "\"}", newClientIp()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return node.get("code").asText();
    }

    @Test
    void createsShortUrlAndReturnsLocationAndBody() throws Exception {
        mvc.perform(create("{\"url\":\"https://example.com/hello\"}", newClientIp()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", matchesPattern("[0-9A-Za-z]{3,8}")))
                .andExpect(jsonPath("$.shortUrl", matchesPattern("http://short.test/[0-9A-Za-z]+")))
                .andExpect(jsonPath("$.originalUrl", is("https://example.com/hello")))
                .andExpect(jsonPath("$.customAlias", is(false)))
                .andExpect(jsonPath("$.createdAt", is("2026-01-15T10:00:00Z")))
                .andExpect(jsonPath("$.expiresAt").doesNotExist())
                .andExpect(header().string("Location", matchesPattern("http://short.test/[0-9A-Za-z]+")))
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    @Test
    void customAliasAndExpiryAreHonouredAndDuplicatesConflict() throws Exception {
        String ip = newClientIp();
        mvc.perform(create("{\"url\":\"https://example.com/a\",\"customAlias\":\"promo-2026\",\"expiresInSeconds\":3600}", ip))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("promo-2026")))
                .andExpect(jsonPath("$.customAlias", is(true)))
                .andExpect(jsonPath("$.expiresAt", is("2026-01-15T11:00:00Z")));

        mvc.perform(create("{\"url\":\"https://example.com/b\",\"customAlias\":\"promo-2026\"}", ip))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void validatesInput() throws Exception {
        String ip = newClientIp();
        mvc.perform(create("{\"url\":\"\"}", ip)).andExpect(status().isBadRequest());
        mvc.perform(create("{\"url\":\"javascript:alert(1)\"}", ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", notNullValue()));
        mvc.perform(create("{\"url\":\"https://example.com\",\"expiresInSeconds\":0}", ip))
                .andExpect(status().isBadRequest());
        mvc.perform(create("{not json", ip)).andExpect(status().isBadRequest());
    }

    @Test
    void redirectsWithFoundAndPopulatesTheCache() throws Exception {
        String code = createCode("https://example.com/landing?a=1&b=2");

        mvc.perform(get("/" + code).header("Referer", "https://blog.example.org/post"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing?a=1&b=2"))
                .andExpect(header().string("Cache-Control", "no-store"));

        org.assertj.core.api.Assertions.assertThat(cacheManager.getCache(UrlResolver.CACHE_NAME).get(code)).isNotNull();
    }

    @Test
    void unknownCodeIs404AndExpiredCodeIs410() throws Exception {
        mvc.perform(get("/nope123")).andExpect(status().isNotFound());

        String ip = newClientIp();
        mvc.perform(create("{\"url\":\"https://example.com/t\",\"customAlias\":\"short-lived\",\"expiresInSeconds\":60}", ip))
                .andExpect(status().isCreated());
        mvc.perform(get("/short-lived")).andExpect(status().isFound());

        clock.advance(Duration.ofSeconds(61));
        mvc.perform(get("/short-lived")).andExpect(status().isGone());
    }

    @Test
    void statsReflectClicks() throws Exception {
        String code = createCode("https://example.com/stats");
        mvc.perform(get("/" + code).header("Referer", "https://a.example.org/x")).andExpect(status().isFound());
        mvc.perform(get("/" + code).header("Referer", "https://a.example.org/y")).andExpect(status().isFound());
        mvc.perform(get("/" + code)).andExpect(status().isFound());

        mvc.perform(get("/api/v1/urls/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks", is(3)))
                .andExpect(jsonPath("$.clicksByDay", hasSize(1)))
                .andExpect(jsonPath("$.clicksByDay[0].date", is("2026-01-15")))
                .andExpect(jsonPath("$.clicksByDay[0].clicks", is(3)))
                .andExpect(jsonPath("$.topReferrers[0].referrer", is("a.example.org")))
                .andExpect(jsonPath("$.topReferrers[0].clicks", is(2)));

        mvc.perform(get("/api/v1/urls/" + code)).andExpect(jsonPath("$.clickCount", is(3)));
    }

    @Test
    void deleteRemovesTheLink() throws Exception {
        String code = createCode("https://example.com/bye");
        mvc.perform(get("/" + code)).andExpect(status().isFound());

        mvc.perform(delete("/api/v1/urls/" + code)).andExpect(status().isNoContent());

        mvc.perform(get("/" + code)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/urls/" + code)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/urls/" + code)).andExpect(status().isNotFound());
    }

    @Test
    void throttlesCreationPerClientButNotRedirects() throws Exception {
        String ip = newClientIp();
        for (int i = 0; i < 5; i++) {
            mvc.perform(create("{\"url\":\"https://example.com/rl/" + i + "\"}", ip)).andExpect(status().isCreated());
        }
        mvc.perform(create("{\"url\":\"https://example.com/rl/6\"}", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("\\d+")))
                .andExpect(header().string("X-RateLimit-Remaining", is("0")));

        // a different client is unaffected, and so is the redirect path
        mvc.perform(create("{\"url\":\"https://example.com/rl/other\"}", newClientIp())).andExpect(status().isCreated());
        mvc.perform(get("/nope456")).andExpect(status().isNotFound());

        // after the window rolls over the budget is restored
        clock.advance(Duration.ofMinutes(2));
        mvc.perform(create("{\"url\":\"https://example.com/rl/again\"}", ip)).andExpect(status().isCreated());
    }

    @Test
    void exposesOpenApiDocumentation() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/urls']").exists())
                .andExpect(jsonPath("$.paths['/{code}']").exists());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/swagger-ui/index.html")));
    }
}
