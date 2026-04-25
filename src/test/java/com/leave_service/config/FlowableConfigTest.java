package com.leave_service.config;

import org.flowable.engine.*;
import org.flowable.spring.ProcessEngineFactoryBean;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowableConfigTest {

    @Mock private DataSource dataSource;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ApplicationContext applicationContext;
    @Mock private ProcessEngine processEngine;
    @InjectMocks private FlowableConfig flowableConfig;

    @Test
    void processEngineConfiguration_createsConfig() {
        SpringProcessEngineConfiguration config = flowableConfig.processEngineConfiguration(dataSource, transactionManager);

        assertNotNull(config);
        assertNotNull(config.getDataSource());
        assertEquals(transactionManager, config.getTransactionManager());
    }

    @Test
    void processEngine_createsFactoryBean() {
        SpringProcessEngineConfiguration config = mock(SpringProcessEngineConfiguration.class);

        ProcessEngineFactoryBean factoryBean = flowableConfig.processEngine(config);

        assertNotNull(factoryBean);
    }

    @Test
    void restTemplate_createsBean() {
        RestTemplate restTemplate = flowableConfig.restTemplate();

        assertNotNull(restTemplate);
    }

    @Test
    void runtimeService_createsBean() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        when(processEngine.getRuntimeService()).thenReturn(runtimeService);

        RuntimeService result = flowableConfig.runtimeService(processEngine);

        assertNotNull(result);
        assertEquals(runtimeService, result);
    }

    @Test
    void taskService_createsBean() {
        TaskService taskService = mock(TaskService.class);
        when(processEngine.getTaskService()).thenReturn(taskService);

        TaskService result = flowableConfig.taskService(processEngine);

        assertNotNull(result);
        assertEquals(taskService, result);
    }

    @Test
    void repositoryService_createsBean() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(processEngine.getRepositoryService()).thenReturn(repositoryService);

        RepositoryService result = flowableConfig.repositoryService(processEngine);

        assertNotNull(result);
        assertEquals(repositoryService, result);
    }

    @Test
    void historyService_createsBean() {
        HistoryService historyService = mock(HistoryService.class);
        when(processEngine.getHistoryService()).thenReturn(historyService);

        HistoryService result = flowableConfig.historyService(processEngine);

        assertNotNull(result);
        assertEquals(historyService, result);
    }
}
