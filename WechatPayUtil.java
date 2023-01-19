package pers.lly.gradproj.common.util;

import com.github.wxpay.sdk.WXPay;
import com.github.wxpay.sdk.WXPayConfig;
import com.github.wxpay.sdk.WXPayUtil;
import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import javax.servlet.http.HttpServletRequest;
import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请先导入以下依赖：
 * https://mvnrepository.com/artifact/com.github.wxpay/wxpay-sdk
 * https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-web
 * https://mvnrepository.com/artifact/commons-io/commons-io
 * https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-websocket
 *
 * @author lly - 微信支付工具类
 */
@RestController
@RequestMapping("/wechatPay")
public class WechatPayUtil {

    /**
     * ===== 如何使用 =====
     * WechatPayUtil.nativePay(outTradeNo, totalFee, body, natSocket, payStatusBusiness)
     * 根据 code_url 生成微信支付二维码（在线二维码生成器 https://cli.im/）
     * * Map<String, String> responseOrderMap = WechatPayUtil.nativePay(
     * *         UUID.randomUUID().toString().replace("-", ""),
     * *         "1",
     * *         "《商品描述》",
     * *         "202.182.125.24:37858",
     * *         new WechatPayUtil.PayStatusBusiness() {
     * *             @Override
     * *             public void paySuccess() {
     * *                 System.out.println("支付成功");
     * *             }
     * *
     * *             @Override
     * *             public void payFail() {
     * *                 System.out.println("支付失败");
     * *             }
     * *         }
     * * );
     * * Optional.ofNullable(responseOrderMap).ifPresent(
     * *         message -> System.out.println(Arrays.toString(responseOrderMap.entrySet().toArray()))
     * * );
     */

    /**
     * 静态对象引用
     */
    public static final WXPay WX_PAY = new WXPay(new WechatPayConfig());
    private static PayStatusBusiness payStatusBusiness;

    /**
     * 工具类不应具有公共构造函数
     */
    private WechatPayUtil() {
    }

    /**
     * 参考文档：
     * https://pay.weixin.qq.com/wiki/doc/api/jsapi.php?chapter=9_1
     * https://pay.weixin.qq.com/wiki/doc/apiv3/apis/chapter3_4_1.shtml
     * https://yclimb.gitbook.io/wxpay/
     *
     * @param outTradeNo        商户订单号（长度限制 6 ~ 32）
     * @param totalFee          标价金额（单位为分）
     * @param body              商品描述（长度限制 1 ~ 127）
     * @param natSocket         内网穿透的网络套接字 ip:port（通知地址：异步接收微信支付结果通知的回调地址 https）
     *                          《 https://www.natfrp.com/ 》的《 #72 日本东京NTT-1 》无需实名认证：
     *                          https://202.182.125.24:37858 抑或 https://jp-tyo-ntt-1.natfrp.cloud:37858
     * @param payStatusBusiness 支付状态的业务
     * @return Map<String, String> 订单信息
     */
    public static Map<String, String> nativePay(String outTradeNo, String totalFee, String body, String natSocket,
                                                PayStatusBusiness payStatusBusiness) {
        WechatPayUtil.payStatusBusiness = payStatusBusiness;
        Map<String, String> requestParamMap = new HashMap<>(5);
        requestParamMap.put("out_trade_no", outTradeNo);
        requestParamMap.put("total_fee", totalFee);
        requestParamMap.put("body", body);
        requestParamMap.put("notify_url", "https://" + natSocket + "/wechatPay/resultCallback");
        // 交易类型 JSAPI、NATIVE、APP
        requestParamMap.put("trade_type", "NATIVE");
        Map<String, String> responseOrderMap = null;
        try {
            responseOrderMap = WX_PAY.unifiedOrder(requestParamMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return responseOrderMap;
    }

    /**
     * 千锋JAVA：
     * wx632c8f211f8122c6
     * 1497984412
     * sbNCm1JnevqI36LrEaxFwcaT0hkGxFnC
     * 无锡福瑞博课网络科技有限公司：
     * wx10104ed516a1h8z
     * 3411256105
     * 4fkZabcKhMabcSxSXYZGbaQc369nP1SI
     */
    private static class WechatPayConfig implements WXPayConfig {
        @Override
        public String getAppID() {
            // 应用 ID
            return "wx632c8f211f8122c6";
        }

        @Override
        public String getMchID() {
            // 直连商户号
            return "1497984412";
        }

        @Override
        public String getKey() {
            // 密钥
            return "sbNCm1JnevqI36LrEaxFwcaT0hkGxFnC";
        }

        @Override
        public InputStream getCertStream() {
            return null;
        }

        @Override
        public int getHttpConnectTimeoutMs() {
            return 0;
        }

        @Override
        public int getHttpReadTimeoutMs() {
            return 0;
        }
    }

    /**
     * 用户支付完成后，微信会把相关支付结果和用户信息发送给商户，商户需要接收处理该消息，并返回应答
     * 对后台通知交互时，如果微信收到商户的应答不符合规范或超时，微信认为通知失败
     * 微信会通过一定的策略定期重新发起通知，尽可能提高通知的成功率，但微信不保证通知最终能成功
     * （通知频率为 15s/15s/30s/3m/10m/20m/30m/30m/30m/60m/3h/3h/3h/6h/6h - 总计 24h4m）
     */
    @PostMapping("/resultCallback")
    public String resultCallback(HttpServletRequest request) throws Exception {
        Map<String, String> resultMap = WXPayUtil.xmlToMap(
                IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8)
        );
        String successValue = "SUCCESS";
        String resultCode = "result_code";
        // 应答成功 SUCCESS
        if (successValue.equalsIgnoreCase(resultMap.get(resultCode))) {
            Map<String, String> replySuccessMap = new HashMap<>(4);
            replySuccessMap.put("return_code", "SUCCESS");
            replySuccessMap.put("return_msg", "OK");
            replySuccessMap.put("appid", resultMap.get("appid"));
            replySuccessMap.put("mch_id", resultMap.get("mch_id"));
            WechatPayUtil.payStatusBusiness.paySuccess();
            return WXPayUtil.mapToXml(replySuccessMap);
        }
        // 应答失败 FAIL
        WechatPayUtil.payStatusBusiness.payFail();
        return null;
    }

    /**
     * 支付状态的业务
     */
    public static class PayStatusBusiness {
        /**
         * 支付成功的业务逻辑
         */
        public void paySuccess() {
        }

        /**
         * 支付失败的业务逻辑
         */
        public void payFail() {
        }
    }

    /**
     * Websocket 配置
     */
    @Bean
    public ServerEndpointExporter getServerEndpointExporter() {
        return new ServerEndpointExporter();
    }

    /**
     * 前端发送 WebSocket 连接请求，并接收后端发送的信息
     * * if ('WebSocket' in window) {
     * *     let websocketUrl = "ws://localhost:8088" + "/websocket/" + orderId;
     * *     let websocket = new WebSocket(websocketUrl);
     * *     websocket.onmessage = function(event) {
     * *         let message = event.data;
     * *         console.log("websocket-message", message);
     * *     }
     * * } else {
     * * }
     */
    @Component
    @ServerEndpoint("/websocket/{orderId}")
    public static class WebsocketServer {
        private static final ConcurrentHashMap<String, Session> SESSIONS_MAP = new ConcurrentHashMap<>();

        /**
         * 前端发送请求建立 Websocket 连接时，就会执行 @OnOpen 方法
         */
        @OnOpen
        public void open(@PathParam("orderId") String orderId, Session session) {
            System.out.println("Websocket open");
            SESSIONS_MAP.put(orderId, session);
        }

        /**
         * 前端关闭页面或者主动关闭 Websocket 连接时，就会执行 @OnClose 方法
         */
        @OnClose
        public void close(@PathParam("orderId") String orderId) {
            System.out.println("Websocket close");
            SESSIONS_MAP.remove(orderId);
        }

        /**
         * 向前端发送消息
         */
        public static void sendMessage(String orderId, String message) {
            Session session = SESSIONS_MAP.get(orderId);
            if (session != null) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
