package com.caetp.digiex.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.caetp.digiex.dto.PageInfoDTO;
import com.caetp.digiex.dto.TPage;
import com.caetp.digiex.dto.api.*;
import com.caetp.digiex.dto.cms.AIRobotCmsNameDTO;
import com.caetp.digiex.entity.*;
import com.caetp.digiex.entity.mapper.*;
import com.caetp.digiex.entity.rmsmapper.DMemberAccountDetailsMapper;
import com.caetp.digiex.entity.rmsmapper.DMemberAccountMapper;
import com.caetp.digiex.exception.CommonException;
import com.caetp.digiex.exception.UserException;
import com.caetp.digiex.utli.common.GetMemberId;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.gson.JsonArray;
import net.bytebuddy.asm.Advice;
import org.java_websocket.client.WebSocketClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created by hy on 2018/2/17.
 */
@Service
public class UserOrderService extends BaseService {

    @Autowired
    private UserOrderMapper userOrderMapper;
    @Autowired
    private DMemberAccountMapper dMemberAccountMapper;
    @Autowired
    private DMemberAccountDetailsMapper dMemberAccountDetailsMapper;
    @Autowired
    private Mt5UserOrderMapper mt5UserOrderMapper;
    @Autowired
    private AiRobotMapper aiRobotMapper;
    @Autowired
    private UserMt5OrderDetailMapper userMt5OrderDetailMapper;
    @Autowired
    private MemberMt5Mapper memberMt5Mapper;
    @Autowired
    private MarketMapper marketMapper;
    @Autowired
    private UserMt5OrderParameterMapper userMt5OrderParameterMapper;
    @Autowired
    private HistoryOrderMapper historyOrderMapper;
    @Autowired
    private WebSocketClient websocketCalculationUserOrderProfit;
    @Autowired
    private UserManualOrderDayEarningsMapper userManualOrderDayEarningsMapper;
    @Autowired
    private UserManualOrderEarningsMapper userManualOrderEarningsMapper;

    /**
     * 持仓列表
     * @param pageNumber
     * @param pageSize
     * @param token
     * @return
     */
    public TPage<UserOrderListDTO> userOrderBuyList(Integer pageNumber, Integer pageSize, String token){
        Member member = validateUserToken(token);

        List<UserOrderListDTO> list = PageHelper.startPage(pageNumber,pageSize).doSelectPage(() ->
                userOrderMapper.userOrderBuyList(member.getMemberId()));
        PageInfo<UserOrderListDTO> pageInfo = new PageInfo<>(list);
        PageInfoDTO pageInfoDTO = new PageInfoDTO((int) pageInfo.getTotal(), pageNumber, pageInfo.getPages(), pageSize);
        return TPage.of(list, pageInfo.getTotal(), pageInfoDTO);
    }

    /**
     * 历史列表
     * @param pageNumber
     * @param pageSize
     * @param token
     * @return
     */
    public TPage<UserOrderListDTO> userOrderSellList(Integer pageNumber, Integer pageSize, String token){
        Member member = validateUserToken(token);

        List<UserOrderListDTO> list = PageHelper.startPage(pageNumber,pageSize).doSelectPage(() ->
                userOrderMapper.userOrderSellList(member.getMemberId()));
        PageInfo<UserOrderListDTO> pageInfo = new PageInfo<>(list);
        PageInfoDTO pageInfoDTO = new PageInfoDTO((int) pageInfo.getTotal(), pageNumber, pageInfo.getPages(), pageSize);
        return TPage.of(list, pageInfo.getTotal(), pageInfoDTO);
    }

    /**
     * 订单详情
     * @param token  用户token
     * @param id  用户下单表id
     * @return
     */
    public UserOrderDetailDTO userOrderDetail(String token, Long id){
        Member member = validateUserToken(token);
        UserOrderDetailDTO userOrderDetailDTO = userOrderMapper.userOrderDetail(member.getMemberId(), id);
        // mt5订单交易的次数
        userOrderDetailDTO.setNumberOfAiTrade(mt5UserOrderMapper.countAiTradeById(id));
        // 当前mt5订单交易次数的截止时间
        userOrderDetailDTO.setDeadLine(LocalDateTime.now());
        return userOrderDetailDTO;
    }

    /**
     * 撤销未建仓的订单
     * @param token
     * @param id
     */
    public void cancelUserOrder(String token, Long id) {
        Member member = validateUserToken(token);

        UserOrder order = userOrderMapper.selectByPrimaryKey(id);
        if (order == null) {
            throw CommonException.NOT_FOUND_RESULT;
        }

        int count = userOrderMapper.cancelUserOrder(member.getMemberId(), id);
        if (count == 0) {
            // 可能由于订单已建仓，不能删除了，导致撤销失败
            throw UserException.CANCEL_ORDER_FAIL;
        } else {
            // 返回订单金额
            BigDecimal totalFee = order.getFee().add(order.getPettyCash());
            DMemberAccountIncrement account = DMemberAccountIncrement.builder()
                    .memberId(member.getMemberId())
                    .usdBalanceIncrement(totalFee.multiply(BigDecimal.valueOf(100)).longValue())
                    .updatedBy(member.getMemberId()).updatedTime(LocalDateTime.now())
                    .build();
            dMemberAccountMapper.updateAccountByPrimaryKeySelective(account);

            // 插入USD账户的流水明细
            DMemberAccountDetails accountDetails = DMemberAccountDetails.builder()
                    .memberId(member.getMemberId()).linkId(order.getId().toString())
                    .accountType("USD").amount(totalFee)
                    .detailsType("ex_ai").createdTime(LocalDateTime.now())
                    .build();
            dMemberAccountDetailsMapper.insertSelective(accountDetails);
        }
    }

    /**
     * 删除历史订单
     * @param token
     * @param id
     */
    public void deleteUserOrderSell(String token, Long id) {
        Member member = validateUserToken(token);

        userOrderMapper.deleteUserOrderSell(member.getMemberId(), id);
    }

    /**
     * 用户订单平仓
     * @param token
     * @param id
     */
    public void sellUserOrder(String token, Long id) {
        Member member = validateUserToken(token);
        userOrderMapper.sellUserOrder(member.getMemberId(), id);
    }

    /** ai,挂单，手动单的历史
     * @param pageNumber
     * @param pageSize
     * @param token
     * @return
     */
    public TPage<UserHistoeryOrderDTO> userOrderHistoeryList(Integer pageNumber, Integer pageSize, String token) {
        Member member = validateUserToken(token);
        List<UserHistoeryOrderDTO> userHistoeryOrderDTOList = new ArrayList<UserHistoeryOrderDTO>();
        HashMap<Object, Object> chinessNameMap = new HashMap<>();

        // 用户ai跟单历史数据(终止跟单和撤销跟单)
        List<UserOrder > userOrderList =  userOrderMapper.userHistoryOrdre(member.getMemberId());
        List<AIRobotCmsNameDTO> aiRobotCmsNameDTOS = aiRobotMapper.aiRobotByName();

        for (AIRobotCmsNameDTO aiRobotCmsNameDTO : aiRobotCmsNameDTOS) {
            chinessNameMap.put(aiRobotCmsNameDTO.getId().toString(), aiRobotCmsNameDTO.getName());
        }

        LocalDateTime time = null;
        if (userOrderList != null && userOrderList.size() > 0) {
            for (UserOrder  userOrder : userOrderList) {
                LocalDateTime cancelTime = userOrder.getCancelTime();
                if ( cancelTime != null) {
                    time = cancelTime;
                } else {
                    time = userOrder.getSellTime();
                }
                // ai名
                String aiRobotName = (String) chinessNameMap.get(userOrder.getAiRobotId().toString());
                UserHistoeryOrderDTO userHistoeryOrderDTO = UserHistoeryOrderDTO.builder()
                        .aiRobotId(userOrder.getAiRobotId()).tradeType("ai跟单").name(aiRobotName)
                        .standardHands(userOrder.getStandardHands().doubleValue())
                        .profit(userOrder.getEarnings().doubleValue())
                        .status(userOrder.getStatus()).time(time).symbolName(null)
                        .orderId(Integer.parseInt(userOrder.getId().toString()))
                        .tradeCmd(null).startPrice(0.0).endPrice(0.0)
                        .build();
                userHistoeryOrderDTOList.add(userHistoeryOrderDTO);
            }
        }

        //  手动下单数据  平仓订单order != position_id and position_id !=0  建仓价位initPrice 平仓价位endPrice
        Integer login = memberMt5Mapper.selectByPrimaryKey(member.getMemberId()).getLogin();
        List<MarketDTO> marketDTOList = marketMapper.getCoinName();
        for (MarketDTO marketDTO : marketDTOList) {
            chinessNameMap.put(marketDTO.getCoin(), marketDTO.getCoinName());
        }

        /*List<UserMt5OrderDetail> userMt5OrderDetailList = userMt5OrderDetailMapper.getUserManualHistoryOrder(login);*/
        List<HistoryOrder> historyOrderList = historyOrderMapper.getHistoryOrderByLogin(login);
        for (HistoryOrder historyOrder : historyOrderList) {
            String symbol = historyOrder.getSymbol();
            String coinName = (String) chinessNameMap.get(symbol);

            UserHistoeryOrderDTO histoeryOrderDTO = UserHistoeryOrderDTO.builder()
                    .tradeType("平仓订单").name(symbol)
                    .symbolName(coinName).orderId(Integer.parseInt(historyOrder.getOpenOrder()))
                    .standardHands(historyOrder.getVolume()).tradeCmd(historyOrder.getAction())
                    .profit(historyOrder.getProfit().doubleValue()).startPrice(historyOrder.getOpenPrice().doubleValue())
                    .endPrice(historyOrder.getPrice().doubleValue()).status(5).time(historyOrder.getCloseTime())
                    .aiRobotId(0L)
                    .build();
            userHistoeryOrderDTOList.add(histoeryOrderDTO);
        }

        // 撤销的挂单status=2
        List<UserMt5OrderDetail> userCancleOrderList = userMt5OrderDetailMapper.getCencleOrders(login.toString());
        List<UserMt5OrderParameter> userMt5OrderParameters =  userMt5OrderParameterMapper.getStarPriceByLogin(login);
        for (UserMt5OrderDetail userMt5OrderDetail : userCancleOrderList) {
            // 挂单货币的中文名
            String symbol = userMt5OrderDetail.getSymbol();
            String comment = userMt5OrderDetail.getComment();
            String coinName = (String) chinessNameMap.get(symbol);
            BigDecimal price = userMt5OrderParameterMapper.selectByPrimaryKey(GetMemberId.getOrder(comment)).getPrice();
            UserHistoeryOrderDTO userHistoeryOrderDTO = UserHistoeryOrderDTO.builder()
                    .tradeType("挂单").name(symbol).symbolName(coinName)
                    .orderId(userMt5OrderDetail.getOrder())
                    .standardHands(userMt5OrderDetail.getFinalVolume())
                    .status(6).startPrice(price.doubleValue())
                    .endPrice(userMt5OrderDetail.getOrderPrice()
                    .doubleValue()).time(userMt5OrderDetail.getDoneTime())
                    .tradeCmd(userMt5OrderDetail.getType())
                    .aiRobotId(0L).profit(0.0)
                    .build();
            userHistoeryOrderDTOList.add(userHistoeryOrderDTO);
        }

        // 最后对List集合中UserHistoeryOrderDTO对象根据 时间排序
        Collections.sort(userHistoeryOrderDTOList, new Comparator<UserHistoeryOrderDTO>() {
            @Override
            public int compare(UserHistoeryOrderDTO o1, UserHistoeryOrderDTO o2) {
                if (o1.getTime().isAfter(o2.getTime())) {
                    return -1;
                } else if (o1.getTime().isEqual(o2.getTime())) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });

        // 手动分页
        int totalRow = userHistoeryOrderDTOList.size();
        int totalPage = ((totalRow % pageSize) == 0) ? (totalRow / pageSize) : (totalRow / pageSize) + 1;
        int indexBegin = (pageNumber-1) * pageSize - 1 ;
        int indexEnd = indexBegin + pageSize + 1 ;
        if (indexEnd >= totalRow) {
            indexEnd = totalRow;
        }
        if (indexBegin < 0) {
            indexBegin = 0;
        }
        List<UserHistoeryOrderDTO> pageList = userHistoeryOrderDTOList.subList(indexBegin, indexEnd);
        PageInfoDTO pageInfoDTO = new PageInfoDTO(totalRow, pageNumber, totalPage, pageSize);
        return new TPage<>(pageList, totalRow, pageInfoDTO);
    }

    /** 历史订单详情
     * @param orderId
     * @param token
     * @return
     */
    public HistoryOrderDTO userOrderHistoeryDetail(Integer orderId, String token) {
        Member member = validateUserToken(token);
        HistoryOrder historyOrder = historyOrderMapper.selectByPrimaryKey(orderId.toString());
        UserMt5OrderDetail userMt5OrderDetail = userMt5OrderDetailMapper.selectByPrimaryKey(orderId);
        HistoryOrderDTO historyOrderDTO = null;
        if (historyOrder != null) { // 平仓订单
            String comment = historyOrder.getComment();
            Integer tradeType = null;
            if ("".equals(comment)) {
                tradeType = 1;
            } else if (comment.contains("sl")) {
                tradeType = 2;
            } else if (comment.contains("tp")) {
                tradeType = 3;
            } else {
                tradeType = 4;
            }
            UserMt5OrderParameter userMt5OrderParameter = userMt5OrderParameterMapper.selectByPrimaryKey(
                    GetMemberId.getOrder(userMt5OrderDetail.getComment())
            );
            Integer leverage = userMt5OrderParameter.getLeverage();
            Double volume = historyOrder.getVolume();
            BigDecimal rateMargin = historyOrder.getRateMargin();
            // 预付款 手数 * MR * 100000 / 杠杆
            BigDecimal subsist = rateMargin.multiply(BigDecimal.valueOf(volume))
                    .multiply(new BigDecimal("100000"))
                    .divide(new BigDecimal(leverage.toString()));
            historyOrderDTO = HistoryOrderDTO.builder()
                    .orderType(1).symbol(historyOrder.getSymbol())
                    .profit(historyOrder.getProfit().doubleValue())
                    .openPrice(historyOrder.getOpenPrice().doubleValue())
                    .closePrice(historyOrder.getPrice().doubleValue()).volume(volume)
                    .action(historyOrder.getAction())
                    .order(orderId.toString()).createTime(historyOrder.getCreateTime())
                    .closeTime(historyOrder.getCloseTime()).tradeType(tradeType)
                    .leverage(leverage).subsist(subsist)
                    .tp("0".equals( ((Integer) userMt5OrderParameter.getTp().intValue()).toString())? "未设置":
                            userMt5OrderParameter.getTp().toString())
                    .sl("0".equals( ((Integer) userMt5OrderParameter.getSl().intValue()).toString())? "未设置":
                            userMt5OrderParameter.getSl().toString())
                    .commission(Double.valueOf(historyOrder.getCommission()))
                    .swap(historyOrder.getStorage().doubleValue())
                    .build();

        } else { // 撤销的挂单
            String comment = userMt5OrderDetail.getComment();
            UserMt5OrderParameter userMt5OrderParameter = userMt5OrderParameterMapper.selectByPrimaryKey(GetMemberId.getOrder(comment));
            Integer leverage = userMt5OrderParameter.getLeverage();
            historyOrderDTO = HistoryOrderDTO.builder()
                    .orderType(2).symbol(userMt5OrderDetail.getSymbol())
                    .profit(0.0).openPrice(userMt5OrderDetail.getOrderPrice().doubleValue())
                    .closePrice(0.0).action(userMt5OrderParameter.getTradecmd())
                    .volume(userMt5OrderDetail.getFinalVolume())
                    .order(orderId.toString()).createTime(userMt5OrderDetail.getSetupTime())
                    .closeTime(userMt5OrderDetail.getDoneTime())
                    .tradeType(null).leverage(leverage)
                    .subsist(new BigDecimal("0.0"))
                    .tp("0".equals(((Integer) userMt5OrderParameter.getTp().intValue()).toString())? "未设置":
                            userMt5OrderParameter.getTp().toString())
                    .sl("0".equals(((Integer) userMt5OrderParameter.getSl().intValue()).toString())? "未设置": userMt5OrderParameter.getSl().toString())
                    .commission(0.0).swap(0.0)
                    .build();
        }
        return historyOrderDTO;
    }

    /** 计算用户每日盈利和每个订单的每日盈利
     * @param now
     */
    public void calculationUserOrderProfit(LocalDateTime now) {
        // 查询组持仓统计
        Map reqParameterMap = new HashMap();
        reqParameterMap.put("reqtype", "getGroupPositionOrdersInfo");
        reqParameterMap.put("reqid", now);
        reqParameterMap.put("groupname", "real\\DIGUSDH-B");
        websocketCalculationUserOrderProfit.send(JSON.toJSONString(reqParameterMap));
    }

    public TPage<UserManualOrderEarningsDTO> userOrderDayProfit(Integer pageNumber, Integer pageSize, Integer orderId, String token) {
        Member member = validateUserToken(token);
        MemberMt5 memberMt5 = memberMt5Mapper.selectByPrimaryKey(member.getMemberId());
        PageHelper.startPage(pageNumber, pageSize);
        List<UserManualOrderEarningsDTO> userManualOrderEarningsDTOList = userManualOrderEarningsMapper
                .UserOrderDayProfitList(memberMt5.getLogin(), orderId);
        PageInfo<UserManualOrderEarningsDTO> pageInfo = new PageInfo<>(userManualOrderEarningsDTOList);
        PageInfoDTO pageInfoDTO = new PageInfoDTO((int) pageInfo.getTotal(), pageNumber, pageInfo.getPages(), pageSize);
        return TPage.of(userManualOrderEarningsDTOList, pageInfo.getTotal(), pageInfoDTO);
    }

    public TPage<UserManualOrderDayEaringsDTO> userDayProfit(Integer pageNumber, Integer pageSize, String token) {
        Member member = validateUserToken(token);
        MemberMt5 memberMt5 = memberMt5Mapper.selectByPrimaryKey(member.getMemberId());
        PageHelper.startPage(pageNumber, pageSize);
        List<UserManualOrderDayEaringsDTO> userManualOrderDayEaringsDTOList = userManualOrderDayEarningsMapper
                .userDayProfitList(memberMt5.getLogin());
        PageInfo<UserManualOrderDayEaringsDTO> pageInfo = new PageInfo<>(userManualOrderDayEaringsDTOList);
        PageInfoDTO pageInfoDTO = new PageInfoDTO((int) pageInfo.getTotal(), pageNumber, pageInfo.getPages(), pageSize);
        return TPage.of(userManualOrderDayEaringsDTOList, pageInfo.getTotal(), pageInfoDTO);
    }
}
