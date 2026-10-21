FROM python:3.12-slim

WORKDIR /app

# The lockfile is the pinned environment the tests and the image share.
COPY requirements.lock ./
RUN pip install --no-cache-dir -r requirements.lock

COPY pyproject.toml ./
COPY src ./src

ENV PYTHONPATH=/app/src
EXPOSE 8000

CMD ["uvicorn", "medical_agent.api.main:app", "--host", "0.0.0.0", "--port", "8000"]
