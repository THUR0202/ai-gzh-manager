package com.yuyuan.wxmp.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yuyuan.wxmp.common.ErrorCode;
import com.yuyuan.wxmp.exception.ThrowUtils;
import com.yuyuan.wxmp.model.entity.WxAccount;
import com.yuyuan.wxmp.service.WxAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.mp.api.WxMpMessageRouter;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 微信消息
 *
 * @author cq
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/wx/msg/{appId}")
@Tag(name = "微信消息 - 供微信调用")
@Slf4j
public class WxMpPortalController {

    private final WxMpService wxService;

    private final WxAccountService wxAccountService;

    private final WxMpMessageRouter messageRouter;

    @GetMapping(produces = "text/plain;charset=utf-8")
    @Operation(summary = "微信服务器的认证消息", description = "公众号接入开发模式时微信调用此接口")
    public String wxServiceAuth(@PathVariable String appId,
                          @RequestParam(name = "signature", required = false) String signature,
                          @RequestParam(name = "timestamp", required = false) String timestamp,
                          @RequestParam(name = "nonce", required = false) String nonce,
                          @RequestParam(name = "echostr", required = false) String echoStr) {

        log.info("接收到来自微信服务器的认证消息：[{}, {}, {}, {}]", signature,
                timestamp, nonce, echoStr);

        ThrowUtils.throwIf(StringUtils.isAnyBlank(signature, timestamp, nonce, echoStr), ErrorCode.PARAMS_ERROR);

        this.wxService.switchoverTo(appId);
        if (wxService.checkSignature(timestamp, nonce, signature)) {
            // 设置公众号的认证状态
            wxAccountService.update(
                    Wrappers.lambdaUpdate(WxAccount.class)
                            .eq(WxAccount::getAppId, appId)
                            .set(WxAccount::getVerified, true)
            );
            return echoStr;
        }

        return "非法请求";
    }

    @PostMapping(produces = "application/xml; charset=UTF-8")
    @Operation(summary = "微信各类消息", description = "公众号接入开发模式后才有效")
    public String post(@PathVariable String appId,
                       @RequestBody String requestBody,
                       @RequestParam("signature") String signature,
                       @RequestParam("timestamp") String timestamp,
                       @RequestParam("nonce") String nonce,
                       @RequestParam("openid") String openId,
                       @RequestParam(name = "encrypt_type", required = false) String encType,
                       @RequestParam(name = "msg_signature", required = false) String msgSignature) {
        this.wxService.switchoverTo(appId);

        ThrowUtils.throwIf(!wxService.checkSignature(timestamp, nonce, signature), ErrorCode.PARAMS_ERROR, "非法请求，可能属于伪造的请求！");

        String out = null;
        if (encType == null) {
            // 明文传输的消息
            WxMpXmlMessage inMessage = WxMpXmlMessage.fromXml(requestBody);
            WxMpXmlOutMessage outMessage = this.route(appId, inMessage);
            if (outMessage == null) {
                return "";
            }

            out = outMessage.toXml();
        } else if ("aes".equalsIgnoreCase(encType)) {
            // aes加密的消息
            WxMpXmlMessage inMessage = WxMpXmlMessage.fromEncryptedXml(requestBody, wxService.getWxMpConfigStorage(),
                    timestamp, nonce, msgSignature);
            log.info("消息解密后内容为：\n{} ", inMessage.toString());
            WxMpXmlOutMessage outMessage = this.route(appId, inMessage);
            if (outMessage == null) {
                return "";
            }

            out = outMessage.toEncryptedXml(wxService.getWxMpConfigStorage());
        }

        log.info("组装回复信息：{}", out);
        return out;
    }

    private WxMpXmlOutMessage route(String appid, WxMpXmlMessage message) {
        try {
            return messageRouter.route(appid, message);
        } catch (Exception e) {
            log.error("路由消息时出现异常！", e);
        }
        return null;
    }

}
