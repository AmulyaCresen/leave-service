package com.leave_service.controller;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowableAuditControllerTest {

    @Mock private RuntimeService runtimeService;
    @Mock private TaskService taskService;
    @Mock private HistoryService historyService;
    @InjectMocks private FlowableAuditController controller;

    @Test
    void getActiveProcesses_returnsProcesses() {
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        ProcessInstance pi = mock(ProcessInstance.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.processDefinitionKey(anyString())).thenReturn(query);
        when(query.list()).thenReturn(List.of(pi));

        List<ProcessInstance> result = controller.getActiveProcesses();

        assertEquals(1, result.size());
    }

    @Test
    void getActiveTasks_returnsTasks() {
        TaskQuery query = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        List<Task> result = controller.getActiveTasks();

        assertEquals(1, result.size());
    }

    @Test
    void getTasksByAssignee_returnsTasks() {
        TaskQuery query = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskAssignee(anyString())).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        List<Task> result = controller.getTasksByAssignee("mgr@test.com");

        assertEquals(1, result.size());
    }

    @Test
    void getProcessHistory_returnsHistory() {
        HistoricActivityInstanceQuery query = mock(HistoricActivityInstanceQuery.class);
        HistoricActivityInstance activity = mock(HistoricActivityInstance.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(query);
        when(query.processInstanceId(anyString())).thenReturn(query);
        when(query.orderByHistoricActivityInstanceStartTime()).thenReturn(query);
        when(query.asc()).thenReturn(query);
        when(query.list()).thenReturn(List.of(activity));

        List<HistoricActivityInstance> result = controller.getProcessHistory("proc-1");

        assertEquals(1, result.size());
    }

    @Test
    void getProcessVariables_returnsVariables() {
        Map<String, Object> vars = Map.of("leaveId", 1L);
        when(runtimeService.getVariables(anyString())).thenReturn(vars);

        Map<String, Object> result = controller.getProcessVariables("proc-1");

        assertEquals(1L, result.get("leaveId"));
    }

    @Test
    void getProcessByLeaveId_returnsProcess() {
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        ProcessInstance pi = mock(ProcessInstance.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.processDefinitionKey(anyString())).thenReturn(query);
        when(query.variableValueEquals(anyString(), any())).thenReturn(query);
        when(query.singleResult()).thenReturn(pi);

        ProcessInstance result = controller.getProcessByLeaveId(1L);

        assertNotNull(result);
    }

    @Test
    void getLeaveAuditTrail_activeProcess() {
        ProcessInstanceQuery piQuery = mock(ProcessInstanceQuery.class);
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("proc-1");
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.processDefinitionKey(anyString())).thenReturn(piQuery);
        when(piQuery.variableValueEquals(anyString(), any())).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(pi);

        HistoricActivityInstanceQuery query = mock(HistoricActivityInstanceQuery.class);
        HistoricActivityInstance activity = mock(HistoricActivityInstance.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(query);
        when(query.processInstanceId(anyString())).thenReturn(query);
        when(query.orderByHistoricActivityInstanceStartTime()).thenReturn(query);
        when(query.asc()).thenReturn(query);
        when(query.list()).thenReturn(List.of(activity));

        List<HistoricActivityInstance> result = controller.getLeaveAuditTrail(1L);

        assertEquals(1, result.size());
    }

    @Test
    void getLeaveAuditTrail_completedProcess() {
        ProcessInstanceQuery piQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.processDefinitionKey(anyString())).thenReturn(piQuery);
        when(piQuery.variableValueEquals(anyString(), any())).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(null);

        HistoricProcessInstanceQuery hpiQuery = mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
        when(hpi.getId()).thenReturn("proc-1");
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
        when(hpiQuery.processDefinitionKey(anyString())).thenReturn(hpiQuery);
        when(hpiQuery.variableValueEquals(anyString(), any())).thenReturn(hpiQuery);
        when(hpiQuery.singleResult()).thenReturn(hpi);

        HistoricActivityInstanceQuery query = mock(HistoricActivityInstanceQuery.class);
        HistoricActivityInstance activity = mock(HistoricActivityInstance.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(query);
        when(query.processInstanceId(anyString())).thenReturn(query);
        when(query.orderByHistoricActivityInstanceStartTime()).thenReturn(query);
        when(query.asc()).thenReturn(query);
        when(query.list()).thenReturn(List.of(activity));

        List<HistoricActivityInstance> result = controller.getLeaveAuditTrail(1L);

        assertEquals(1, result.size());
    }

    @Test
    void getLeaveAuditTrail_noProcess() {
        ProcessInstanceQuery piQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.processDefinitionKey(anyString())).thenReturn(piQuery);
        when(piQuery.variableValueEquals(anyString(), any())).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(null);

        HistoricProcessInstanceQuery hpiQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
        when(hpiQuery.processDefinitionKey(anyString())).thenReturn(hpiQuery);
        when(hpiQuery.variableValueEquals(anyString(), any())).thenReturn(hpiQuery);
        when(hpiQuery.singleResult()).thenReturn(null);

        List<HistoricActivityInstance> result = controller.getLeaveAuditTrail(1L);

        assertEquals(0, result.size());
    }

    @Test
    void getCompletedProcesses_returnsProcesses() {
        HistoricProcessInstanceQuery query = mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(query);
        when(query.processDefinitionKey(anyString())).thenReturn(query);
        when(query.finished()).thenReturn(query);
        when(query.orderByProcessInstanceEndTime()).thenReturn(query);
        when(query.desc()).thenReturn(query);
        when(query.list()).thenReturn(List.of(hpi));

        List<HistoricProcessInstance> result = controller.getCompletedProcesses();

        assertEquals(1, result.size());
    }
}
