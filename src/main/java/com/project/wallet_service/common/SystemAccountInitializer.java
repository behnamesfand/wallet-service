package com.project.wallet_service.common;

import com.project.wallet_service.account.service.SystemAccountService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SystemAccountInitializer {

    @Bean
    ApplicationRunner initializeSystemAccounts(
            SystemAccountService systemAccountService) {
        return args -> systemAccountService.initializeClearingAccount();
    }
}