package com.caetp.digiex.dto.api;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Created by gaoyx on 2019/6/25.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ApiModel("历史订单详情")
public class HistoryOrderDTO {
    @ApiModelProperty("订单类型: 1.平仓历史订单。 2.回踩、突破撤销订单。 3.突破回踩撤销订单")
    private Integer orderType;
    @ApiModelProperty("币种")
    private String symbol;
    @ApiModelProperty("1.平仓历史订单:开仓价格. 2.撤销挂单：挂单价格")
    private double openPrice;
    @ApiModelProperty("1.平仓历史订单:平仓价格. 2.撤销挂单：1回踩、突破撤销订单--无. 2突破回踩撤销订单:触发价格")
    private double closePrice;
    @ApiModelProperty("方向: 1.平仓历史订单:0卖出，1买入.  2撤销挂单:0买入，1卖出. ")
    private int action;
    @ApiModelProperty("手数")
    private double volume;
    @ApiModelProperty("1.平仓历史订单：1手动平仓,2止损平仓,3止盈平仓,4系统强制平仓. 2.撤销挂单:无")
    private Integer tradeType;
    @ApiModelProperty("平仓盈利 ")
    private double profit;
    @ApiModelProperty("1.平仓历史订单：订单号.2.撤销挂单:订单号")
    private String order;
    @ApiModelProperty("1.平仓历史订单：开仓时间. 2.撤销挂单:挂单时间")
    private LocalDateTime createTime;
    @ApiModelProperty("1.平仓历史订单：平仓时间. 2.撤销挂单:撤销挂单时间")
    private LocalDateTime closeTime;
   /* @ApiModelProperty("1.平仓历史订单:平仓类型. 2.撤销挂单:无")
    private String closeType;*/
    @ApiModelProperty("杠杆率")
    private Integer leverage;
    @ApiModelProperty("预付款")
    private BigDecimal subsist;
    @ApiModelProperty("止盈")
    private String tp;
    @ApiModelProperty("止损")
    private String sl;
    @ApiModelProperty("手续费")
    private double commission;
    @ApiModelProperty("库存费")
    private double swap;
}
