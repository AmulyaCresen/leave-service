package com.leave_service.service;

import com.leave_service.model.Leave;
import com.leave_service.model.LeaveType;
import com.leave_service.model.ChatSession;

import com.leave_service.repository.LeaveRepository;
import com.leave_service.repository.ChatSessionRepository;

import com.leave_service.client.UserServiceClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;

@Service
public class ChatBotService {

    @Autowired
    private LeaveService leaveService;
    
    @Autowired
    private LeaveRepository leaveRepository;
    
    @Autowired
    private ChatSessionRepository chatSessionRepository;
    

    
    @Autowired
    private UserServiceClient userServiceClient;
    
    @Autowired
    private org.springframework.web.client.RestTemplate restTemplate;
    
    @org.springframework.beans.factory.annotation.Value("${app.user-service.url}")
    private String userServiceUrl;
    
    private String currentUserEmail;
    private final ChatClient chatClient;

    @Autowired
    public ChatBotService(OllamaChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .build();
        
        System.out.println("[ChatBot] AI Function Calling ENABLED with Caching");
    }
    
    public record Request() {}
    public record LeaveBalanceResponse(Map<String, LeaveInfo> leaves) {}
    public record LeaveInfo(String type, int maxDays, int used, int remaining) {}
    
    public record PendingLeavesResponse(List<PendingLeave> pendingLeaves) {}
    public record PendingLeave(String type, String fromDate, String toDate) {}
    
    public record LeaveTypesResponse(List<LeaveTypeInfo> types) {}
    public record LeaveTypeInfo(String name, int maxDays) {}

    public String processMessage(String message, String email, String name, String role) {
        return processMessage(message, email, name, role, null);
    }
    
    public String processMessage(String message, String email, String name, String role, Long sessionId) {
        String accessDenied = checkRoleAccess(message, role);
        if (accessDenied != null) {
            return accessDenied;
        }
        
        long startTime = System.currentTimeMillis();
        String response = generateResponse(message, email, name, role);
        long latency = System.currentTimeMillis() - startTime;
        
        Long newSessionId = saveChatHistory(message, response, email, sessionId, latency);
        
        return response;
    }
    
    private String checkRoleAccess(String message, String role) {
        String lowerMsg = message.toLowerCase();
        
        if ("ADMIN".equalsIgnoreCase(role)) {
            return null;
        }
        
        if (lowerMsg.contains("how many employees") || 
            lowerMsg.contains("employee count") ||
            lowerMsg.contains("company id of") ||
            lowerMsg.contains("employee details") ||
            lowerMsg.contains("all employees") ||
            lowerMsg.contains("details of all employees") ||
            lowerMsg.contains("details of employees") ||
            lowerMsg.contains("list of employees") ||
            lowerMsg.contains("employee list") ||
            lowerMsg.contains("give details") ||
            lowerMsg.contains("show employees") ||
            (lowerMsg.contains("details") && lowerMsg.contains("employee"))) {
            return "Access denied. Employee information is only available for administrators.";
        }
        
        if (lowerMsg.contains("team leaves") ||
            lowerMsg.contains("pending approvals") ||
            lowerMsg.contains("approve") ||
            lowerMsg.contains("reject")) {
            if (!"MANAGER".equalsIgnoreCase(role)) {
                return "Access denied. Team leave information is only available for managers and administrators.";
            }
        }
        
        return null;
    }
    
    private Long saveChatHistory(String userMessage, String botResponse, String email, Long sessionId, long latency) {
        try {
            ChatSession userRecord = chatSessionRepository.findByEmailId(email)
                .orElseGet(() -> createNewUserRecord(email));
            
            Map<String, Object> session = null;
            
            if (sessionId != null) {
                for (Map<String, Object> s : userRecord.getSessions()) {
                    if (sessionId.equals(((Number) s.get("sessionId")).longValue())) {
                        session = s;
                        break;
                    }
                }
            }
            
            if (session == null) {
                session = createNewSession(userMessage);
                userRecord.getSessions().add(session);
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) session.get("messages");
            
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("sender", "user");
            userMsg.put("content", userMessage);
            userMsg.put("createdAt", OffsetDateTime.now().toString());
            messages.add(userMsg);
            
            Map<String, Object> botMsg = new HashMap<>();
            botMsg.put("sender", "bot");
            botMsg.put("latency", latency);
            botMsg.put("createdAt", OffsetDateTime.now().toString());
            
            if (botResponse.contains("|") && botResponse.contains("\n")) {
                Map<String, Object> structuredResponse = parseTableResponse(botResponse);
                botMsg.put("type", "table");
                botMsg.put("content", structuredResponse.get("text"));
                botMsg.put("tableData", structuredResponse.get("tableData"));
            } else {
                botMsg.put("type", "text");
                botMsg.put("content", botResponse);
            }
            
            messages.add(botMsg);
            
            session.put("updatedAt", OffsetDateTime.now().toString());
            userRecord.setUpdatedAt(OffsetDateTime.now());
            chatSessionRepository.save(userRecord);
            
            return ((Number) session.get("sessionId")).longValue();
        } catch (Exception e) {
            System.err.println("[ChatBot] Error saving chat history: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private ChatSession createNewUserRecord(String email) {
        ChatSession userRecord = new ChatSession();
        userRecord.setEmailId(email);
        return chatSessionRepository.save(userRecord);
    }
    
    private Map<String, Object> createNewSession(String firstMessage) {
        Map<String, Object> session = new HashMap<>();
        session.put("sessionId", System.currentTimeMillis());
        session.put("title", generateSessionTitle(firstMessage));
        session.put("createdAt", OffsetDateTime.now().toString());
        session.put("updatedAt", OffsetDateTime.now().toString());
        session.put("messages", new ArrayList<Map<String, Object>>());
        return session;
    }
    
    private String generateSessionTitle(String firstMessage) {
        if (firstMessage.length() > 50) {
            return firstMessage.substring(0, 47) + "...";
        }
        return firstMessage;
    }
    
    private Map<String, Object> parseTableResponse(String response) {
        Map<String, Object> result = new HashMap<>();
        
        // Extract text before table
        int tableStart = response.indexOf("|");
        String textPart = "";
        String tablePart = response;
        
        if (tableStart > 0) {
            textPart = response.substring(0, tableStart).trim();
            tablePart = response.substring(tableStart);
        }
        
        result.put("text", textPart);
        
        // Parse markdown table
        String[] lines = tablePart.split("\n");
        List<Map<String, String>> tableData = new ArrayList<>();
        
        if (lines.length >= 3) {
            // Extract headers from first line
            String headerLine = lines[0];
            String[] headers = headerLine.split("\\|");
            List<String> headerList = new ArrayList<>();
            for (String header : headers) {
                String trimmed = header.trim();
                if (!trimmed.isEmpty()) {
                    headerList.add(trimmed);
                }
            }
            
            // Parse data rows (skip header and separator line)
            for (int i = 2; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.startsWith("|") && line.endsWith("|")) {
                    String[] cells = line.split("\\|");
                    Map<String, String> row = new HashMap<>();
                    
                    int cellIndex = 0;
                    for (String cell : cells) {
                        String trimmed = cell.trim();
                        if (!trimmed.isEmpty() && cellIndex < headerList.size()) {
                            row.put(headerList.get(cellIndex), trimmed);
                            cellIndex++;
                        }
                    }
                    
                    if (!row.isEmpty()) {
                        tableData.add(row);
                    }
                }
            }
        }
        
        result.put("tableData", tableData);
        return result;
    }
    

    
    @Cacheable(value = "chatbotResponses", key = "#message + '_' + #email + '_' + #role")
    private String generateResponse(String message, String email, String name, String role) {
        try {
            this.currentUserEmail = email;
            
            System.out.println("\n[ChatBot] ===== AI Processing (Cache MISS) =====");
            System.out.println("[ChatBot] User: " + name + " (" + email + ")");
            System.out.println("[ChatBot] Query: " + message);
            
            String intentPrompt = String.format("""
                Analyze this user query and respond with ONLY ONE of these function names:
                - getLeaveBalance (for: "leave balance", "how many leaves used", "sick leaves used", "casual leaves used", "remaining leaves")
                - getPendingLeaves (for: "pending leaves", "leaves pending", "waiting for approval")
                - getLeaveTypes (for: "leave types", "what leaves available", "leave policies")
                - getUserInfo (for: "my role", "my details", "who am i", "my name", "my email", "my information")
                - getTeamLeaves (for: "team leaves", "all employees leaves") [MANAGER/ADMIN only]
                - getEmployeeInfo (for: "employee count", "how many employees", "company id of", "employee details", "all employees", "list employees", "show employees", "give details", "details of all employees", "details of employees", "employee information", questions about OTHER employees) [ADMIN only]
                - greeting (for: "hello", "hi", "hey")
                - help (for: "help", "what can you do")
                
                User query: "%s"
                User role: %s
                
                IMPORTANT: If query contains "details" AND "employees", use getEmployeeInfo.
                If query asks about OTHER people (not "my"), use getEmployeeInfo.
                If query asks about "my" details, use getUserInfo.
                If query mentions "all employees" or "employee details" or "list", use getEmployeeInfo.
                
                Respond with ONLY the function name, nothing else.
                """, message, role);
            
            String intent = chatClient.prompt()
                .user(intentPrompt)
                .call()
                .content()
                .trim()
                .toLowerCase();
            
            System.out.println("[ChatBot] AI Intent: " + intent);
            
            String functionResult = null;
            
            if (intent.contains("getleavebalance")) {
                System.out.println(">>> [AI FUNCTION CALLED] getLeaveBalance <<<");
                LeaveBalanceResponse result = calculateLeaveBalance(email);
                
                String lowerMsg = message.toLowerCase();
                if (lowerMsg.contains("sick")) {
                    for (Map.Entry<String, LeaveInfo> entry : result.leaves().entrySet()) {
                        if (entry.getKey().toLowerCase().contains("sick")) {
                            LeaveInfo info = entry.getValue();
                            if (lowerMsg.contains("remaining") || lowerMsg.contains("left")) {
                                return String.format("You have %d sick leave days remaining out of %d.", 
                                    info.remaining(), info.maxDays());
                            } else {
                                return String.format("You have used %d sick leave days out of %d.", 
                                    info.used(), info.maxDays());
                            }
                        }
                    }
                } else if (lowerMsg.contains("casual")) {
                    for (Map.Entry<String, LeaveInfo> entry : result.leaves().entrySet()) {
                        if (entry.getKey().toLowerCase().contains("casual")) {
                            LeaveInfo info = entry.getValue();
                            if (lowerMsg.contains("remaining") || lowerMsg.contains("left")) {
                                return String.format("You have %d casual leave days remaining out of %d.", 
                                    info.remaining(), info.maxDays());
                            } else {
                                return String.format("You have used %d casual leave days out of %d.", 
                                    info.used(), info.maxDays());
                            }
                        }
                    }
                }
                
                functionResult = formatLeaveBalance(result);
            } else if (intent.contains("getpendingleaves")) {
                System.out.println(">>> [AI FUNCTION CALLED] getPendingLeaves <<<");
                PendingLeavesResponse result = calculatePendingLeaves(email);
                functionResult = formatPendingLeaves(result);
            } else if (intent.contains("getleavetypes")) {
                System.out.println(">>> [AI FUNCTION CALLED] getLeaveTypes <<<");
                LeaveTypesResponse result = getAllLeaveTypesInfo();
                functionResult = formatLeaveTypes(result);
            } else if (intent.contains("getuserinfo")) {
                System.out.println(">>> [AI FUNCTION CALLED] getUserInfo <<<");
                return String.format("Your details:\nName: %s\nEmail: %s\nRole: %s", name, email, role);
            } else if (intent.contains("getteamleaves")) {
                System.out.println(">>> [AI FUNCTION CALLED] getTeamLeaves <<<");
                return "Team leave summary: This feature shows all team members' leave status. Currently showing your personal data only.";
            } else if (intent.contains("getemployeeinfo")) {
                System.out.println(">>> [AI FUNCTION CALLED] getEmployeeInfo <<<");
                String lowerMsg = message.toLowerCase();
                
                if (lowerMsg.contains("how many") || lowerMsg.contains("employee count")) {
                    try {
                        @SuppressWarnings("unchecked")
                        List<java.util.Map<String, Object>> users = restTemplate
                            .getForObject(userServiceUrl + "/users/all", List.class);
                        
                        if (users == null) {
                            return "Unable to fetch employee count. Please try again later.";
                        }
                        
                        int totalUsers = users.size();
                        return String.format("There are %d employees in the system.", totalUsers);
                    } catch (Exception e) {
                        System.err.println("[ChatBot] Error fetching employee count: " + e.getMessage());
                        e.printStackTrace();
                        return "Unable to fetch employee count. Please try again later.";
                    }
                } else if (lowerMsg.contains("company id")) {
                    try {
                        String employeeName = message.replaceAll("(?i)(what is|the|company id|of|\\?)", "").trim();
                        @SuppressWarnings("unchecked")
                        List<java.util.Map<String, Object>> users = restTemplate
                            .getForObject(userServiceUrl + "/users/all", List.class);
                        
                        if (users != null) {
                            for (java.util.Map<String, Object> user : users) {
                                String fullName = (String) user.get("fullName");
                                if (fullName != null && fullName.toLowerCase().contains(employeeName.toLowerCase())) {
                                    String companyId = (String) user.get("companyId");
                                    return String.format("%s's company ID is %s.", fullName, companyId);
                                }
                            }
                        }
                        return String.format("Employee '%s' not found in the system.", employeeName);
                    } catch (Exception e) {
                        System.err.println("[ChatBot] Error fetching company ID: " + e.getMessage());
                        e.printStackTrace();
                        return "Unable to fetch company ID. Please try again later.";
                    }
                } else {
                    try {
                        @SuppressWarnings("unchecked")
                        List<java.util.Map<String, Object>> users = restTemplate
                            .getForObject(userServiceUrl + "/users/all", List.class);
                        
                        if (users == null || users.isEmpty()) {
                            return "No employees found in the system.";
                        }
                        
                        StringBuilder table = new StringBuilder();
                        table.append("Here are all employees in the system:\n\n");
                        table.append("| Name | Email | Company ID | Role |\n");
                        table.append("|------|-------|------------|------|\n");
                        
                        for (java.util.Map<String, Object> user : users) {
                            String fullName = (String) user.get("fullName");
                            String userEmail = (String) user.get("email");
                            String companyId = (String) user.get("companyId");
                            String userRole = (String) user.get("role");
                            
                            table.append(String.format("| %s | %s | %s | %s |\n", 
                                fullName != null ? fullName : "N/A",
                                userEmail != null ? userEmail : "N/A",
                                companyId != null ? companyId : "N/A",
                                userRole != null ? userRole : "N/A"
                            ));
                        }
                        
                        String result = table.toString();
                        System.out.println("[ChatBot] Returning table with newlines: " + result.contains("\n"));
                        return result;
                    } catch (Exception e) {
                        System.err.println("[ChatBot] Error fetching employee list: " + e.getMessage());
                        e.printStackTrace();
                        return "Unable to fetch employee list. Please try again later.";
                    }
                }
            } else if (intent.contains("greeting")) {
                return "Hello! I'm CresenGPT. Ask me about your leave balance, pending leaves, or leave types!";
            } else if (intent.contains("help")) {
                return "I can help with:\n• Leave balance\n• Pending leaves\n• Leave types\n• Your details\n\nJust ask!";
            } else {
                return "I'm not sure about that. Try asking about your leave balance or pending leaves.";
            }
            
            String responsePrompt = String.format("""
                User asked: "%s"
                
                Data:
                %s
                
                Generate a SHORT, direct answer (1 sentence) using ONLY the exact numbers from this data.
                Do not change or interpret the numbers. Use them exactly as provided.
                """, message, functionResult);
            
            long startTime = System.currentTimeMillis();
            
            String response = chatClient.prompt()
                .user(responsePrompt)
                .call()
                .content();
            
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("[ChatBot] AI responded in " + duration + "ms");
            System.out.println("[ChatBot] Response: " + response);
            System.out.println("[ChatBot] ========================\n");
            
            return (response != null && !response.isBlank()) ? response : "I'm not sure about that. Try asking about your leave balance or pending leaves.";
            
        } catch (Exception e) {
            System.err.println("[ChatBot] ERROR: " + e.getMessage());
            e.printStackTrace();
            return "I'm not sure about that. Try asking about your leave balance or pending leaves.";
        }
    }
    
    private String formatLeaveBalance(LeaveBalanceResponse response) {
        StringBuilder sb = new StringBuilder("Leave Balance:\n");
        for (Map.Entry<String, LeaveInfo> entry : response.leaves().entrySet()) {
            LeaveInfo info = entry.getValue();
            sb.append(String.format("- %s: Used %d days, Remaining %d days (Total: %d days)\n",
                info.type(), info.used(), info.remaining(), info.maxDays()));
        }
        return sb.toString();
    }
    
    private String formatPendingLeaves(PendingLeavesResponse response) {
        if (response.pendingLeaves().isEmpty()) {
            return "No pending leaves.";
        }
        StringBuilder sb = new StringBuilder("Pending Leaves:\n");
        for (PendingLeave leave : response.pendingLeaves()) {
            sb.append(String.format("- %s: %s to %s\n", leave.type(), leave.fromDate(), leave.toDate()));
        }
        return sb.toString();
    }
    
    private String formatLeaveTypes(LeaveTypesResponse response) {
        StringBuilder sb = new StringBuilder("Available Leave Types:\n");
        for (LeaveTypeInfo type : response.types()) {
            sb.append(String.format("- %s: %d days per year\n", type.name(), type.maxDays()));
        }
        return sb.toString();
    }
    
    private LeaveBalanceResponse calculateLeaveBalance(String email) {
        try {
            List<LeaveType> types = leaveService.getAllLeaveTypes();
            List<Leave> allLeaves = leaveRepository.findByEmailId(email);
            
            Map<String, LeaveInfo> result = new LinkedHashMap<>();
            
            for (LeaveType lt : types) {
                int used = 0;
                
                System.out.println("\n[DEBUG] Calculating for: " + lt.getLeaveName());
                
                for (Leave leave : allLeaves) {
                    if (leave.getLeaveType() != null && 
                        leave.getLeaveType().equalsIgnoreCase(lt.getLeaveName())) {
                        
                        System.out.println("  [DEBUG] Found leave ID: " + leave.getId() + ", Type: " + leave.getLeaveType());
                        System.out.println("  [DEBUG] From: " + leave.getFromDate() + ", To: " + leave.getToDate());
                        
                        boolean isApproved = false;
                        List<Map<String, String>> trail = leave.getTrail();
                        if (trail != null) {
                            System.out.println("  [DEBUG] Trail entries: " + trail.size());
                            for (Map<String, String> entry : trail) {
                                System.out.println("    [DEBUG] Stage: " + entry.get("stage") + ", Status: " + entry.get("status"));
                                if ("ADMIN".equals(entry.get("stage")) && 
                                    "APPROVED".equals(entry.get("status"))) {
                                    isApproved = true;
                                    break;
                                }
                            }
                        }
                        
                        System.out.println("  [DEBUG] Is Approved by ADMIN: " + isApproved);
                        
                        if (isApproved && leave.getDays() != null) {
                            System.out.println("  [DEBUG] Total days entries: " + leave.getDays().size());
                            for (var day : leave.getDays()) {
                                System.out.println("    [DEBUG] Day: " + day.getDate() + ", Status: " + day.getStatus());
                            }
                            long approvedDays = leave.getDays().stream()
                                .filter(d -> "APPROVED".equals(d.getStatus()))
                                .count();
                            System.out.println("  [DEBUG] Approved days count for this leave: " + approvedDays);
                            used += (int) approvedDays;
                        }
                    }
                }
                
                System.out.println("[DEBUG] TOTAL used for " + lt.getLeaveName() + ": " + used + "\n");
                
                result.put(lt.getLeaveName(), 
                    new LeaveInfo(lt.getLeaveName(), lt.getMaxDays(), used, lt.getMaxDays() - used));
            }
            
            return new LeaveBalanceResponse(result);
        } catch (Exception e) {
            System.err.println("[Function] Error: " + e.getMessage());
            e.printStackTrace();
            return new LeaveBalanceResponse(new HashMap<>());
        }
    }
    
    private PendingLeavesResponse calculatePendingLeaves(String email) {
        try {
            List<Leave> allLeaves = leaveRepository.findByEmailId(email);
            List<PendingLeave> pending = new ArrayList<>();
            
            for (Leave leave : allLeaves) {
                if (leave.getLeaveType() != null) {
                    boolean hasAdminDecision = false;
                    List<Map<String, String>> trail = leave.getTrail();
                    
                    if (trail != null) {
                        for (Map<String, String> entry : trail) {
                            if ("ADMIN".equals(entry.get("stage")) && 
                                ("APPROVED".equals(entry.get("status")) || 
                                 "REJECTED".equals(entry.get("status")))) {
                                hasAdminDecision = true;
                                break;
                            }
                        }
                    }
                    
                    if (!hasAdminDecision) {
                        pending.add(new PendingLeave(
                            leave.getLeaveType(),
                            leave.getFromDate().toString(),
                            leave.getToDate().toString()
                        ));
                    }
                }
            }
            
            return new PendingLeavesResponse(pending);
        } catch (Exception e) {
            return new PendingLeavesResponse(new ArrayList<>());
        }
    }
    
    private LeaveTypesResponse getAllLeaveTypesInfo() {
        try {
            List<LeaveType> types = leaveService.getAllLeaveTypes();
            List<LeaveTypeInfo> result = types.stream()
                .map(lt -> new LeaveTypeInfo(lt.getLeaveName(), lt.getMaxDays()))
                .toList();
            return new LeaveTypesResponse(result);
        } catch (Exception e) {
            return new LeaveTypesResponse(new ArrayList<>());
        }
    }

    public String getWelcomeMessage() {
        return "Hi! I'm CresenGPT, your AI Leave Management assistant powered by Llama AI. " +
             "I can help with your leave balance, pending approvals, and policies. How can I help you today?";
    }

    private String fallback(String message) {
        String m = message.toLowerCase();
        if (m.contains("hello") || m.contains("hi") || m.contains("hey"))
            return "Hello! I'm CresenGPT. Ask me about your leaves!";
        if (m.contains("help"))
            return "I can help with leave balances, pending leaves, and leave types. Just ask!";
        return "I'm not sure about that. Try asking about your leave balance or pending leaves.";
    }
    
    public List<Map<String, Object>> getChatSessions(String email) {
        return chatSessionRepository.findByEmailId(email)
            .map(ChatSession::getSessions)
            .orElse(new ArrayList<>());
    }
    
    public Map<String, Object> getChatHistory(Long sessionId, String email) {
        try {
            System.out.println("[ChatBot] Loading chat history for session: " + sessionId);
            
            ChatSession userRecord = chatSessionRepository.findByEmailId(email)
                .orElseThrow(() -> new RuntimeException("User record not found"));
            
            for (Map<String, Object> session : userRecord.getSessions()) {
                if (sessionId.equals(((Number) session.get("sessionId")).longValue())) {
                    System.out.println("[ChatBot] Session found");
                    return session;
                }
            }
            
            throw new RuntimeException("Session not found");
        } catch (Exception e) {
            System.err.println("[ChatBot] Error loading chat history: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to load chat history: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public void deleteSession(Long sessionId, String email) {
        try {
            System.out.println("[ChatBot] Attempting to delete session: " + sessionId + " for email: " + email);
            
            ChatSession userRecord = chatSessionRepository.findByEmailId(email)
                .orElseThrow(() -> new RuntimeException("User record not found"));
            
            userRecord.getSessions().removeIf(session -> 
                sessionId.equals(((Number) session.get("sessionId")).longValue())
            );
            
            chatSessionRepository.save(userRecord);
            System.out.println("[ChatBot] Session deleted successfully");
        } catch (Exception e) {
            System.err.println("[ChatBot] Error deleting session: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to delete session: " + e.getMessage(), e);
        }
    }
}