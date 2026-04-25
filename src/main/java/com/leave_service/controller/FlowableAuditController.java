package com.leave_service.controller;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flowable")
public class FlowableAuditController {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    public FlowableAuditController(RuntimeService runtimeService, 
                                   TaskService taskService, 
                                   HistoryService historyService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
    }

    @GetMapping("/active-processes")
    public List<ProcessInstance> getActiveProcesses() {
        return runtimeService.createProcessInstanceQuery()
                .processDefinitionKey("leaveApprovalProcess")
                .list();
    }

    @GetMapping("/active-tasks")
    public List<Task> getActiveTasks() {
        return taskService.createTaskQuery().list();
    }

    @GetMapping("/tasks/assignee/{assignee}")
    public List<Task> getTasksByAssignee(@PathVariable String assignee) {
        return taskService.createTaskQuery()
                .taskAssignee(assignee)
                .list();
    }

    @GetMapping("/process/{processInstanceId}/history")
    public List<HistoricActivityInstance> getProcessHistory(@PathVariable String processInstanceId) {
        return historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();
    }

    @GetMapping("/process/{processInstanceId}/variables")
    public Map<String, Object> getProcessVariables(@PathVariable String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    @GetMapping("/leave/{leaveId}/process")
    public ProcessInstance getProcessByLeaveId(@PathVariable Long leaveId) {
        return runtimeService.createProcessInstanceQuery()
                .processDefinitionKey("leaveApprovalProcess")
                .variableValueEquals("leaveId", leaveId)
                .singleResult();
    }

    @GetMapping("/leave/{leaveId}/audit")
    public List<HistoricActivityInstance> getLeaveAuditTrail(@PathVariable Long leaveId) {
        ProcessInstance process = getProcessByLeaveId(leaveId);
        if (process != null) {
            return getProcessHistory(process.getId());
        }
        
        // Check completed processes
        HistoricProcessInstance historicProcess = historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey("leaveApprovalProcess")
                .variableValueEquals("leaveId", leaveId)
                .singleResult();
                
        if (historicProcess != null) {
            return getProcessHistory(historicProcess.getId());
        }
        
        return List.of();
    }

    @GetMapping("/completed-processes")
    public List<HistoricProcessInstance> getCompletedProcesses() {
        return historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey("leaveApprovalProcess")
                .finished()
                .orderByProcessInstanceEndTime()
                .desc()
                .list();
    }
}