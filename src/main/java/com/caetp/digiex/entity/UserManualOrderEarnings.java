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
public class UserManualOrderEarnings {
    private Long id;

    private Long position;

    private Long login;

    private LocalDateTime msc;

    private BigDecimal price;

    private BigDecimal priceSl;

    private BigDecimal priceTp;

    private BigDecimal profit;

    private BigDecimal swap;

    private String symbol;

    private LocalDateTime openTime;

    private LocalDateTime earningsTime;

    private Integer type;

    private Double volume;


}