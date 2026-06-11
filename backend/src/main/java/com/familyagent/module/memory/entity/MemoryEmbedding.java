package com.familyagent.module.memory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.familyagent.common.handler.PgJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "memory_embeddings", autoResultMap = true)
public class MemoryEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;
    private Long userId;
    private String sourceType;
    private Long sourceId;
    private String contentHash;
    private String embeddingModel;
    private String status;

    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Object metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
