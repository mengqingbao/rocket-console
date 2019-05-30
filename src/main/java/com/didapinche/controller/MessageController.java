package com.didapinche.controller;

import org.apache.rocketmq.common.Pair;
import org.apache.rocketmq.common.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import com.didapinche.model.MessageView;
import com.didapinche.service.MessageService;
import com.didapinche.support.annotation.JsonBody;
import com.didapinche.util.JsonUtil;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Created by tangjie
 * 2016/11/25
 * styletang.me@gmail.com
 */
@Controller
@RequestMapping("/message")
public class MessageController {
    private Logger logger = LoggerFactory.getLogger(MessageController.class);
    @Resource
    private MessageService messageService;

    @RequestMapping(value = "/viewMessage.query", method = RequestMethod.GET)
    @JsonBody
    public Object viewMessage(@RequestParam(required = false) String subject,@RequestParam String msgId) {
        Map<String, Object> messageViewMap = Maps.newHashMap();
        Pair<MessageView, List<MessageTrack>> messageViewListPair = messageService.viewMessage(subject,msgId);
        messageViewMap.put("messageView", messageViewListPair.getObject1());
        messageViewMap.put("messageTrackList", messageViewListPair.getObject2());
        return messageViewMap;
    }

    @RequestMapping(value = "/queryMessageByTopicAndKey.query", method = RequestMethod.GET)
    @JsonBody
    public Object queryMessageByTopicAndKey(@RequestParam String topic, @RequestParam String key) {
        return messageService.queryMessageByTopicAndKey(topic, key);
    }

    @RequestMapping(value = "/queryMessageByTopic.query", method = RequestMethod.GET)
    @JsonBody
    public Object queryMessageByTopic(@RequestParam String topic, @RequestParam long begin,
                                      @RequestParam long end) {
        return messageService.queryMessageByTopic(topic, begin, end);
    }

    @RequestMapping(value = "/viewMessageByBrokerAndOffset.query", method = RequestMethod.GET)
    @JsonBody
    public Object viewMessageByBrokerAndOffset(@RequestParam String brokerHost,@RequestParam int port, @RequestParam long offset) {
        Map<String, Object> messageViewMap = Maps.newHashMap();
        Pair<MessageView, List<MessageTrack>> messageViewListPair =  messageService.viewMessageByBrokerAndOffset(brokerHost,port, offset);
        messageViewMap.put("messageView", messageViewListPair.getObject1());
        messageViewMap.put("messageTrackList", messageViewListPair.getObject2());
        return messageViewMap;
    }

    @RequestMapping(value = "/consumeMessageDirectly.do", method = RequestMethod.POST)
    @JsonBody
    public Object consumeMessageDirectly(@RequestParam String consumerGroup,
                                         @RequestParam String msgId,
                                         /**如果需要指定特定的clientId的话，自己curl传下clientId吧*/
                                         @RequestParam(required = false) String clientId) {
        logger.info("msgId={} consumerGroup={} clientId={}", msgId, consumerGroup, clientId);
        ConsumeMessageDirectlyResult consumeMessageDirectlyResult = messageService.consumeMessageDirectly(msgId, consumerGroup, clientId);
        logger.info("consumeMessageDirectlyResult={}", JsonUtil.obj2String(consumeMessageDirectlyResult));
        return consumeMessageDirectlyResult;
    }
}
