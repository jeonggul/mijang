package com.example.mijang.user.mapper;

import com.example.mijang.user.dto.NotificationResponse;
import com.example.mijang.user.dto.NotificationSettingsForm;
import com.example.mijang.user.dto.NotificationSettingsResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {

    List<NotificationResponse> findRecent(@Param("userId") Long userId, @Param("limit") int limit);

    int markAllRead(@Param("userId") Long userId);

    NotificationSettingsResponse findSettings(@Param("userId") Long userId);

    int upsertSettings(@Param("userId") Long userId, @Param("form") NotificationSettingsForm form);
}
