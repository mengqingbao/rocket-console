package com.didapinche.service.impl;


import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.admin.ConsumeStats;
import org.apache.rocketmq.common.admin.RollbackStats;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.body.ClusterInfo;
import org.apache.rocketmq.common.protocol.body.ConsumerConnection;
import org.apache.rocketmq.common.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.common.protocol.body.GroupList;
import org.apache.rocketmq.common.protocol.body.TopicList;
import org.apache.rocketmq.common.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import com.didapinche.aspect.admin.annotation.MultiMQAdminCmdMethod;
import com.didapinche.model.ConsumerGroupRollBackStat;
import com.didapinche.model.GroupConsumeInfo;
import com.didapinche.model.QueueStatInfo;
import com.didapinche.model.TopicConsumerInfo;
import com.didapinche.model.request.ConsumerConfigInfo;
import com.didapinche.model.request.DeleteSubGroupRequest;
import com.didapinche.model.request.ResetOffsetRequest;
import com.didapinche.service.ConsumerService;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import static com.google.common.base.Throwables.propagate;

/**
 * Created by tangjie
 * 2016/11/22
 * styletang.me@gmail.com
 */
@Service
public class ConsumerServiceImpl implements ConsumerService {
    private Logger logger = LoggerFactory.getLogger(ConsumerServiceImpl.class);
    @Resource
    private MQAdminExt mqAdminExt;



    @Override
    @MultiMQAdminCmdMethod
    public List<GroupConsumeInfo> queryGroupList() { // todo can improve     // todo fetch GroupConsume form team
        TopicList topicList = null;
        try {
            topicList = mqAdminExt.fetchAllTopicList();
        } catch (Exception e) {
            throw propagate(e);
        }
        List<GroupConsumeInfo> groupConsumeInfoList = Lists.newArrayList();
        for (String topic : topicList.getTopicList()) {
            if (topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX)) {
                String tconsumerGroup = topic.substring(MixAll.RETRY_GROUP_TOPIC_PREFIX.length());
                try {
                    ConsumeStats consumeStats = null;
                    try {
                        consumeStats = mqAdminExt.examineConsumeStats(tconsumerGroup);
                    } catch (Exception e) {
                        logger.warn("examineConsumeStats exception, " + tconsumerGroup, e);
                    }

                    ConsumerConnection consumerConnection = null;
                    try {
                        consumerConnection = mqAdminExt.examineConsumerConnectionInfo(tconsumerGroup);
                    } catch (Exception e) {
                        logger.warn("examineConsumerConnectionInfo exception, " + tconsumerGroup, e);
                    }

                    GroupConsumeInfo groupConsumeInfo = new GroupConsumeInfo();
                    groupConsumeInfo.setGroup(tconsumerGroup);

                    if (consumeStats != null) {
                        groupConsumeInfo.setConsumeTps((int) consumeStats.getConsumeTps());
                        groupConsumeInfo.setDiffTotal(consumeStats.computeTotalDiff());
                    }

                    if (consumerConnection != null) {
                        groupConsumeInfo.setCount(consumerConnection.getConnectionSet().size());
                        groupConsumeInfo.setMessageModel(consumerConnection.getMessageModel());
                        groupConsumeInfo.setConsumeType(consumerConnection.getConsumeType());
                        groupConsumeInfo.setVersion(MQVersion.getVersionDesc(consumerConnection.computeMinVersion()));
                    }

                    groupConsumeInfoList.add(groupConsumeInfo);
                } catch (Exception e) {
                    logger.warn("examineConsumeStats or examineConsumerConnectionInfo exception, "
                            + tconsumerGroup, e);
                }
            }
        }
        Collections.sort(groupConsumeInfoList);
        return groupConsumeInfoList;

    }

    @Override
    public List<TopicConsumerInfo> queryConsumeStatsListByGroupName(String groupName) {
        return queryConsumeStatsList(null, groupName);
    }

    @Override
    public List<TopicConsumerInfo> queryConsumeStatsList(final String topic, String groupName) {
        ConsumeStats consumeStats = null;
        try {
            consumeStats = mqAdminExt.examineConsumeStats(groupName); // todo  ConsumeStats examineConsumeStats(final String consumerGroup, final String topic) can use
        } catch (Exception e) {
            throw propagate(e);
        }
        List<MessageQueue> mqList = Lists.newArrayList(Iterables.filter(consumeStats.getOffsetTable().keySet(), new Predicate<MessageQueue>() {
            @Override
            public boolean apply(MessageQueue o) {
                return StringUtils.isBlank(topic) || o.getTopic().equals(topic);
            }
        }));
        Collections.sort(mqList);
        List<TopicConsumerInfo> topicConsumerInfoList = Lists.newArrayList();
        TopicConsumerInfo nowTopicConsumerInfo = null;
        for (MessageQueue mq : mqList) {
            if (nowTopicConsumerInfo == null || (!StringUtils.equals(mq.getTopic(), nowTopicConsumerInfo.getTopic()))) {
                nowTopicConsumerInfo = new TopicConsumerInfo(mq.getTopic());
                topicConsumerInfoList.add(nowTopicConsumerInfo);
            }
            nowTopicConsumerInfo.appendQueueStatInfo(QueueStatInfo.fromOffsetTableEntry(mq, consumeStats.getOffsetTable().get(mq)));
        }
        return topicConsumerInfoList;
    }

    @Override
    @MultiMQAdminCmdMethod
    public Map<String /*groupName*/, TopicConsumerInfo> queryConsumeStatsListByTopicName(String topic) {
        Map<String, TopicConsumerInfo> group2ConsumerInfoMap = Maps.newHashMap();
        try {
            GroupList groupList = mqAdminExt.queryTopicConsumeByWho(topic);
            for (String group : groupList.getGroupList()) {
                List<TopicConsumerInfo> topicConsumerInfoList = queryConsumeStatsList(topic, group);
                group2ConsumerInfoMap.put(group, CollectionUtils.isEmpty(topicConsumerInfoList) ? new TopicConsumerInfo(topic) : topicConsumerInfoList.get(0));
            }
            return group2ConsumerInfoMap;
        } catch (Exception e) {
            throw propagate(e);
        }
    }

    @Override
    @MultiMQAdminCmdMethod
    public Map<String, ConsumerGroupRollBackStat> resetOffset(ResetOffsetRequest resetOffsetRequest) {
        Map<String, ConsumerGroupRollBackStat> groupRollbackStats = Maps.newHashMap();
        for (String consumerGroup : resetOffsetRequest.getConsumerGroupList()) {
            try {
                Map<MessageQueue, Long> rollbackStatsMap =
                        mqAdminExt.resetOffsetByTimestamp(resetOffsetRequest.getTopic(), consumerGroup, resetOffsetRequest.getResetTime(), resetOffsetRequest.isForce());
                ConsumerGroupRollBackStat consumerGroupRollBackStat = new ConsumerGroupRollBackStat(true);
                List<RollbackStats> rollbackStatsList = consumerGroupRollBackStat.getRollbackStatsList();
                for (Map.Entry<MessageQueue, Long> rollbackStatsEntty : rollbackStatsMap.entrySet()) {
                    RollbackStats rollbackStats = new RollbackStats();
                    rollbackStats.setRollbackOffset(rollbackStatsEntty.getValue());
                    BeanUtils.copyProperties(rollbackStatsEntty.getKey(), rollbackStats);
                    rollbackStatsList.add(rollbackStats);
                }
                groupRollbackStats.put(consumerGroup, consumerGroupRollBackStat);
            }catch (MQClientException e) {
                if (ResponseCode.CONSUMER_NOT_ONLINE == e.getResponseCode()) {//不在线的可以调老方法去重置 但是无法知道重置进度
                    try {
                        mqAdminExt.resetOffsetByTimestampOld(consumerGroup, resetOffsetRequest.getTopic(), resetOffsetRequest.getResetTime(), true);
                        groupRollbackStats.put(consumerGroup, new ConsumerGroupRollBackStat(true));
                        continue;
                    } catch (Exception err) {
                        logger.error("op=resetOffset_which_not_online_error", err);

                    }
                }else {
                    logger.error("op=resetOffset_error", e);
                }
                groupRollbackStats.put(consumerGroup, new ConsumerGroupRollBackStat(false, e.getMessage()));
            }  catch (Exception e) {
                logger.error("op=resetOffset_error", e);
                groupRollbackStats.put(consumerGroup, new ConsumerGroupRollBackStat(false, e.getMessage()));
            }
        }
        return groupRollbackStats;
    }

    @Override
    @MultiMQAdminCmdMethod
    public List<ConsumerConfigInfo> examineSubscriptionGroupConfig(String group) {
        List<ConsumerConfigInfo> consumerConfigInfoList = Lists.newArrayList();
        try {
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            for (String brokerName : fetchBrokerNameSetBySubscriptionGroup(group)) {
                String brokerAddress =  clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr();
                SubscriptionGroupConfig subscriptionGroupConfig = mqAdminExt.examineSubscriptionGroupConfig(brokerAddress,group);
                consumerConfigInfoList.add(new ConsumerConfigInfo(Lists.newArrayList(brokerName),subscriptionGroupConfig));
            }
        } catch (Exception e) {
            throw propagate(e);
        }
        return consumerConfigInfoList;
    }

    @Override
    @MultiMQAdminCmdMethod
    public boolean deleteSubGroup(DeleteSubGroupRequest deleteSubGroupRequest) {
        try {
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            for (String brokerName : deleteSubGroupRequest.getBrokerNameList()) {
                logger.info("addr={} groupName={}", clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), deleteSubGroupRequest.getGroupName());
                mqAdminExt.deleteSubscriptionGroup(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), deleteSubGroupRequest.getGroupName());
            }
        } catch (Exception e) {
            throw propagate(e);
        }
        return true;
    }

    @Override
    public boolean createAndUpdateSubscriptionGroupConfig(ConsumerConfigInfo consumerConfigInfo) {
        try {
            ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
            for (String brokerName : consumerConfigInfo.getBrokerNameList()) {
                mqAdminExt.createAndUpdateSubscriptionGroupConfig(clusterInfo.getBrokerAddrTable().get(brokerName).selectBrokerAddr(), consumerConfigInfo.getSubscriptionGroupConfig());
            }
        } catch (Exception err) {
            throw Throwables.propagate(err);
        }
        return true;
    }

    @Override
    public Set<String> fetchBrokerNameSetBySubscriptionGroup(String group){
        Set<String> brokerNameSet = Sets.newHashSet();
        ConsumeStats consumeStats = null;
        try {
            consumeStats = mqAdminExt.examineConsumeStats(group);
        } catch (Exception err){
            throw propagate(err);
        }
        for(MessageQueue messageQueue : consumeStats.getOffsetTable().keySet()){
            brokerNameSet.add(messageQueue.getBrokerName());
        }
        return brokerNameSet;

    }

    @Override
    public ConsumerConnection getConsumerConnection(String consumerGroup) {
        try {
            return mqAdminExt.examineConsumerConnectionInfo(consumerGroup);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public ConsumerRunningInfo getConsumerRunningInfo(String consumerGroup, String clientId, boolean jstack) {
        try {
            return mqAdminExt.getConsumerRunningInfo(consumerGroup, clientId, jstack);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
