package com.caetp.digiex.dto.api;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Created by gaoyx on 2019/6/27.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ApiModel("持仓订单每日盈利")
public class UserManualOrderEarningsDTO {
    @ApiModelProperty("订单id")
    private Integer position;
    @ApiModelProperty("盈利")
    private BigDecimal profit;
    @ApiModelProperty("盈利日期")
    private LocalDateTime time;
}
