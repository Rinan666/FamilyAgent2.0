"""
Logging configuration helpers.
"""
import logging
import sys


def setup_logging(level: str = "DEBUG") -> logging.Logger:
    """Configure application logging."""
    logger = logging.getLogger("familyagent.ai")
    logger.setLevel(getattr(logging, level.upper(), logging.DEBUG))

    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(
        logging.Formatter(
            "%(asctime)s [%(levelname)s] %(name)s: %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        )
    )
    logger.handlers.clear()
    logger.addHandler(handler)

    return logger
