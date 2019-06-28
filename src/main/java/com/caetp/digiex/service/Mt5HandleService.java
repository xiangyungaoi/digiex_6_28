package com.caetp.digiex.service;

import com.alibaba.fastjson.JSON;
import com.caetp.digiex.consts.ProjectConsts;
import com.caetp.digiex.entity.*;
import com.caetp.digiex.entity.mapper.MemberMt5Mapper;
import com.caetp.digiex.entity.mapper.UserMt5OrderMapper;
import com.caetp.digiex.entity.mapper.UserMt5OrderParameterMapper;
import com.caetp.digiex.entity.rmsmapper.DMemberAccountMapper;
import com.caetp.digiex.entity.rmsmapper.DMemberMapper;
import com.caetp.digiex.utli.common.GetMemberId;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**发送到mt5之前的处理以及失败处理服务
 * Created by gaoyx on 2019/5/16.
 */

@Service
public class Mt5HandleService extends BaseService{
    static Logger log = LoggerFactory.getLogger(Mt5HandleService.class);
    @Autowired
    RegisterService registerService;
    @Autowired
    DMemberAccountMapper dMemberAccountMapper;
    @Autowired
    MemberMt5Mapper memberMt5Mapper;
    @Autowired
    UserMt5OrderMapper userMt5OrderMapper;
    @Autowired
    WebSocketClient webSocketClient;
    @Autowired
    DMemberMapper dMemberMapper;
    @Autowired
    UserMt5OrderParameterMapper userMt5OrderParameterMapper;

    /**
     *  给请求参数中的reqid添加谓词,并在发送前做一些处理
     *
     */
    public String send2Mt5Before(String reqParameter) {
        Map reqParameterMap = (Map) JSON.parseObject(reqParameter);
        String reqid = (String) reqParameterMap.get("reqid");

        String reqOperation = getOperation(reqParameterMap);
        // 划转前判断
        if (judgeWallMoney(reqParameterMap, reqid, reqOperation)) return null;

        reqid = reqid + "." + reqOperation;
        reqParameterMap.put("reqid", reqid);
        return JSON.toJSONString(reqParameterMap);
    }


    private boolean judgeWallMoney(Map reqParameterMap, String reqid, String reqOperation) {
        if ("ioMoney".equals(reqOperation)) {
            DMemberAccount userUsdAmount = dMemberAccountMapper.getUserUsdAmount(GetMemberId
                    .getMebmerId(reqParameterMap));
            Long usdBalanceLong = userUsdAmount.getUsdBalance();
            BigDecimal usdBalanceBig = new BigDecimal(usdBalanceLong);
            Double usdBalanceDouble = usdBalanceBig.divide(new BigDecimal("100")).doubleValue();
            Double amount = null;
            Object amountObj = reqParameterMap.get("amount");
            if (amountObj instanceof BigDecimal) { // BigDecimal
                amount = (((BigDecimal) amountObj).doubleValue());
            } else { // int
                String amountStr = ((Integer) amountObj).toString();
                amount = Double.parseDouble(amountStr + ".00");
            }
            if (amount > 0) { // 入金
                // 钱包账户余额不足
                if (amount > usdBalanceDouble) {
                    try {
                        WebSocketToAppService.webSocketToApp2S.get(reqid).sendMessage("钱包账户余额不足");
                        return true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return false;
    }

    /** 根据请求参数确定本次是什么操作
     * @param reqParameterMap
     * @return
     */
    private String getOperation(Map reqParameterMap) {
        String reqtype = (String) reqParameterMap.get("reqtype");
        String operation = "";

        switch (reqtype) {
            case "register":   // 注册接口
                operation  = "register";
                break;
            case "deposituserinfo":  // 出入金/信用接口
                String operationtype = ((Integer) reqParameterMap.get("operationtype")).toString();
                switch (operationtype) {
                    case "2":
                        operation = "ioMoney";// 2 表示出入金（结余），
                        break;
                    case "3":
                        operation = "credit";// 3 表示信用，
                        break;
                    case "4":
                        operation = "charge";//4 费用
                        break;
                    case "5":
                        operation = "change";//5 更正
                        break;
                    case "6":
                        operation = "bonus";//6 红利
                        break;
                    case "7":
                        operation = "storage"; //7 手续费
                        break;
                }
                break;
            case "tradeorderinfo": // 市场开仓/市场挂单接口
                String order = ((Integer) reqParameterMap.get("order")).toString();
                String tradeType = ((Integer) reqParameterMap.get("tradetype")).toString();
                if ("0".equals(order)) {
                    if ("1".equals(tradeType)) {
                        operation = "openOrder";
                    } else if ("2".equals(tradeType)) {
                        operation = "suspendOrder";
                    } else if ("3".equals(tradeType)) {
                        operation = "updateOrder";
                    } else {
                        operation = "cancleOrder";
                    }
                } else {
                    operation = "closeOrder";
                }
                break;
            case "updateuserinfo":  // 更改用户信息
                operation = "updateuserinfo";
                break;
            case "ChangeAccountLevelAge":  // 修改杠杆
                operation = "ChangeAccountLevelAge";
                break;
            case "getuserinfo":  // 获取mt5账户信息
                operation = "getuserinfo";
                break;
            case "marginleveluserinfo":  // 查询账户资金
                operation = "marginleveluserinfo";
                break;
            case "ordersuserinfo":  // 用户持仓
                operation = "ordersuserinfo";
                break;
            case "historyorderinfo":  //历史订单查询
                operation = "historyorderinfo";
                break;
            case "getGroupPositionOrdersInfo": // 获取组信息
                operation = "getGroupPositionOrdersInfo";
                break;
        }
        return operation;
    }

    /** 用户登录后自动注册mt5账号
     * @param memberId
     */
    public  void autoRegister(Integer memberId) {
        MemberMt5 memberMt5 = memberMt5Mapper.selectByPrimaryKey(memberId);
        if (memberMt5 == null) {
            //  没有mt5账号，自动注册
            Map<String,Object> map = new HashMap<>();

            String reqtype = "register";
            String reqid = memberId + "." + System.currentTimeMillis();
            String mobile = dMemberMapper.selectByPrimaryKey(memberId).getMobile();
            Long login = 0L;
            Integer leverage = 1;
            String groupname = "real\\DIGUSDH-B";
            String password = "DIGUSDH" + memberId;
            String investor = memberId + "DIGUSDH";
            Integer agentaccount = 0;
            String phonepwd = password + "wd";
            map.put("reqtype", reqtype);
            map.put("reqid", reqid);
            map.put("username", mobile);
            map.put("login", login);
            map.put("leverage", leverage);
            map.put("groupname", groupname);
            map.put("password", password);
            map.put("investor", investor);
            map.put("agentaccount", agentaccount);
            map.put("phonepwd", phonepwd);
            String reqParameter = JSON.toJSONString(map);
            reqParameter = this.send2Mt5Before(reqParameter);
            WebSocketToAppService.reqParamters.put(reqid, reqParameter);

            webSocketClient.send(reqParameter);
        }
    }

    public boolean saveReqParameter(String reqParameter) {
        try {
            Map reqParameterMap = (Map) JSON.parse(reqParameter);
            if ("tradeorderinfo".equals(reqParameterMap.get("reqtype"))
                    && "0".equals(reqParameterMap.get("order").toString()))
            {
                log.info("下单接口.将请求参数插入数据库");
                //  下单接口.将请求参数插入数据库
                Integer tradetype = (Integer) reqParameterMap.get("tradetype");
                Integer mebmerId = Integer.parseInt(GetMemberId.getMebmerId(reqParameterMap));
                Object spreadObje =  reqParameterMap.get("spread");
                Double spread = null;
                if ( spreadObje instanceof Double) {
                    spread = (Double) spreadObje;
                } else if (spreadObje instanceof BigDecimal) {
                    spread = ((BigDecimal) spreadObje).doubleValue();
                } else {
                    spread = ((Integer) spreadObje).doubleValue();
                }
                BigDecimal volume = (BigDecimal) reqParameterMap.get("volume");

                // 开仓价格
                Object priceObj =  reqParameterMap.get("price");
                BigDecimal price = null;
                if (priceObj instanceof Integer) {
                    price = BigDecimal.valueOf(((Integer) priceObj).doubleValue());
                } else {
                    price = (BigDecimal) priceObj;
                }

                // 杠杆
                Integer leverage = memberMt5Mapper.getUserLeverage(mebmerId.toString()).getLeverage();
                Integer login = (Integer) reqParameterMap.get("login");
                String symbol = (String) reqParameterMap.get("symbol");
                double volumeInitial = volume.doubleValue();
                Integer fillPolicy = (Integer) reqParameterMap.get("FillPolicy");
                Integer expiration = (Integer) reqParameterMap.get("Expiration");
                Integer expirationDate = (Integer) reqParameterMap.get("ExpirationDate");
                String comment = (String) reqParameterMap.get("comment");
                Integer tradecmd = (Integer) reqParameterMap.get("tradecmd");

                // sl tp 可能为BigDecimal Integer
                Object slObj = reqParameterMap.get("sl");
                Object tpObj = reqParameterMap.get("tp");
                BigDecimal sl = null;
                BigDecimal tp = null;
                if (slObj instanceof Integer) {
                    sl =   BigDecimal.valueOf(((Integer) slObj).doubleValue());
                    tp = BigDecimal.valueOf(((Integer) tpObj).doubleValue());
                }else {
                    sl = (BigDecimal) slObj;
                    tp = (BigDecimal) tpObj;
                }
                // 生成订单号order memeberid+当前时间戳
                Long order = GetMemberId.getOrder(comment);
                UserMt5OrderParameter userMt5OrderParameter = UserMt5OrderParameter.builder()
                        .order(order).reqid((String) reqParameterMap.get("reqid"))
                        .login(login).symbol(symbol).tradeType(tradetype)
                        .tradecmd(tradecmd).price(price).spread(spread)
                        .initialVolume(volumeInitial).fillPolicy(fillPolicy)
                        .expiration(expiration)
                        .expirationDat(LocalDateTime.ofEpochSecond(expirationDate.longValue(), 0,  ZoneOffset.ofHours(8)))
                        .sl(sl).tp(tp).orderby(login.toString())
                        .comment(comment).leverage(leverage).status(ProjectConsts.ORDERING)
                        .build();
                userMt5OrderParameterMapper.insertSelective(userMt5OrderParameter);
                return true;
            } else { // 非下单接口，直接跳过。返回true
                log.info("非下单接口，直接跳过");
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                WebSocketToAppService.webSocketToApp2S.get(GetMemberId.getMemberId(reqParameter))
                        .sendMessage("出现错误下单失败:" + e.toString());
                return false;
            } catch (IOException e1) {
                e1.printStackTrace();
                return false;
            }
        }

    }
}
