package com.caetp.digiex.task;

import com.caetp.digiex.service.UserOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Created by gaoyx on 2019/6/26.
 * 计算用户每日盈利和每个订单的每日盈利
 */
@Component
public class UserOrderTask {
    @Autowired
    UserOrderService userOrderService;
    // cron = "0 48 20 * * *"
    @Scheduled(cron = "0 58 23 * * *")
    public void calculateOrderEarnings() {
        userOrderService.calculationUserOrderProfit(LocalDateTime.now());
    }


}
