package com.didapinche.service;

import org.apache.rocketmq.common.protocol.body.ConsumerConnection;
import org.apache.rocketmq.common.protocol.body.ConsumerRunningInfo;
import com.didapinche.model.ConsumerGroupRollBackStat;
import com.didapinche.model.GroupConsumeInfo;
import com.didapinche.model.TopicConsumerInfo;
import com.didapinche.model.request.ConsumerConfigInfo;
import com.didapinche.model.request.DeleteSubGroupRequest;
import com.didapinche.model.request.ResetOffsetRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by tangjie
 * 2016/11/22
 * styletang.me@gmail.com
 */
public interface ConsumerService {
    /**
     * 查询所有的消费组信息
     * @return
     */
    List<GroupConsumeInfo> queryGroupList();

    List<TopicConsumerInfo> queryConsumeStatsListByGroupName(String groupName);

    List<TopicConsumerInfo> queryConsumeStatsList(String topic, String groupName);

    Map<String,TopicConsumerInfo> queryConsumeStatsListByTopicName(String topic);


    Map<String /*consumerGroup*/ ,ConsumerGroupRollBackStat> resetOffset(ResetOffsetRequest resetOffsetRequest);


    List<ConsumerConfigInfo> examineSubscriptionGroupConfig(String group);

    boolean deleteSubGroup(DeleteSubGroupRequest deleteSubGroupRequest);

    boolean createAndUpdateSubscriptionGroupConfig(ConsumerConfigInfo consumerConfigInfo);

    Set<String> fetchBrokerNameSetBySubscriptionGroup(String group);

    ConsumerConnection getConsumerConnection(String consumerGroup);

    ConsumerRunningInfo getConsumerRunningInfo(String consumerGroup, String clientId, boolean jstack);
}
