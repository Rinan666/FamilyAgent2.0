"""Keep expected failure fixtures from polluting evaluation logs."""

from __future__ import annotations

import logging
from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar


_suppress_application_logs = ContextVar("suppress_eval_application_logs", default=False)


class _ApplicationLogFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        return not (
            _suppress_application_logs.get()
            and record.name.startswith("familyagent.ai")
        )


@contextmanager
def suppress_application_logs(enabled: bool) -> Iterator[None]:
    """Suppress application logs only while an expected failure fixture runs."""
    if not enabled:
        yield
        return

    log_filter = _ApplicationLogFilter()
    handlers = _active_handlers()
    token = _suppress_application_logs.set(True)
    for handler in handlers:
        handler.addFilter(log_filter)
    try:
        yield
    finally:
        for handler in handlers:
            handler.removeFilter(log_filter)
        _suppress_application_logs.reset(token)


def _active_handlers() -> tuple[logging.Handler, ...]:
    handlers: list[logging.Handler] = list(logging.getLogger().handlers)
    for value in logging.root.manager.loggerDict.values():
        if isinstance(value, logging.Logger):
            handlers.extend(value.handlers)
    return tuple(dict.fromkeys(handlers))
