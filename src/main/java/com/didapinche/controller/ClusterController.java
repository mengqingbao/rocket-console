package com.didapinche.controller;

import com.alibaba.fastjson.JSONObject;
import com.didapinche.service.ClusterService;
import com.didapinche.support.JsonResult;
import com.didapinche.support.annotation.JsonBody;
import com.didapinche.util.JsonUtil;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;

/**
 * Created by tangjie on 2016/11/17.
 */
@Controller
@RequestMapping("/cluster")
public class ClusterController {

    @Resource
    private ClusterService clusterService;

    @RequestMapping(value = "/list.query", method = RequestMethod.GET)
    @ResponseBody
    @JsonBody
    public Object list() {
        Object json= JSONObject.toJSON(clusterService.list());
         
        return clusterService.list();
    }

    @RequestMapping(value = "/brokerConfig.query", method = RequestMethod.GET)
    @JsonBody
    public Object brokerConfig(@RequestParam String brokerAddr) {
        return clusterService.getBrokerConfig(brokerAddr);
    }
}
