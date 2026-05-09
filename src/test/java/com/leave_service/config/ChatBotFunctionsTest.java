package com.leave_service.config;

import com.leave_service.model.LeaveType;
import com.leave_service.service.LeaveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatBotFunctionsTest {

    @Mock
    private LeaveService leaveService;

    @InjectMocks
    private ChatBotFunctions chatBotFunctions;

    @BeforeEach
    void setUp() {
        ChatBotFunctions.CTX_EMAIL.set("test@example.com");
        ChatBotFunctions.CTX_NAME.set("Test User");
        ChatBotFunctions.CTX_ROLE.set("EMPLOYEE");
    }

    @AfterEach
    void tearDown() {
        ChatBotFunctions.CTX_EMAIL.remove();
        ChatBotFunctions.CTX_NAME.remove();
        ChatBotFunctions.CTX_ROLE.remove();
    }

    // ---- getUserInfo ----

    @Test
    void getUserInfo_returnsCurrentThreadLocalContext() {
        Function<ChatBotFunctions.EmptyInput, ChatBotFunctions.UserInfo> fn =
                chatBotFunctions.getUserInfo();

        ChatBotFunctions.UserInfo info = fn.apply(new ChatBotFunctions.EmptyInput());

        assertEquals("Test User", info.name());
        assertEquals("test@example.com", info.email());
        assertEquals("EMPLOYEE", info.role());
    }

    @Test
    void getUserInfo_withNullContext_returnsNulls() {
        ChatBotFunctions.CTX_EMAIL.remove();
        ChatBotFunctions.CTX_NAME.remove();
        ChatBotFunctions.CTX_ROLE.remove();

        Function<ChatBotFunctions.EmptyInput, ChatBotFunctions.UserInfo> fn =
                chatBotFunctions.getUserInfo();

        ChatBotFunctions.UserInfo info = fn.apply(new ChatBotFunctions.EmptyInput());
        assertNull(info.name());
        assertNull(info.email());
        assertNull(info.role());
    }

    // ---- getLeaveTypes ----

    @Test
    void getLeaveTypes_returnsAllLeaveTypes() {
        LeaveType lt1 = new LeaveType();
        lt1.setLeaveName("Annual Leave");
        lt1.setMaxDays(12);

        LeaveType lt2 = new LeaveType();
        lt2.setLeaveName("Sick Leave");
        lt2.setMaxDays(7);

        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(lt1, lt2));

        Function<ChatBotFunctions.EmptyInput, List<ChatBotFunctions.LeaveTypeInfo>> fn =
                chatBotFunctions.getLeaveTypes();
        List<ChatBotFunctions.LeaveTypeInfo> result = fn.apply(new ChatBotFunctions.EmptyInput());

        assertEquals(2, result.size());
        assertEquals("Annual Leave", result.get(0).leaveName());
        assertEquals(12, result.get(0).maxDays());
        assertEquals("Sick Leave", result.get(1).leaveName());
        assertEquals(7, result.get(1).maxDays());
    }

    @Test
    void getLeaveTypes_withEmptyList_returnsEmpty() {
        when(leaveService.getAllLeaveTypes()).thenReturn(List.of());

        Function<ChatBotFunctions.EmptyInput, List<ChatBotFunctions.LeaveTypeInfo>> fn =
                chatBotFunctions.getLeaveTypes();
        List<ChatBotFunctions.LeaveTypeInfo> result = fn.apply(new ChatBotFunctions.EmptyInput());

        assertTrue(result.isEmpty());
    }

    // ---- getLeaveBalance ----

    @Test
    void getLeaveBalance_withApprovedLeaves_calculatesUsedAndRemaining() {
        LeaveType lt = new LeaveType();
        lt.setLeaveName("Annual Leave");
        lt.setMaxDays(12);

        Map<String, Object> leaveMap = new HashMap<>();
        List<Map<String, Object>> approved = new ArrayList<>();
        Map<String, Object> approvedEntry = new HashMap<>();
        approvedEntry.put("leaveType", "Annual Leave");
        approvedEntry.put("totalDays", 3.0);
        approved.add(approvedEntry);
        leaveMap.put("approved_leaves", approved);

        Map<String, Object> data = new HashMap<>();
        data.put("leaveBalance", leaveMap);

        when(leaveService.getLeaveBalance("test@example.com")).thenReturn(data);
        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(lt));

        Function<ChatBotFunctions.EmptyInput, ChatBotFunctions.LeaveBalanceInfo> fn =
                chatBotFunctions.getLeaveBalance();
        ChatBotFunctions.LeaveBalanceInfo result = fn.apply(new ChatBotFunctions.EmptyInput());

        assertNotNull(result);
        ChatBotFunctions.LeaveTypeBalance balance = result.balances().get("Annual Leave");
        assertNotNull(balance);
        assertEquals(3, balance.used());
        assertEquals(9, balance.remaining());
        assertEquals(12, balance.maxDays());
    }

    @Test
    void getLeaveBalance_withNoApprovedLeaves_allRemainingAtMax() {
        LeaveType lt = new LeaveType();
        lt.setLeaveName("Sick Leave");
        lt.setMaxDays(7);

        Map<String, Object> data = new HashMap<>();
        data.put("leaveBalance", new HashMap<>());

        when(leaveService.getLeaveBalance("test@example.com")).thenReturn(data);
        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(lt));

        Function<ChatBotFunctions.EmptyInput, ChatBotFunctions.LeaveBalanceInfo> fn =
                chatBotFunctions.getLeaveBalance();
        ChatBotFunctions.LeaveBalanceInfo result = fn.apply(new ChatBotFunctions.EmptyInput());

        assertNotNull(result);
        ChatBotFunctions.LeaveTypeBalance balance = result.balances().get("Sick Leave");
        assertNotNull(balance);
        assertEquals(0, balance.used());
        assertEquals(7, balance.remaining());
    }

    @Test
    void getLeaveBalance_withNullBalanceMap_returnsZeroUsed() {
        LeaveType lt = new LeaveType();
        lt.setLeaveName("Casual Leave");
        lt.setMaxDays(5);

        Map<String, Object> data = new HashMap<>();
        data.put("leaveBalance", null);

        when(leaveService.getLeaveBalance("test@example.com")).thenReturn(data);
        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(lt));

        Function<ChatBotFunctions.EmptyInput, ChatBotFunctions.LeaveBalanceInfo> fn =
                chatBotFunctions.getLeaveBalance();
        ChatBotFunctions.LeaveBalanceInfo result = fn.apply(new ChatBotFunctions.EmptyInput());

        assertNotNull(result);
        ChatBotFunctions.LeaveTypeBalance balance = result.balances().get("Casual Leave");
        assertEquals(0, balance.used());
        assertEquals(5, balance.remaining());
    }

    @Test
    void getLeaveBalance_whenServiceThrowsException_returnsEmptyMap() {
        when(leaveService.getLeaveBalance(anyString())).thenThrow(new RuntimeException("DB error"));

        Function<ChatBotFunctions.EmptyInput, ChatBotFunctions.LeaveBalanceInfo> fn =
                chatBotFunctions.getLeaveBalance();
        ChatBotFunctions.LeaveBalanceInfo result = fn.apply(new ChatBotFunctions.EmptyInput());

        assertNotNull(result);
        assertTrue(result.balances().isEmpty());
    }

    @Test
    void getLeaveBalance_withMultipleLeaveEntriesSameType_accumulatesUsage() {
        LeaveType lt = new LeaveType();
        lt.setLeaveName("Annual Leave");
        lt.setMaxDays(12);

        Map<String, Object> leaveMap = new HashMap<>();
        List<Map<String, Object>> approved = new ArrayList<>();

        Map<String, Object> entry1 = new HashMap<>();
        entry1.put("leaveType", "Annual Leave");
        entry1.put("totalDays", 2.0);
        approved.add(entry1);

        Map<String, Object> entry2 = new HashMap<>();
        entry2.put("leaveType", "Annual Leave");
        entry2.put("totalDays", 3.0);
        approved.add(entry2);

        leaveMap.put("approved_leaves", approved);
        Map<String, Object> data = new HashMap<>();
        data.put("leaveBalance", leaveMap);

        when(leaveService.getLeaveBalance("test@example.com")).thenReturn(data);
        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(lt));

        Function<ChatBotFunctions.EmptyInput, ChatBotFunctions.LeaveBalanceInfo> fn =
                chatBotFunctions.getLeaveBalance();
        ChatBotFunctions.LeaveBalanceInfo result = fn.apply(new ChatBotFunctions.EmptyInput());

        ChatBotFunctions.LeaveTypeBalance balance = result.balances().get("Annual Leave");
        assertEquals(5, balance.used());  // 2 + 3
        assertEquals(7, balance.remaining());
    }

    @Test
    void getLeaveBalance_withNullApprovedList_returnsZeroUsage() {
        LeaveType lt = new LeaveType();
        lt.setLeaveName("Annual Leave");
        lt.setMaxDays(12);

        Map<String, Object> leaveMap = new HashMap<>();
        leaveMap.put("approved_leaves", null);

        Map<String, Object> data = new HashMap<>();
        data.put("leaveBalance", leaveMap);

        when(leaveService.getLeaveBalance("test@example.com")).thenReturn(data);
        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(lt));

        Function<ChatBotFunctions.EmptyInput, ChatBotFunctions.LeaveBalanceInfo> fn =
                chatBotFunctions.getLeaveBalance();
        ChatBotFunctions.LeaveBalanceInfo result = fn.apply(new ChatBotFunctions.EmptyInput());

        ChatBotFunctions.LeaveTypeBalance balance = result.balances().get("Annual Leave");
        assertEquals(0, balance.used());
    }

    @Test
    void getLeaveBalance_withLeaveEntryMissingTypeOrDays_skipsEntry() {
        LeaveType lt = new LeaveType();
        lt.setLeaveName("Annual Leave");
        lt.setMaxDays(12);

        Map<String, Object> leaveMap = new HashMap<>();
        List<Map<String, Object>> approved = new ArrayList<>();

        // Entry missing "leaveType"
        Map<String, Object> missingType = new HashMap<>();
        missingType.put("totalDays", 2.0);
        approved.add(missingType);

        // Entry missing "totalDays"
        Map<String, Object> missingDays = new HashMap<>();
        missingDays.put("leaveType", "Annual Leave");
        approved.add(missingDays);

        leaveMap.put("approved_leaves", approved);
        Map<String, Object> data = new HashMap<>();
        data.put("leaveBalance", leaveMap);

        when(leaveService.getLeaveBalance("test@example.com")).thenReturn(data);
        when(leaveService.getAllLeaveTypes()).thenReturn(List.of(lt));

        Function<ChatBotFunctions.EmptyInput, ChatBotFunctions.LeaveBalanceInfo> fn =
                chatBotFunctions.getLeaveBalance();
        ChatBotFunctions.LeaveBalanceInfo result = fn.apply(new ChatBotFunctions.EmptyInput());

        ChatBotFunctions.LeaveTypeBalance balance = result.balances().get("Annual Leave");
        // Both entries were skipped, so used = 0
        assertEquals(0, balance.used());
        assertEquals(12, balance.remaining());
    }
}
