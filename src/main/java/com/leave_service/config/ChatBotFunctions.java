package com.leave_service.config;

import com.leave_service.model.LeaveType;
import com.leave_service.service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Configuration
public class ChatBotFunctions {


    public static final ThreadLocal<String> CTX_EMAIL = new ThreadLocal<>();
    public static final ThreadLocal<String> CTX_NAME  = new ThreadLocal<>();
    public static final ThreadLocal<String> CTX_ROLE  = new ThreadLocal<>();

    @Autowired
    private LeaveService leaveService;


    public record EmptyInput() {}
    public record UserInfo(String name, String email, String role) {}
    public record LeaveTypeInfo(String leaveName, Integer maxDays) {}
    public record LeaveTypeBalance(String leaveName, Integer maxDays, Integer used, Integer remaining) {}
    public record LeaveBalanceInfo(Map<String, LeaveTypeBalance> balances) {}


    @Bean
    @Description("Get the currently logged-in user's full name, email address and role (EMPLOYEE / MANAGER / ADMIN)")
    public Function<EmptyInput, UserInfo> getUserInfo() {
        System.out.println("[Spring] Creating getUserInfo bean");
        return input -> {
            System.out.println("[Function] getUserInfo called");
            return new UserInfo(CTX_NAME.get(), CTX_EMAIL.get(), CTX_ROLE.get());
        };
    }

    @Bean
    @Description("Get the current user's leave balance showing maximum days, days used, and days remaining for each leave type")
    public Function<EmptyInput, LeaveBalanceInfo> getLeaveBalance() {
        System.out.println("[Spring] Creating getLeaveBalance bean");
        return input -> {
            String email = CTX_EMAIL.get();
            System.out.println("[Function] getLeaveBalance called for: " + email);
            try {
                Map<String, Object> data = leaveService.getLeaveBalance(email);
                @SuppressWarnings("unchecked")
                Map<String, Object> balanceMap = (Map<String, Object>) data.get("leaveBalance");
                List<LeaveType> types = leaveService.getAllLeaveTypes();

                Map<String, Double> usedMap = new HashMap<>();
                if (balanceMap != null && balanceMap.containsKey("approved_leaves")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> approved =
                        (List<Map<String, Object>>) balanceMap.get("approved_leaves");
                    if (approved != null) {
                        for (Map<String, Object> leave : approved) {
                            String type = (String) leave.get("leaveType");
                            Object days = leave.get("totalDays");
                            if (type != null && days != null)
                                usedMap.merge(type, ((Number) days).doubleValue(), Double::sum);
                        }
                    }
                }

                Map<String, LeaveTypeBalance> result = new LinkedHashMap<>();
                for (LeaveType lt : types) {
                    int used = (int)(double) usedMap.getOrDefault(lt.getLeaveName(), 0.0);
                    int remaining = lt.getMaxDays() - used;
                    result.put(lt.getLeaveName(),
                        new LeaveTypeBalance(lt.getLeaveName(), lt.getMaxDays(), used, remaining));
                    System.out.println("  - " + lt.getLeaveName() + ": used=" + used + ", max=" + lt.getMaxDays() + ", remaining=" + remaining);
                }
                return new LeaveBalanceInfo(result);
            } catch (Exception e) {
                System.err.println("[Function] getLeaveBalance ERROR: " + e.getMessage());
                e.printStackTrace();
                return new LeaveBalanceInfo(Collections.emptyMap());
            }
        };
    }

    @Bean
    @Description("Get all leave types offered by the company and the maximum number of days allowed per year for each")
    public Function<EmptyInput, List<LeaveTypeInfo>> getLeaveTypes() {
        System.out.println("[Spring] Creating getLeaveTypes bean");
        return input -> {
            System.out.println("[Function] getLeaveTypes called");
            return leaveService.getAllLeaveTypes().stream()
                .map(lt -> new LeaveTypeInfo(lt.getLeaveName(), lt.getMaxDays()))
                .collect(Collectors.toList());
        };
    }
}