package com.aikefu.mapper;

import com.aikefu.entity.ConversationMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {
    
    List<ConversationMessage> getMessagesByConversationId(@Param("conversationId") String conversationId);
    
    List<ConversationMessage> getRecentMessages(
        @Param("conversationId") String conversationId,
        @Param("limit") int limit
    );
}
