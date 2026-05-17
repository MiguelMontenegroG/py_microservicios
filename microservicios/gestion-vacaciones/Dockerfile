FROM golang:1.22-alpine AS builder
WORKDIR /app
RUN apk add --no-cache git
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o vacaciones-service ./cmd/main.go

FROM alpine:3.19
RUN apk --no-cache add ca-certificates tzdata curl
WORKDIR /root/
COPY --from=builder /app/vacaciones-service .
EXPOSE 8084
CMD ["./vacaciones-service"]
