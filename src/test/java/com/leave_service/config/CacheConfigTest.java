package com.leave_service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CacheConfigTest {

    @InjectMocks
    private CacheConfig cacheConfig;

    @Test
    void cacheManager_returnsNonNull() {
        CacheManager manager = cacheConfig.cacheManager();
        assertNotNull(manager);
    }

    @Test
    void cacheManager_isCaffeineCacheManager() {
        CacheManager manager = cacheConfig.cacheManager();
        assertInstanceOf(CaffeineCacheManager.class, manager);
    }

    @Test
    void cacheManager_hasConfiguredChatbotResponsesCache() {
        CaffeineCacheManager manager = (CaffeineCacheManager) cacheConfig.cacheManager();
        // getCache("chatbotResponses") will lazily create the cache if it's configured
        assertNotNull(manager.getCache("chatbotResponses"));
    }

    @Test
    void cacheManager_twoInvocations_returnsFunctionalManagers() {
        CacheManager manager1 = cacheConfig.cacheManager();
        CacheManager manager2 = cacheConfig.cacheManager();
        assertNotNull(manager1);
        assertNotNull(manager2);
    }
}
