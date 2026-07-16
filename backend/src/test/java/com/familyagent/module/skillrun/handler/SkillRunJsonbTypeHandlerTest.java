package com.familyagent.module.skillrun.handler;

import com.familyagent.module.skillrun.dto.SkillRunMetadata;
import com.familyagent.module.skillrun.dto.SkillRunSourceRef;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillRunJsonbTypeHandlerTest {

    @Mock private ResultSet resultSet;
    @Mock private PreparedStatement statement;

    @Test
    void metadataHandlerReadsLegacyFieldsIntoExtraAndWritesExplicitExtra() throws Exception {
        when(resultSet.getString("metadata")).thenReturn("""
                {"savedRecordType":"FAMILY_MEMORY","confirmationId":12,"memoryId":88}
                """);
        SkillRunMetadataTypeHandler handler = new SkillRunMetadataTypeHandler();

        SkillRunMetadata metadata = handler.getNullableResult(resultSet, "metadata");

        assertEquals("FAMILY_MEMORY", metadata.getSavedRecordType());
        assertEquals(12L, metadata.getConfirmationId());
        assertEquals(88, metadata.getExtra().get("memoryId"));

        handler.setNonNullParameter(statement, 1, metadata, JdbcType.OTHER);
        ArgumentCaptor<Object> jsonCaptor = ArgumentCaptor.forClass(Object.class);
        verify(statement).setObject(eq(1), jsonCaptor.capture(), eq(Types.OTHER));
        String json = String.valueOf(jsonCaptor.getValue());
        assertTrue(json.contains("\"extra\":{\"memoryId\":88}"));
    }

    @Test
    void sourceHandlerReadsTypedFieldsAndKeepsCompatibilityDataInExtra() throws Exception {
        when(resultSet.getString("used_sources")).thenReturn("""
                [{"sourceType":"CHAT","sourceId":7,"title":"Selected message"}]
                """);

        List<SkillRunSourceRef> sources = new SkillRunSourceListTypeHandler()
                .getNullableResult(resultSet, "used_sources");

        assertEquals(1, sources.size());
        assertEquals("CHAT", sources.get(0).getSourceType());
        assertEquals(7L, sources.get(0).getSourceId());
        assertEquals("Selected message", sources.get(0).getExtra().get("title"));
    }
}
