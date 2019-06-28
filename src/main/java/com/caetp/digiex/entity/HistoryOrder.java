package com.caetp.digiex.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryOrder {
    private String openOrder;

    private Integer action;

    private String closeOrder;

    private LocalDateTime createTime;

    private LocalDateTime closeTime;

    private String comment;

    private String commission;

    private BigDecimal openPrice;

    private BigDecimal price;

    private BigDecimal profit;

    private BigDecimal rateMargin;

    private BigDecimal storage;

    private String symbol;

    private Long timeMsc;

    private Double volume;

    private Integer login;


}