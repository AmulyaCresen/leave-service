package com.leave_service.config;
import org.flowable.engine.*;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.flowable.spring.ProcessEngineFactoryBean;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;
import javax.sql.DataSource;
@Configuration
public class FlowableConfig {
    @Autowired
    private ApplicationContext applicationContext;
    @Bean
    public SpringProcessEngineConfiguration processEngineConfiguration(
            DataSource dataSource,
            PlatformTransactionManager transactionManager) {
        SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();
        config.setDataSource(dataSource);
        config.setTransactionManager(transactionManager);
        config.setDatabaseSchemaUpdate(StandaloneProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(true);
        config.setApplicationContext(applicationContext);
        config.setDeploymentResources(new Resource[]{
            new ClassPathResource("processes/leaveProcess.bpmn")
        });
        return config;
    }
    @Bean
    public ProcessEngineFactoryBean processEngine(SpringProcessEngineConfiguration config) {
        ProcessEngineFactoryBean factoryBean = new ProcessEngineFactoryBean();
        factoryBean.setProcessEngineConfiguration(config);
        return factoryBean;
    }
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    @Bean
    public RuntimeService runtimeService(ProcessEngine processEngine) {
        return processEngine.getRuntimeService();
    }
    @Bean
    public TaskService taskService(ProcessEngine processEngine) {
        return processEngine.getTaskService();
    }
    @Bean
    public RepositoryService repositoryService(ProcessEngine processEngine) {
        return processEngine.getRepositoryService();
    }
    @Bean
    public HistoryService historyService(ProcessEngine processEngine) {
        return processEngine.getHistoryService();
    }
}