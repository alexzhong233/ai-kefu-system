package com.aikefu.mapper;

import com.aikefu.entity.ConversationMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {
    
    @Select("SELECT * FROM t_conversation_message " +
            "WHERE conversation_id = #{conversationId} AND deleted = 0 " +
            "ORDER BY created_at ASC")
    List<ConversationMessage> getMessagesByConversationId(@Param("conversationId") String conversationId);
    
    @Select("SELECT * FROM t_conversation_message " +
            "WHERE conversation_id = #{conversationId} AND deleted = 0 " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<ConversationMessage> getRecentMessages(
        @Param("conversationId") String conversationId,
        @Param("limit") int limit
    );
}
