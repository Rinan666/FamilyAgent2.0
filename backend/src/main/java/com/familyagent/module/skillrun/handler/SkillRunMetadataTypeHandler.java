package com.familyagent.module.skillrun.handler;

import com.familyagent.common.handler.TypedPgJsonbTypeHandler;
import com.familyagent.module.skillrun.dto.SkillRunMetadata;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(SkillRunMetadata.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class SkillRunMetadataTypeHandler extends TypedPgJsonbTypeHandler<SkillRunMetadata> {

    public SkillRunMetadataTypeHandler() {
        super(SkillRunMetadata.class);
    }
}
