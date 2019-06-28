package com.caetp.digiex.service;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.caetp.digiex.config.CustomTransaction;
import com.caetp.digiex.config.RabbitMqConfig;
import com.caetp.digiex.consts.ProjectConsts;
import com.caetp.digiex.dto.api.MarketDTO;
import com.caetp.digiex.entity.*;
import com.caetp.digiex.entity.mapper.*;
import com.caetp.digiex.entity.rmsmapper.DMemberAccountMapper;
import com.caetp.digiex.utli.common.GetMemberId;
import com.caetp.digiex.utli.mq.Send2Mq;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对mt5返回的结果进行处理
 * Created by gaoyx on 2019/5/21.
 *
 */
@Service
public class Mt5ResultService extends BaseService {

    private static Logger log = LoggerFactory.getLogger(Mt5ResultService.class);
    @Autowired
    private MemberMt5Mapper memberMt5Mapper;
    @Autowired
    private DMemberAccountMapper dMemberAccountMapper;
    @Autowired
    UserMt5OrderMapper userMt5OrderMapper;
    @Autowired
    DepositUserInfoMapper depositUserInfoMapper;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    UserMt5OrderParameterMapper userMt5OrderParameterMapper;
    @Autowired
    UserMt5OrderDetailMapper userMt5OrderDetailMapper;
    @Autowired
    MarketMapper marketMapper;
    @Autowired
    WebSocketClient webSocketClient;
    @Autowired
    WebSocketClient webSocketSaveOrderHistory;
    @Autowired
    Mt5RequestFallService mt5RequestFallService;
    @Autowired
    HistoryOrderMapper historyOrderMapper;
    @Autowired
    UserManualOrderEarningsMapper userManualorderEarningsMapper;
    @Autowired
    UserManualOrderDayEarningsMapper userManualOrderDayEarningsMapper;

    /** 发送到mt5的请求成功后的处理
     * @param opration 操作
     * @param result mt5返回数据
     * @param result reqParameter 请求参数
     */
    public void oprationSuccess(String opration, String result, String reqParameter) {
        log.info("本次" + opration + "操作成功" );
        log.info("mt5返回的数据为:" + result);
        // 根据不同的操作，请求成功后做不同的处理
        switch (opration) {
            case "register":  // 注册成功后的处理
                registerSuccess(reqParameter, result);
                break;
            case "getuserinfo":   // 查询mt5账户信息成功后的处理(存入数据库)
                getUserInfoSuccess(reqParameter, result);
                break;
            case "ChangeAccountLevelAge":  // 修改杠杆成功后的处理
                changeAccountLevelAgeSuccess(reqParameter, result);
                break;
            case "ioMoney":  //出入金成功后的处理
                ioMoneySuccess(reqParameter, result);
                break;
            case "marginleveluserinfo":  // 查询用户资金状况
                marginLevelUserInfoSuccess(reqParameter, result);
                break;
            case "ordersuserinfo":  // 用户持仓
                ordersUserInfoSuccess(reqParameter, result);
                break;
            case "historyorderinfo":  // 用户历史订单
                historyOrderInfoSuccess(reqParameter, result);
                break;
            case "openOrder":  // 开仓
                openOrderSuccess(reqParameter, result);
                break;
            case "closeOrder": // 平仓
                closeOrderSuccess(reqParameter, result);
                break;
            case "suspendOrder":  // 挂单
                openOrderSuccess(reqParameter, result);
                break;
            case "updateOrder":  // 更新订单
                openOrderSuccess(reqParameter, result);
                break;
            case "cancleOrder":  // 取消订单
                openOrderSuccess(reqParameter, result);
                break;
        }

    }

    /** 获取用户信息成功
     * @param reqParameter
     * @param result
     */
    private void getUserInfoSuccess(String reqParameter, String result) {
        log.info("获取用户信息成功");
    }

    /** 开仓成功的处理
     * @param reqParameter
     * @param result
     */

    private void openOrderSuccess(String reqParameter, String result) {
        log.info("openOrderSuccess执行了,收到返回的参数为:" + result);
        // 1.将用户的请求参数和mt5返回的结果中的orderid存到数据库中
        Map resMap = JSON.parseObject(result);
        // 更新请求参数表记录的状态以及数据
        String comment = (String) resMap.get("comment");
        Object priceObj = resMap.get("price");
        BigDecimal sl = (BigDecimal) resMap.get("sl");
        BigDecimal tp = (BigDecimal) resMap.get("tp");
        BigDecimal price = null;
        if (priceObj instanceof Integer) {
            price = BigDecimal.valueOf(((Integer) priceObj).doubleValue());
        } else {
            price = (BigDecimal) priceObj;
        }
        Long order = GetMemberId.getOrder(comment);
        UserMt5OrderParameter userMt5OrderParameter = UserMt5OrderParameter.builder()
                .order(order).status(ProjectConsts.ORDERED)
                .price(price).tp(tp).sl(sl)
                .build();
        userMt5OrderParameterMapper.updateByPrimaryKeySelective(userMt5OrderParameter);
    }

    /**  mt5推送的订单消息的处理
     * @param message
     */
    public void getOrederInfoFromMt5(String message) {
        log.info("收到mt5推送的订单消息:" + message);
        // 将获取到的订单信息更新到数据库中
        Map orderInfoMap = JSON.parseObject(message);
        Integer login = (Integer) orderInfoMap.get("Login");
        String comment = (String) orderInfoMap.get("Comment");
        Integer positionID = (Integer) orderInfoMap.get("PositionID");
        // 推送止盈止损平仓的订单保存平仓历史记录,系统强制平仓还没判断
        if (comment.contains("tp") || comment.contains("sl") ) {
            getHisotryOrderInfo(null, message, positionID);
        }
        // 推送的订单不是我程序上下的单，不处理
        Integer memberId = memberMt5Mapper.getMemberIdByLogin(login.toString());
        if (memberId != null) {
            Integer order = ((Integer) orderInfoMap.get("Order"));
            String symbol = (String) orderInfoMap.get("Symbol");
            Integer ordertype = (Integer) orderInfoMap.get("OrderType");
            String reqid = (String) orderInfoMap.get("reqid");
            String volumeFinal =(String) orderInfoMap.get("Volume");
            Integer digits = (Integer) orderInfoMap.get("Digits");
            Object orderPriceObj = orderInfoMap.get("OrderPrice");
            BigDecimal orderPrice = null;
            if (orderPriceObj instanceof Integer) {
                Integer orderPriceObj1 = (Integer) orderPriceObj;
                orderPrice = BigDecimal.valueOf(orderPriceObj1.doubleValue());
            } else {
                orderPrice = (BigDecimal) orderPriceObj;
            }
            Integer reason = (Integer) orderInfoMap.get("Reason");
            Integer state = (Integer) orderInfoMap.get("State");
            Integer type = (Integer) orderInfoMap.get("Type");
            Integer fillType = (Integer) orderInfoMap.get("FillType");
            Long setupTime = ((Integer) orderInfoMap.get("SetupTime")).longValue();
            Long doneTime = ((Integer) orderInfoMap.get("DoneTime")).longValue();
            Long expirationtime = ((Integer) orderInfoMap.get("ExpirationTime")).longValue();
            if (StringUtils.isEmpty(comment)) {  // 平仓的时候 comment 是空的.通过positionID获取下单时候的comment
                UserMt5OrderDetail userMt5OrderDetail = userMt5OrderDetailMapper.selectByPrimaryKey(positionID);
                    comment = userMt5OrderDetail.getComment();
            }

            Object MarginRate2 = orderInfoMap.get("MarginRate");
            Object slObj = orderInfoMap.get("SL");
            Object tpObj = orderInfoMap.get("TP");
            Double marginrate = null;
            BigDecimal sl = null;
            BigDecimal tp = null;
            if (MarginRate2 instanceof Integer) {
                Integer MarginRate3 = (Integer) MarginRate2;
                marginrate = MarginRate3.doubleValue();
            }else {
                BigDecimal MarginRate3 = (BigDecimal) MarginRate2;
                marginrate = MarginRate3.doubleValue();
            }
            if (slObj instanceof Integer) {
               sl =   BigDecimal.valueOf(((Integer) slObj).doubleValue());
            }else {
               sl = (BigDecimal) slObj;
            }
            if (tpObj instanceof Integer) {
                tp = BigDecimal.valueOf(((Integer) tpObj).doubleValue());
            }else {
                tp = (BigDecimal) tpObj;
            }

            UserMt5OrderDetail userMt5OrderDetailInsert = UserMt5OrderDetail.builder().order(order).symbol(symbol)
                    .orderType(ordertype).login(login).digits(digits).type(type)
                    .finalVolume(Double.valueOf(volumeFinal))
                    .doneTime(LocalDateTime.ofEpochSecond(doneTime,0, ZoneOffset.ofHours(8))).state(state)
                    .orderPrice(orderPrice).sl(sl).tp(tp)
                    .expirationTime(LocalDateTime.ofEpochSecond(expirationtime,0, ZoneOffset.ofHours(8)))
                    .reason(reason).marginrate(marginrate).comment(comment)
                    .setupTime(LocalDateTime.ofEpochSecond(setupTime,0, ZoneOffset.ofHours(8)))
                    .fillType(fillType).positionId(positionID)
                    .build();

            // 已经有数据就更新，没有就插入
            UserMt5OrderDetail userMt5OrderDetailQuery = userMt5OrderDetailMapper.selectByPrimaryKey(order);
            if (userMt5OrderDetailQuery == null) {
                userMt5OrderDetailMapper.insertSelective(userMt5OrderDetailInsert);
            } else {
                userMt5OrderDetailMapper.updateByPrimaryKeySelective(userMt5OrderDetailInsert);
            }
        }
    }

    /** 查询用户历史订单成功的处理
     * @param reqParameter
     * @param result
     */
    private void historyOrderInfoSuccess(String reqParameter, String result) {
        log.info("询用户历史订单成功");
    }


    /** 查询用户持仓成功的处理
     * @param reqParameter
     * @param result
     */
    private void ordersUserInfoSuccess(String reqParameter, String result) {
        // 目前无处理
        log.info("查询用户持仓成功");
    }

    /** 查询用户资金状况成功后的处理
     * @param reqParameter
     * @param result
     */
    private void marginLevelUserInfoSuccess(String reqParameter, String result) {
            //  如果返回的 margin margin_level 字段为空，将字段不上
        log.info("查询用户资金状况成功");

    }

    /** 出入金成功的处理
     * @param reqParameter
     * @param result
     */
    @CustomTransaction(value = {"digiexTransactionManager", "digiexrmsTransactionManager"})
    private void ioMoneySuccess(String reqParameter, String result) {
        // 这里的事务 没起到应有的作用？？？？

        // 扣除用户钱包usd余额, 钱包账户单位为分，所以需要乘100
        Map reqParameterMap = JSON.parseObject(reqParameter);
        BigDecimal amount = null;
        Object amountObj =  reqParameterMap.get("amount");
        if (amountObj instanceof BigDecimal) { // BigDecimal
            amount = (BigDecimal) amountObj;
        } else { // int
            amount = new BigDecimal(((Integer) amountObj).toString());
        }

        Map resMap = JSON.parseObject(result);
        int memberId = Integer.parseInt(GetMemberId.getMemberId(result));
        amount = amount.multiply(new BigDecimal("100"));
        DMemberAccountIncrement memberAccountIncrement = DMemberAccountIncrement.builder()
                .memberId(memberId).usdBalanceIncrement( -amount.longValue()).build();
        dMemberAccountMapper.updateAccountByPrimaryKeySelective(memberAccountIncrement);

        /*System.out.println(1/0);*/

        // 添加member_mt5 的equity
        MemberMt5 memberMt5 = MemberMt5.builder().memberId(memberId).equity(amount.doubleValue()).build();
        memberMt5Mapper.updateEquityByMemberId(memberMt5);

        // 保存出入金记录
        DepositUserInfo depositUserInfo = DepositUserInfo.builder()
                .login((Integer) resMap.get("login")).memberId(memberId)
                .reqid((String) reqParameterMap.get("reqid")).amount(amount)
                .oprationTime(LocalDateTime.now())
                .build();
        depositUserInfoMapper.insert(depositUserInfo);
    }

    /** 修改杠杆
     * @param reqParameter
     * @param result
     */
    private void changeAccountLevelAgeSuccess(String reqParameter, String result) {
        // 更新数据库中用户的杠杆
        Map reqParameterMap = JSON.parseObject(reqParameter);
        int memberId = Integer.parseInt(GetMemberId.getMemberId(reqParameter).toString());
        int levelage = (Integer) reqParameterMap.get("leverage");
        MemberMt5 memberMt5 = MemberMt5.builder()
                .memberId(memberId)
                .leverage(levelage)
                .build();
        memberMt5Mapper.updateByPrimaryKeySelective(memberMt5);

    }



    /** mt5账户注册成功后的处理
     * @param result
     */
    private void registerSuccess(String reqParameter, String result) {
        // 将注册的请求参数和返回的login绑定并存到数据库中
        Map reqParameterMap = JSON.parseObject(reqParameter);
        Map resMap = JSON.parseObject(result);
        //  mt5 账户和密码 是否要进行mt5加密??
        MemberMt5 memberMt5 = MemberMt5.builder().memberId(Integer.parseInt(GetMemberId.getMebmerId(reqParameterMap)))
                .username((String) reqParameterMap.get("username"))
                .login((Integer) resMap.get("login"))
                .leverage((Integer) reqParameterMap.get("leverage"))
                .groupname((String) reqParameterMap.get("groupname"))
                .password((String) reqParameterMap.get("password"))
                .passwordInvestor((String) reqParameterMap.get("investor"))
                .passwordPhone((String) reqParameterMap.get("phonepwd"))
                .agentAccount(Integer.parseInt(reqParameterMap.get("agentaccount").toString()))
                .comment((String) reqParameterMap.get("comment"))
                .email((String) reqParameterMap.get("email"))
                .country((String) reqParameterMap.get("country"))
                .state((String) reqParameterMap.get("state"))
                .city((String) reqParameterMap.get("city"))
                .address((String) reqParameterMap.get("address"))
                .phone((String) reqParameterMap.get("phone"))
                .zipcode((String) reqParameterMap.get("zipcode"))
                .build();
        memberMt5Mapper.insertSelective(memberMt5);
    }

    /** 发送到mt5请求失败后的处理
     * @param opration 操作
     * @param message mt5返回数据
     */
    public void oprationFall(String opration, String reqParameter, String message) {
        log.info("本次" + opration + "操作失败");
        try {
            WebSocketToAppService webSocketToAppService = WebSocketToAppService.webSocketToApp2S
                    .get(GetMemberId.getMemberId(reqParameter));
            webSocketToAppService.sendMessage(message);
            // 根据不同的操作，请求失败后做不同的处理
            switch (opration) {
                case "register":  // 注册失败后的处理
                    mt5RequestFallService.registerFall(reqParameter, message);
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    /** 8090mt5服务器返回结果的处理
     * @param message
     */
    public void sent2Mt5Handle(String message) {
        // 根据reqid 中的opration字段判断mt5响应结果对应的请求
        Map resMap = null;
        try {
            resMap = JSON.parseObject(message);
            String reqid = (String) resMap.get("reqid");
            String[] reqids = reqid.split("\\.");
            String opration = reqids[reqids.length - 1];
            String reqParameter = WebSocketToAppService.reqParamters.get(reqids[0] + "." + reqids[1]);

            // 请求成功以及失败以后的处理,error == null 针对查询用户资金，error再集合里面的情况
            String error = (String) resMap.get("error");
            if ("".equals(error) || error == null) {
                oprationSuccess(opration, message, reqParameter);
            } else {
                oprationFall(opration, reqParameter, message);
            }

            // 返回给前端前的处理,将message种缺少的数据补全
            switch (opration) {
                case "ordersuserinfo": // 用户持仓字段
                    message =  addMessage(message);
                    break;
                case "historyorderinfo":
                    message = addTotalProfit(message);  // 用户历史订单，算平仓盈利
                    break;
                case "marginleveluserinfo":
                    message = addLostField(message);
                    break;
            }

            Send2Mq.sendObject2Mq(message, RabbitMqConfig.EXCHANGE_QUEUE_MESSAGE,
                    RabbitMqConfig.ROUTINGKEY_QUEUE_MESSAGE, rabbitTemplate);
        } catch (JSONException e) {
            e.printStackTrace();
            // 发送到mt5请求参数错误的处理请求参数
            String[] strings = message.split("req format error: ");
            String reqStr = (String) strings[1];
            String memberId = GetMemberId.getMemberId(reqStr);
            try {
                WebSocketToAppService.webSocketToApp2S.get(memberId).sendMessage(message);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    private String deleteOperation(String message, String[] reqids) {
        Map messageMap = JSON.parseObject(message);
        messageMap.put("reqid", reqids[0] + "." + reqids[1]);
        message = JSON.toJSONString(messageMap);
        return message;
    }

    /** 返回请求类型给前端app
     * @param message
     * @return
     */
    private String addReqType(String message) {
        Map resMap = JSON.parseObject(message);
        String reqid = (String) resMap.get("reqid");
        String[] reqids = reqid.split("\\.");
        String reqid2 = reqids[0] + "." + reqids[1];
        resMap.put("reqType", WebSocketToAppService.reqType.get(reqid2));
        return JSON.toJSONString(resMap);
    }

    /**  补全查询账户资金确实字段
     * @param message
     * @return
     */
    private String addLostField(String message) {
        Map resMap = JSON.parseObject(message);
        JSONArray userLists = (JSONArray) resMap.get("UserLists");
        for (Object user : userLists) {
            JSONObject jsonUser = (JSONObject) user;
            Object marginObj =jsonUser.get("margin");
            if (marginObj == null) {
                jsonUser.put("margin", "0.00");
                jsonUser.put("margin_level", "0.00");
            }
        }
        resMap.put("UserLists", userLists);
        return JSON.toJSONString(resMap);
    }

    /** 已平仓盈利
     * @param message
     * @return
     */
    private String addTotalProfit(String message) {
        // 已平仓盈利
        Map resMap = JSON.parseObject(message);
        JSONArray historyOrders = (JSONArray) resMap.get("historyorders");
        BigDecimal totalProfit = new BigDecimal(0.00);
        if (historyOrders != null) {
            for (Object order : historyOrders) {
                JSONObject jsonObject = (JSONObject) order;
                String profit = (String) jsonObject.get("Profit");
                totalProfit = totalProfit.add(new BigDecimal(profit));
            }
            resMap.put("totalProfit", totalProfit.setScale(2, BigDecimal.ROUND_UNNECESSARY).toString());
        } else { // 没有平仓单，平仓盈利为0
            resMap.put("totalProfit", "0.00");
        }
        return JSON.toJSONString(resMap);
    }

    /**  补全用户持仓,前端缺少的数据-->货币中文名称数据
     * @param message mt5返回的
     * @return
     */
    private String addMessage(String message) {
        Map resMap = JSON.parseObject(message);
        Map chinessNameMap = new HashMap();
        JSONArray filledOrder = (JSONArray) resMap.get("FilledOrder");
        BigDecimal totalProfit = new BigDecimal(0.00);
        List<MarketDTO> marketDTOList = marketMapper.getCoinName();
        for (MarketDTO marketDTO : marketDTOList) {
            chinessNameMap.put(marketDTO.getCoin(), marketDTO.getCoinName());
        }

        // 有持仓订单
        if (filledOrder != null) {
            for (Object order : filledOrder) {
                JSONObject jsonObject = (JSONObject) order;
                String symbol = jsonObject.getString("Symbol");
                String coinName = (String) chinessNameMap.get(symbol);
                // 货币中文名称
                jsonObject.put("coinName", coinName);
                // 获取杠杆
                String comment = (String) jsonObject.get("Comment");
                Long orderCommen = GetMemberId.getOrder(comment);
                UserMt5OrderParameter userMt5OrderParameter = userMt5OrderParameterMapper.selectByPrimaryKey(orderCommen);
                Integer leverage = userMt5OrderParameter.getLeverage();
                jsonObject.put("Leverage", leverage);
                // 预付款
                String volume = (String) jsonObject.get("Volume");
                BigDecimal volumeBigDecimal = new BigDecimal(volume);
                BigDecimal subsist = null;
                subsist = BigDecimal.valueOf(100000.00)
                        .divide(BigDecimal.valueOf(leverage.doubleValue()), 2, BigDecimal.ROUND_UNNECESSARY)
                        /*.divide(BigDecimal.valueOf(100.00), 2, BigDecimal.ROUND_UNNECESSARY)*/
                        .multiply(new BigDecimal(volume));
                jsonObject.put("subsist", subsist);
                // 持仓盈利
                String profit = (String) jsonObject.get("Profit");
                totalProfit = totalProfit.add(new BigDecimal(profit));
            }
            resMap.put("FilledOrder", filledOrder);
            resMap.put("totalProfit", totalProfit.setScale(2, BigDecimal.ROUND_UNNECESSARY).toString());
        } else { // 没有持仓单,持仓盈利为0
           /* Double doubleValue = totalProfit.doubleValue();*/
            resMap.put("totalProfit", "0.00");
        }

        // 补充挂单数据 PlaceOrders
        JSONArray placeOrders = (JSONArray) resMap.get("PlaceOrders");
        if (placeOrders != null) {
            for (Object placeOrder : placeOrders) {
                JSONObject jsonPlaceOrder = (JSONObject) placeOrder;
                // 杠杆
                String comment = (String) jsonPlaceOrder.get("Comment");
                Long orderCommen = GetMemberId.getOrder(comment);
                UserMt5OrderParameter userMt5OrderParameter = userMt5OrderParameterMapper.selectByPrimaryKey(orderCommen);
                Integer leverage = userMt5OrderParameter.getLeverage();
                jsonPlaceOrder.put("Leverage", leverage);
                String coinName = (String) chinessNameMap.get(jsonPlaceOrder.get("Symbol"));
                // 货币中文名称
                jsonPlaceOrder.put("coinName", coinName);
                // 预付款
                String volume = (String) jsonPlaceOrder.get("VolumeInitial");
                BigDecimal volumeBigDecimal = new BigDecimal(volume);
                BigDecimal subsist = null;
                subsist = BigDecimal.valueOf(100000.00)
                        .divide(BigDecimal.valueOf(leverage.doubleValue()), 2, BigDecimal.ROUND_UNNECESSARY)
                       /* .divide(BigDecimal.valueOf(100.00), 2, BigDecimal.ROUND_UNNECESSARY)*/
                       .multiply(volumeBigDecimal);
                jsonPlaceOrder.put("subsist", subsist.toString());
            }
            resMap.put("PlaceOrders", placeOrders);
        }
        return JSON.toJSONString(resMap);
    }

    private void closeOrderSuccess(String reqParameter, String result) {
        log.info("平仓成功");
        this.openOrderSuccess(reqParameter,result);
        getHisotryOrderInfo(reqParameter, result, null);

    }

    /**
     * @param reqParameter 手动平仓的请求参数
     * @param result 手动平仓请求返回的参数
     * @param Order  开仓订单的Order
     */
    public void getHisotryOrderInfo(String reqParameter, String result, Integer Order) {
        // 获取平仓盈利存起来
        Map resMap = (Map) JSON.parse(result);
        Map reqParameter1 = (Map) JSON.parse(reqParameter);
        Integer openOrder = null;
        if (Order == null) {
            openOrder = (Integer) reqParameter1.get("order");
        } else {
            openOrder = Order;
        }
        Integer login = (Integer) resMap.get("Login");
        Map<String,Object> reqParameterMap2 = new HashMap();
        reqParameterMap2.put("reqtype", "historyorderinfo");
        reqParameterMap2.put("reqid", openOrder);
        reqParameterMap2.put("login", login );
        reqParameterMap2.put("symbol", "" );
        reqParameterMap2.put("tradeType", 0 );
        reqParameterMap2.put("fromtime", 1516191480); // 2018-01-17 20:18:00
        reqParameterMap2.put("endtime", 1637506315); // 2021-11-21 22:51:55
        String reqParameterJson = JSON.toJSONString(reqParameterMap2);
        log.info("webSocketSaveOrderHistory发送到mt5的请求参数为:" + reqParameterJson);
        webSocketSaveOrderHistory.send(reqParameterJson);
    }


    /** 保持历史订单数据
     * @param message
     */
    public void saveOrderHistory(String message) {
        log.info("查询用户历史订单成功，数据为:" +  message);
        Map hisotoryOrderMap = (Map) JSON.parse(message);
        String order = (String) hisotoryOrderMap.get("reqid");
        JSONArray historyorders = (JSONArray) hisotoryOrderMap.get("historyorders");
        if (historyorders != null) {
            Integer login = (Integer) hisotoryOrderMap.get("login");
            for (Object historyorder : historyorders) {
                JSONObject historyOrderJSONObject = (JSONObject) historyorder;
                Integer historyOrder = (Integer) historyOrderJSONObject.get("OpenOrder");
                Integer timeMsc = (Integer) historyOrderJSONObject.get("TimeMsc");
                if (order.equals(historyOrder.toString())) {
                    log.info("本次平仓单的数据为:" + historyOrderJSONObject.toJSONString());
                    Integer createTime = (Integer) historyOrderJSONObject.get("CreateTime");
                    Integer closeTime = (Integer) historyOrderJSONObject.get("CloseTime");
                    BigDecimal volume = (BigDecimal) historyOrderJSONObject.get("Volume");
                    HistoryOrder historyOrderInsert = HistoryOrder.builder()
                            .openOrder(order).login(login)
                            .closeOrder(((Integer) historyOrderJSONObject.get("CloseOrder")).toString())
                            .action((Integer) historyOrderJSONObject.get("Action"))
                            .createTime(LocalDateTime.ofEpochSecond(createTime.longValue(), 0, ZoneOffset.ofHours(8)))
                            .closeTime(LocalDateTime.ofEpochSecond(closeTime.longValue(), 0, ZoneOffset.ofHours(8)))
                            .comment((String) historyOrderJSONObject.get("Comment"))
                            .openPrice(new BigDecimal((String) historyOrderJSONObject.get("OpenPrice")))
                            .commission((String) historyOrderJSONObject.get("Commission"))
                            .price(new BigDecimal((String) historyOrderJSONObject.get("Price")))
                            .profit(new BigDecimal((String) historyOrderJSONObject.get("Profit")))
                            .rateMargin(new BigDecimal((String) historyOrderJSONObject.get("RateMargin")))
                            .storage(new BigDecimal((String) historyOrderJSONObject.get("Storage")))
                            .symbol((String) historyOrderJSONObject.get("Symbol"))
                            .timeMsc(timeMsc.longValue()).volume(volume.doubleValue())
                            .build();
                    HistoryOrder historyOrderIsExist = historyOrderMapper.selectByPrimaryKey(order);
                    if (historyOrderIsExist == null) {
                        historyOrderMapper.insertSelective(historyOrderInsert);
                    }
                }

            }
        }


    }

    /**  存储用户订单日收益和用户日收益
     * @param message
     */
    public void saveCalculation(String message) {
        Map resMap = (Map) JSON.parse(message);
        JSONArray positionLists = (JSONArray) resMap.get("PositionLists");
        // 用户订单日收益
        for (Object positionList : positionLists) {
            JSONObject position = (JSONObject) positionList;
            UserManualOrderEarnings userManualorderEarnings = UserManualOrderEarnings.builder()
                    .position(((Integer) position.get("Position")).longValue())
                    .login(((Integer) position.get("Login")).longValue())
                    .msc(LocalDateTime.ofEpochSecond(
                            ((Long) position.get("Msc"))/1000,
                            0, ZoneOffset.ofHours(8)
                            )
                    )
                    .price(new BigDecimal((String) position.get("Price")))
                    .priceSl(new BigDecimal(position.get("PriceSL").toString()))
                    .priceTp(new BigDecimal(position.get("PriceTP").toString()))
                    .profit(new BigDecimal((String) position.get("Profit")))
                    .swap(new BigDecimal((String) position.get("Swap")))
                    .symbol((String) position.get("Symbol"))
                    .openTime(LocalDateTime.ofEpochSecond(
                            ((Integer) position.get("Time")).longValue(),
                            0, ZoneOffset.ofHours(8)
                            )
                    )
                    .earningsTime(LocalDateTime.now()).type((Integer) position.get("Type"))
                    .volume( Double.valueOf((String) position.get("Volume")))
                    .build();
            userManualorderEarningsMapper.insertSelective(userManualorderEarnings);
        }

        // 用户日收益
        Long[] logins = memberMt5Mapper.countAccount();
        for (Long login : logins) {
            UserManualOrderDayEarnings userManualOrderDayEarnings = userManualOrderDayEarningsMapper
                    .calculationUserDayProfit(login);
            if (userManualOrderDayEarnings !=null) {
                userManualOrderDayEarningsMapper.insertSelective(userManualOrderDayEarnings);
            }
        }
    }
}
