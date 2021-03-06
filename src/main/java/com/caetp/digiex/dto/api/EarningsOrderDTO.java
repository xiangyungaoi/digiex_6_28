package com.caetp.digiex.dto.api;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Created by wangzy on 2019/3/25.
 */
@Data
@NoArgsConstructor
public class EarningsOrderDTO {

    private Long userOrderId;

    private Integer memberId;

    private BigDecimal earnings;
}
