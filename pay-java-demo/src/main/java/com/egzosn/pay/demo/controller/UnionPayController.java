
package com.egzosn.pay.demo.controller;


import com.egzosn.pay.common.api.PayService;
import com.egzosn.pay.common.bean.*;
import com.egzosn.pay.common.http.HttpConfigStorage;
import com.egzosn.pay.common.util.sign.SignUtils;
import com.egzosn.pay.demo.request.QueryOrder;
import com.egzosn.pay.union.api.UnionPayConfigStorage;
import com.egzosn.pay.union.api.UnionPayService;
import com.egzosn.pay.union.bean.UnionTransactionType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.egzosn.pay.union.bean.UnionTransactionType.WEB;

/**
 *  银联相关
 *
 * @author: egan
 * @email egzosn@gmail.com
 * @date 2016/11/18 0:25
 */
@RestController
@RequestMapping("union")
public class UnionPayController {

    private PayService service = null;

    @PostConstruct
    public void init() {
        UnionPayConfigStorage unionPayConfigStorage = new UnionPayConfigStorage();
        unionPayConfigStorage.setMerId("700000000000001");
        //设置CertSign必须在设置证书前
        unionPayConfigStorage.setCertSign(true);
        //公钥，验签证书链格式： 中级证书路径;根证书路径
//        unionPayConfigStorage.setKeyPublic("D:/certs/acp_test_middle.cer;D:/certs/acp_test_root.cer");
        //中级证书路径
        unionPayConfigStorage.setAcpMiddleCert("D:/certs/acp_test_middle.cer");
        //根证书路径
        unionPayConfigStorage.setAcpRootCert("D:/certs/acp_test_root.cer");

        //私钥, 私钥证书格式： 私钥证书路径;私钥证书对应的密码
//        unionPayConfigStorage.setKeyPrivate("D:/certs/acp_test_sign.pfx;000000");
        // 私钥证书路径
        unionPayConfigStorage.setKeyPrivateCert("D:/certs/acp_test_sign.pfx");
        //私钥证书对应的密码
        unionPayConfigStorage.setKeyPrivateCertPwd("000000");

        unionPayConfigStorage.setNotifyUrl("http://www.pay.egzosn.com/payBack.json");
        // 无需同步回调可不填  app填这个就可以
        unionPayConfigStorage.setReturnUrl("http://www.pay.egzosn.com/payBack.json");
        unionPayConfigStorage.setSignType(SignUtils.RSA2.name());
        //单一支付可不填
        unionPayConfigStorage.setPayType("unionPay");
        unionPayConfigStorage.setInputCharset("UTF-8");
        //是否为测试账号，沙箱环境
        unionPayConfigStorage.setTest(true);
        service = new UnionPayService(unionPayConfigStorage);

        //请求连接池配置
        HttpConfigStorage httpConfigStorage = new HttpConfigStorage();
        //最大连接数
        httpConfigStorage.setMaxTotal(20);
        //默认的每个路由的最大连接数
        httpConfigStorage.setDefaultMaxPerRoute(10);
        service.setRequestTemplateConfigStorage(httpConfigStorage);

    }



    /**
     * 跳到支付页面
     * 针对实时支付,即时付款
     *
     * @param price       金额
     * @return 跳到支付页面
     */
    @RequestMapping(value = "toPay.html", produces = "text/html;charset=UTF-8")
    public String toPay( BigDecimal price) {
        //及时收款
        PayOrder order = new PayOrder("订单title", "摘要", null == price ? new BigDecimal(0.01) : price, UUID.randomUUID().toString().replace("-", ""), WEB);
        //WAP
//        PayOrder order = new PayOrder("订单title", "摘要", null == price ? new BigDecimal(0.01) : price, UUID.randomUUID().toString().replace("-", ""), UnionTransactionType.WAP);
         //企业网银支付（B2B支付）
//        PayOrder order = new PayOrder("订单title", "摘要", null == price ? new BigDecimal(0.01) : price, UUID.randomUUID().toString().replace("-", ""), UnionTransactionType.B2B);

        Map orderInfo = service.orderInfo(order);
        return service.buildRequest(orderInfo, MethodType.POST);
    }




    /**
     *  APP 获取支付预订单信息
     *
     * @return 支付预订单信息
     */
    @RequestMapping("app")
    public Map<String, Object> app() {
        Map<String, Object> data = new HashMap<>();
        data.put("code", 0);
        PayOrder order = new PayOrder("订单title", "摘要", new BigDecimal(0.01), SignUtils.randomStr());
        //App支付
        order.setTransactionType(UnionTransactionType.APP);

        //APPLE支付 苹果付
//        order.setTransactionType(UnionTransactionType.APPLE);

        data.put("orderInfo", service.orderInfo(order));
        return data;
    }

    /**
     * 获取二维码图像 APPLY_QR_CODE
     * 二维码支付
     * @param price       金额
     * @return 二维码图像
     */
    @RequestMapping(value = "toQrPay.jpg", produces = "image/jpeg;charset=UTF-8")
    public byte[] toWxQrPay( BigDecimal price) throws IOException {
        //获取对应的支付账户操作工具（可根据账户id）
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(service.genQrPay( new PayOrder("订单title", "摘要", null == price ? new BigDecimal(0.01) : price, System.currentTimeMillis()+"", UnionTransactionType.APPLY_QR_CODE)), "JPEG", baos);
        return baos.toByteArray();
    }


    /**
     * 刷卡付,pos主动扫码付款(条码付)  CONSUME
     * @param authCode        授权码，条码等
     * @param price       金额
     * @return 支付结果
     */
    @RequestMapping(value = "microPay")
    public Map<String, Object> microPay(BigDecimal price, String authCode) throws IOException {
        //获取对应的支付账户操作工具（可根据账户id）
        //条码付
        PayOrder order = new PayOrder("huodull order", "huodull order", null == price ? new BigDecimal(0.01) : price, SignUtils.randomStr(), UnionTransactionType.CONSUME);
        //设置授权码，条码等
        order.setAuthCode(authCode);
        //支付结果
        Map<String, Object> params = service.microPay(order);
        //校验
        if (service.verify(params)) {

            //支付校验通过后的处理
            //......业务逻辑处理块........


        }
        //这里开发者自行处理
        return params;
    }

    /**
     * 支付回调地址
     *
     * @param request
     *
     * @return
     */
    @RequestMapping(value = "payBack.json")
    public String payBack(HttpServletRequest request) throws IOException {

        //获取支付方返回的对应参数
        Map<String, Object> params = service.getParameter2Map(request.getParameterMap(), request.getInputStream());
        if (null == params) {
            return service.getPayOutMessage("fail", "失败").toMessage();
        }

        //校验
        if (service.verify(params)) {
            //这里处理业务逻辑
            //......业务逻辑处理块........
            return service.getPayOutMessage("success", "成功").toMessage();
        }

        return service.getPayOutMessage("fail", "失败").toMessage();
    }


    /**
     * 查询
     *
     * @param order 订单的请求体
     * @return 返回查询回来的结果集，支付方原值返回
     */
    @RequestMapping("query")
    public Map<String, Object> query(QueryOrder order) {
        return service.query(order.getTradeNo(), order.getOutTradeNo());
    }


    /**
     * 申请退款接口
     *
     * @param order 订单的请求体
     * @return 返回支付方申请退款后的结果
     */
    @RequestMapping("refund")
    public Map<String, Object> refund(RefundOrder order) {
        return service.refund(order);
    }

    /**
     * 查询退款
     *
     * @param order 订单的请求体
     * @return 返回支付方查询退款后的结果
     */
    @RequestMapping("refundquery")
    public Map<String, Object> refundquery(QueryOrder order) {
        return service.refundquery(order.getTradeNo(), order.getOutTradeNo());
    }

    /**
     * 下载对账单
     *
     * @param order 订单的请求体
     * @return 返回支付方下载对账单的结果
     */
    @RequestMapping("downloadbill")
    public Object downloadbill(QueryOrder order) {
        return service.downloadbill(order.getBillDate(), order.getBillType());
    }



}
