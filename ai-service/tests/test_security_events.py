from types import SimpleNamespace

import pytest

from app.utils.security_events import record_security_event, recent_security_events, security_event_summary


class FakeRequest:
    method = "POST"
    headers = {"x-forwarded-for": "203.0.113.9, 10.0.0.1"}
    client = SimpleNamespace(host="127.0.0.1")
    state = SimpleNamespace(user_id=42)
    url = SimpleNamespace(path="/ai/tutor/explain")

    async def body(self):
        return b'{"student_message":"secret family detail should not be logged"}'


@pytest.mark.asyncio
async def test_security_event_records_metadata_without_raw_body():
    request = FakeRequest()

    event = await record_security_event(
        request,
        event_type="ROLE_HIJACK",
        status_code=400,
        reason="拒绝覆盖 FamilyAgent 的身份设定或安全边界。",
    )

    assert event.event_type == "ROLE_HIJACK"
    assert event.status_code == 400
    assert event.user_id == "42"
    assert event.ip == "203.0.113.9"
    assert event.path == "/ai/tutor/explain"
    assert event.request_chars > 0
    assert "secret family detail" not in str(event)

    latest = recent_security_events(1)[0]
    assert latest["event_type"] == "ROLE_HIJACK"
    assert "secret family detail" not in str(latest)

    summary = security_event_summary()
    assert summary["total"] >= 1
    assert summary["by_type"]["ROLE_HIJACK"] >= 1
