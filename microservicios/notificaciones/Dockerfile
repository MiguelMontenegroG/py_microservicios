# Placeholder - pendiente de implementar
FROM python:3.12-alpine AS builder
WORKDIR /app
RUN apk add --no-cache gcc musl-dev
COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

FROM python:3.12-alpine
WORKDIR /app
COPY --from=builder /install /usr/local
COPY src ./src
EXPOSE 8085
CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8085"]
