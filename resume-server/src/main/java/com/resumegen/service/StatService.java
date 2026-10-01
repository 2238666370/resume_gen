package com.resumegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.config.StatsProperties;
import com.resumegen.dto.PvUvVO;
import com.resumegen.dto.StatsOverviewVO;
import com.resumegen.dto.TrackRequest;
import com.resumegen.dto.TrendPointVO;
import com.resumegen.entity.AccessLog;
import com.resumegen.mapper.AccessLogMapper;
import com.resumegen.mapper.ResumeMapper;
import com.resumegen.mapper.SysUserMapper;
import com.resumegen.mq.MessageQueue;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 埋点采集与流量统计：
 * 原始事件异步入队（R10-O2）→ 消费者落 access_log；分钟级 PV 预聚合到 stat_minute；
 * 报表按需聚合 PV/UV。
 */
@Service
public class StatService {

    private static final long ONLINE_WINDOW_MINUTES = 5;

    private final AccessLogMapper accessLogMapper;
    private final SysUserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final StatLogWriter statLogWriter;
    private final MessageQueue messageQueue;
    private final ObjectMapper om;
    private final StatsProperties props;

    public StatService(AccessLogMapper accessLogMapper, SysUserMapper userMapper, ResumeMapper resumeMapper,
                       StatLogWriter statLogWriter, MessageQueue messageQueue,
                       ObjectMapper om, StatsProperties props) {
        this.accessLogMapper = accessLogMapper;
        this.userMapper = userMapper;
        this.resumeMapper = resumeMapper;
        this.statLogWriter = statLogWriter;
        this.messageQueue = messageQueue;
        this.om = om;
        this.props = props;
    }

    /** 记录一次访问事件：登录用户按 userId 去重，匿名按 deviceId 去重。异步时入队，失败降级同步直写。 */
    public void track(TrackRequest req, Long userId) {
        AccessLog log = buildLog(req, userId);
        if (props.getAsync().isEnabled()) {
            try {
                messageQueue.publish(StatLogConsumer.QUEUE, om.writeValueAsString(log));
                return;
            } catch (Exception ignored) {
                // 入队失败降级同步直写，保证埋点不丢
            }
        }
        statLogWriter.write(log);
    }

    public StatsOverviewVO overview() {
        long userCount = userMapper.selectCount(null);
        long resumeCount = resumeMapper.selectCount(null);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = now.plusSeconds(1);

        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setUserCount(userCount);
        vo.setResumeCount(resumeCount);
        vo.setTodayPv(accessLogMapper.countPv(from, to));
        vo.setTodayUv(accessLogMapper.countUv(from, to));
        vo.setOnlineUsers(accessLogMapper.countUv(now.minusMinutes(ONLINE_WINDOW_MINUTES), to));
        return vo;
    }

    public List<TrendPointVO> trend(String granularity, LocalDateTime from, LocalDateTime to) {
        return accessLogMapper.trend(bucketFormat(granularity), from, to);
    }

    public PvUvVO pvuv(LocalDateTime from, LocalDateTime to) {
        return new PvUvVO(accessLogMapper.countPv(from, to), accessLogMapper.countUv(from, to));
    }

    private AccessLog buildLog(TrackRequest req, Long userId) {
        AccessLog log = new AccessLog();
        log.setUserId(userId);
        log.setDeviceId(req == null ? null : req.getDeviceId());
        log.setEventType(req == null || req.getEventType() == null ? "page_view" : req.getEventType());
        log.setPage(req == null ? null : req.getPage());
        log.setResumeId(req == null ? null : req.getResumeId());
        log.setTs(LocalDateTime.now());
        return log;
    }

    private String bucketFormat(String granularity) {
        if ("minute".equalsIgnoreCase(granularity)) {
            return "%Y-%m-%d %H:%i:00";
        }
        if ("day".equalsIgnoreCase(granularity)) {
            return "%Y-%m-%d 00:00:00";
        }
        return "%Y-%m-%d %H:00:00"; // 默认 hour
    }
}
