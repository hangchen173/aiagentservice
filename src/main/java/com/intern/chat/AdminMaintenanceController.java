package com.intern.chat;

import com.intern.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminMaintenanceController {
    private final DemoDataCleanupService cleanupService;

    public AdminMaintenanceController(DemoDataCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @DeleteMapping("/history")
    public ApiResponse<DemoDataCleanupResponse> cleanupHistory() {
        return ApiResponse.ok(cleanupService.cleanup());
    }
}
