package com.caetp.digiex.config;

import com.alibaba.fastjson.JSON;
import com.caetp.digiex.service.Mt5ResultService;
import com.caetp.digiex.utli.sms.AliYunSMSUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Created by gaoyx on 2019/5/13.
 */
@Configuration
@Component

public class WebSocketConfig {
    private static Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    Mt5ResultService mt5ResultService;



    /**
     * 开启WebSocket支持
     * ServerEndpointExporter作用
     * 这个Bean会自动注册使用@ServerEndpoint注解声明的websocket endpoint
     *
     * @return 这个Bean会自动注册使用@ServerEndpoint注解声明的websocket endpoint
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Bean
    public WebSocketClient webSocketClient(){
        WebSocketClient webSocketClient = null;
        try {
             webSocketClient = new WebSocketClient(new URI("ws://api.digiexclub.com:8090/"), new Draft_6455(), null, 660000000) {
                 @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    log.info("[websocket] 连接mt5服务器成功");
                }

                @Override
                public void onMessage(String message) {
                    log.info("收到mt5返回的消息"+ message);
                    mt5ResultService.sent2Mt5Handle(message);
                }

                @Override
                public void onClose(int code, String reason, boolean b) {
                    log.info("退出mt5服务器连接" + code + "----" + reason);
                  /*  // 重新连接
                    mt5ResultService.reconnect();*/
                }

                @Override
                public void onError(Exception e) {
                     e.printStackTrace();
                    log.info("mt5服务器连接错误:" + e.toString());
                }

            };
            webSocketClient.connect();
        } catch (URISyntaxException e) {
            log.info("连接mt5服务器错误抛出异常被捕抓到");
            e.printStackTrace();
        }
        return webSocketClient;
    }


    @Bean
    public WebSocketClient webSocketClientGetOrderInfo() {
         WebSocketClient webSocketClient = null;
        try {
            webSocketClient = new WebSocketClient(new URI("ws://api.digiexclub.com:8092/"), new Draft_6455(), null, 360000000) {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    log.info("订阅mt5推送订单");
                }

                @Override
                public void onMessage(String message) {
                    log.info("收到推送的订单"+ message);
                    mt5ResultService.getOrederInfoFromMt5(message);
                }

                @Override
                public void onClose(int code, String reason, boolean b) {
                    log.info("退出mt5推送订单连接:" + "---" + code + "----" + reason);
                }

                @Override
                public void onError(Exception e) {
                    e.printStackTrace();
                    log.info("mt5推送订单连接错误" );
                }
            };
            webSocketClient.connect();
        } catch (URISyntaxException e) {
            log.info("mt5推送订单连接错误抛出异常被捕抓到");
            e.printStackTrace();
        }
        return webSocketClient;
    }


    @Bean
    public WebSocketClient webSocketCheckPayMentRate(){
        WebSocketClient webSocketCheckPayMentRate = null;
        try {
            webSocketCheckPayMentRate = new WebSocketClient(new URI("ws://api.digiexclub.com:8090/"),
                    new Draft_6455(), null, 660000000)
            {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    log.info("[webSocketCheckPayMentRate] 连接mt5服务器成功");
                }

                @Override
                public void onMessage(String message) {
                    log.info("webSocketCheckPayMentRate收到mt5返回的消息"+ message);
                    Map resMap = (Map) JSON.parse(message);
                    Double margin_level = (Double) resMap.get("margin_level");
                    AliYunSMSUtils.sendCode("18377302284", AliYunSMSUtils.ACCESS_KEY,
                            AliYunSMSUtils.ACCESS_SECRET, AliYunSMSUtils.SIGN_TEXT,
                            AliYunSMSUtils.TEMPLATE_CODE_VERIFICATION, AliYunSMSUtils.MESSAGE_TEXT_VERIFICATION, "asd4");

                    // 用户的30% < 预付款比例 < 40%，需要给用户发送短信通知

                }

                @Override
                public void onClose(int code, String reason, boolean b) {
                    log.info("webSocketCheckPayMentRate退出mt5服务器连接" + code + "----" + reason);
                  /*  // 重新连接
                    mt5ResultService.reconnect();*/
                }

                @Override
                public void onError(Exception e) {
                    e.printStackTrace();
                    log.info("webSocketCheckPayMentRate和mt5服务器连接错误:" + e.toString());
                }

            };
            webSocketCheckPayMentRate.connect();
        } catch (URISyntaxException e) {
            log.info("webSocketCheckPayMentRate连接mt5服务器错误抛出异常被捕抓到");
            e.printStackTrace();
        }
        return webSocketCheckPayMentRate;
    }


    @Bean
    public WebSocketClient webSocketSaveOrderHistory(){
        WebSocketClient webSocketClient = null;
        try {
            webSocketClient = new WebSocketClient(new URI("ws://api.digiexclub.com:8090/"), new Draft_6455(), null, 660000000) {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    log.info("[webSocketSaveOrderHistory] 连接mt5服务器成功");
                }

                @Override
                public void onMessage(String message) {
                    log.info("webSocketSaveOrderHistory收到mt5返回的消息"+ message);
                    // 如果是平仓订单，将平仓单的数据存到数据库中
                    mt5ResultService.saveOrderHistory(message);
                }

                @Override
                public void onClose(int code, String reason, boolean b) {
                    log.info("webSocketSaveOrderHistory退出mt5服务器连接" + code + "----" + reason);
                  /*  // 重新连接
                    mt5ResultService.reconnect();*/
                }

                @Override
                public void onError(Exception e) {
                    e.printStackTrace();
                    log.info("webSocketSaveOrderHistory和mt5服务器连接错误:" + e.toString());
                }

            };
            webSocketClient.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return webSocketClient;
    }


    @Bean
    public WebSocketClient websocketCalculationUserOrderProfit(){
        WebSocketClient webSocketClient = null;
        try {
            webSocketClient = new WebSocketClient(new URI("ws://api.digiexclub.com:8090/"), new Draft_6455(), null, 660000000) {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    log.info("[websocketCalculationUserOrderProfit] 连接mt5服务器成功");
                }

                @Override
                public void onMessage(String message) {
                    log.info("websocketCalculationUserOrderProfit收到mt5返回的消息"+ message);
                    // 存储用户订单日收益和用户日收益
                    mt5ResultService.saveCalculation(message);
                }

                @Override
                public void onClose(int code, String reason, boolean b) {
                    log.info("websocketCalculationUserOrderProfit退出mt5服务器连接" + code + "----" + reason);
                  /*  // 重新连接
                    mt5ResultService.reconnect();*/
                }

                @Override
                public void onError(Exception e) {
                    e.printStackTrace();
                    log.info("websocketCalculationUserOrderProfit和mt5服务器连接错误:" + e.toString());
                }

            };
            webSocketClient.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return webSocketClient;
    }
}
