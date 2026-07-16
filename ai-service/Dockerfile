# syntax=docker/dockerfile:1.7

ARG PYTHON_IMAGE=python:3.12-slim

FROM ${PYTHON_IMAGE}

ARG DEBIAN_MIRROR=
ARG PIP_INDEX_URL=https://pypi.org/simple

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV HOME=/home/appuser

RUN groupadd -r appgroup && useradd -r -m -d /home/appuser -g appgroup appuser

RUN if [ -n "${DEBIAN_MIRROR}" ]; then \
        sed -i \
            -e "s|http://deb.debian.org/debian|${DEBIAN_MIRROR}/debian|g" \
            -e "s|http://deb.debian.org/debian-security|${DEBIAN_MIRROR}/debian-security|g" \
            -e "s|http://security.debian.org/debian-security|${DEBIAN_MIRROR}/debian-security|g" \
            /etc/apt/sources.list.d/debian.sources; \
    fi \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
    libgl1 \
    libxcb1 \
    libglib2.0-0 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /home/appuser/.insightface && chown -R appuser:appgroup /home/appuser

COPY requirements.txt ./
# InsightFace declares desktop OpenCV even though it runs with the headless
# distribution. Normalize that dependency after installation so pip can still
# verify the complete runtime environment.
RUN --mount=type=cache,target=/home/appuser/.cache/pip,uid=0,gid=0,mode=0755 \
    set -eu; \
    insightface_requirement="$(sed -n '/^insightface==/p' requirements.txt | head -n 1)"; \
    test -n "${insightface_requirement}"; \
    grep -v '^insightface==' requirements.txt > /tmp/requirements-runtime.txt; \
    pip install --upgrade pip --index-url "${PIP_INDEX_URL}"; \
    pip install -r /tmp/requirements-runtime.txt --index-url "${PIP_INDEX_URL}"; \
    pip install --no-deps "${insightface_requirement}" --index-url "${PIP_INDEX_URL}"; \
    insightface_metadata="$(find /usr/local/lib/python*/site-packages \
        -path '*/insightface-*.dist-info/METADATA' -print -quit)"; \
    test -n "${insightface_metadata}"; \
    grep -q '^Requires-Dist: opencv-python$' "${insightface_metadata}"; \
    sed -i 's/^Requires-Dist: opencv-python$/Requires-Dist: opencv-python-headless/' \
        "${insightface_metadata}"; \
    pip check; \
    rm -f /tmp/requirements-runtime.txt

COPY --chown=appuser:appgroup app ./app
COPY --chown=appuser:appgroup dip ./dip

USER appuser

EXPOSE 8000

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
