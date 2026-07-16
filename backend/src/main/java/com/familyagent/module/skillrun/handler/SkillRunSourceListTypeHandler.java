package com.familyagent.module.skillrun.handler;

import com.familyagent.common.handler.TypedPgJsonbTypeHandler;
import com.familyagent.module.skillrun.dto.SkillRunSourceRef;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.util.List;

@MappedJdbcTypes(JdbcType.OTHER)
public class SkillRunSourceListTypeHandler extends TypedPgJsonbTypeHandler<List<SkillRunSourceRef>> {

    public SkillRunSourceListTypeHandler() {
        super(listType(SkillRunSourceRef.class));
    }
}
