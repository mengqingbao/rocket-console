package com.didapinche.service.impl;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.admin.TopicStatsTable;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.protocol.body.ClusterInfo;
import org.apache.rocketmq.common.protocol.body.GroupList;
import org.apache.rocketmq.common.protocol.body.TopicList;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.tools.command.CommandUtil;
import com.didapinche.config.ConfigureInitializer;
import com.didapinche.model.request.SendTopicMessageRequest;
import com.didapinche.model.request.TopicConfigInfo;
import com.didapinche.service.TopicService;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by tangjie on 2016/11/18.
 */
@Service
public class TopicServiceImpl implements TopicService {
    @Resource
    private MQAdminExt mqAdminExt;
    @Autowired
    private ConfigureInitializer configureInitializer;

    @Override
    public TopicList fetchAllTopicList() {
        try {
            TopicList topicList = mqAdminExt.fetchAllTopicList();
            topicList.setTopicList(Sets.newHashSet(Iterables.filter(topicList.getTopicList(), new Predicate<String>() {
                @Override
                public boolean apply(String s) {
                    return !(s.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX) || s.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX)); // todo 暂时先过滤掉 以后再搞出来
                }
            })));
            return topicList;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public TopicStatsTable stats(String topic) {
        try {
            return mqAdminExt.examineTopicStats(topic);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public TopicRouteData route(String topic) {
        try {
            return mqAdminExt.examineTopicRouteInfo(topic);
        } catch (Exception ex) {
            throw Throwables.propagate(ex);
        }
    }


    @Override
    public GroupList queryTopicConsumerInfo(String topic) {
        try {
            return mqAdminExt.queryTopicConsumeByWho(topic);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void createOrUpdate(TopicConfigInfo topicCreateOrUpdateRequest) {
        TopicConfig topicConfig = new TopicConfig();
        BeanUtils.copyProperties(topicCreateOrUpdateRequest, topicConfig);
        try {
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            for (String brokerName : topicCreateOrUpdateRequest.getBrokerNameList()) {
                mqAdminExt.createAndUpdateTopicConfig(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), topicConfig);
            }
        } catch (Exception err) {
            throw Throwables.propagate(err);
        }
    }

    @Override
    public TopicConfig examineTopicConfig(String topic, String brokerName) {
        ClusterInfo clusterInfo = null;
        try {
            clusterInfo = mqAdminExt.examineBrokerClusterInfo();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return mqAdminExt.examineTopicConfig(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), topic);
    }

    @Override
    public List<TopicConfigInfo> examineTopicConfig(String topic) {
        List<TopicConfigInfo> topicConfigInfoList = Lists.newArrayList();
        TopicRouteData topicRouteData = route(topic);
        for (BrokerData brokerData : topicRouteData.getBrokerDatas()) {
            TopicConfigInfo topicConfigInfo = new TopicConfigInfo();
            TopicConfig topicConfig = examineTopicConfig(topic, brokerData.getBrokerName());
            BeanUtils.copyProperties(topicConfig, topicConfigInfo);
            boolean hasSameTopicConfig = false;
//            for (TopicConfigInfo topicConfigInfoExist : topicConfigInfoList) {
//                if (topicConfigInfoExist.equals(topicConfigInfo)) {
//                    topicConfigInfoExist.getBrokerNameList().add(brokerData.getBrokerName());
//                    hasSameTopicConfig = true;
//                    break;
//                }
//            } //每一个broker的配置单独展示 变更 交互可以优化下
            if (!hasSameTopicConfig) {
                topicConfigInfo.setBrokerNameList(Lists.newArrayList(brokerData.getBrokerName()));
                topicConfigInfoList.add(topicConfigInfo);
            }
        }
        return topicConfigInfoList;
    }

    @Override
    public boolean deleteTopic(String topic, String clusterName) {
        try {
            if(StringUtils.isBlank(clusterName)){
                return deleteTopic(topic);
            }
            Set<String> masterSet = CommandUtil.fetchMasterAddrByClusterName(mqAdminExt, clusterName);
            mqAdminExt.deleteTopicInBroker(masterSet, topic);
            Set<String> nameServerSet = null;
            if (StringUtils.isNotBlank(configureInitializer.getNameSrvAddr())) {
                String[] ns = configureInitializer.getNameSrvAddr().split(";");
                nameServerSet = new HashSet<String>(Arrays.asList(ns));
            }
            mqAdminExt.deleteTopicInNameServer(nameServerSet, topic);
        } catch (Exception err) {
            throw Throwables.propagate(err);
        }
        return true;
    }

    @Override
    public boolean deleteTopic(String topic) {
        ClusterInfo clusterInfo = null;
        try {
            clusterInfo = mqAdminExt.examineBrokerClusterInfo();
        } catch (Exception err){
            throw Throwables.propagate(err);
        }
        for(String clusterName : clusterInfo.getClusterAddrTable().keySet()){
            deleteTopic(topic,clusterName);
        }
        return true;
    }

    @Override
    public boolean deleteTopicInBroker(String brokerName,String topic) {

        try {
            ClusterInfo clusterInfo = null;
            try {
                clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
            mqAdminExt.deleteTopicInBroker(Sets.newHashSet(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr()),topic);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return true;
    }

    @Override
    public SendResult sendTopicMessageRequest(SendTopicMessageRequest sendTopicMessageRequest) {
        DefaultMQProducer producer = new DefaultMQProducer(MixAll.SELF_TEST_PRODUCER_GROUP);
        producer.setInstanceName(String.valueOf(System.currentTimeMillis()));
        producer.setNamesrvAddr(configureInitializer.getNameSrvAddr());
        try {
            producer.start();
            Message msg = new Message(sendTopicMessageRequest.getTopic(),// topic
                    sendTopicMessageRequest.getTag(),// tag
                    sendTopicMessageRequest.getKey(),
                    sendTopicMessageRequest.getMessageBody().getBytes()// body
            );
            return producer.send(msg);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            producer.shutdown();
        }
    }



}
