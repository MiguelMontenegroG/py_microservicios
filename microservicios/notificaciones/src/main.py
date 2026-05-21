import asyncio
import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Optional

import aio_pika
from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from src.config import settings
from src.email_service import check_smtp_connection, get_stats

# --- Logging estructurado JSON ---
from pythonjsonlogger import jsonlogger

log_handler = logging.StreamHandler()
formatter = jsonlogger.JsonFormatter(
    fmt="%(timestamp)s %(level)s %(name)s %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S",
)
log_handler.setFormatter(formatter)

root_logger = logging.getLogger()
root_logger.addHandler(log_handler)
root_logger.setLevel(logging.INFO)

# Silenciar loggers ruidosos
logging.getLogger("aio_pika").setLevel(logging.WARNING)
logging.getLogger("aiormq").setLevel(logging.WARNING)

logger = logging.getLogger(settings.SERVICE_NAME)


# --- Lifespan: iniciar/parar consumers ---
@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Iniciando servicio de notificaciones...")

    # Iniciar consumers en background
    from src.consumers.cuenta_consumer import start_all_cuenta_consumers
    from src.consumers.vacaciones_consumer import start_vacaciones_consumer
    from src.consumers.reset_consumer import start_reset_consumer

    tasks = [
        asyncio.create_task(start_all_cuenta_consumers()),
        asyncio.create_task(start_vacaciones_consumer()),
        asyncio.create_task(start_reset_consumer()),
    ]

    yield

    logger.info("Deteniendo servicio de notificaciones...")
    for task in tasks:
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass
    logger.info("Servicio detenido correctamente")


app = FastAPI(
    title="Servicio de Notificaciones",
    description="Microservicio de notificaciones por email usando eventos RabbitMQ",
    version="1.0.0",
    lifespan=lifespan,
)

# Prometheus metrics
Instrumentator().instrument(app).expose(app, endpoint="/metrics", include_in_schema=False)


# --- Health Check ---
@app.get("/health")
async def health():
    from src.consumers.cuenta_consumer import is_consumer_running as cuenta_running
    from src.consumers.vacaciones_consumer import is_consumer_running as vacaciones_running
    from src.consumers.reset_consumer import is_reset_consumer_running as reset_running

    consumer_ok = cuenta_running() or vacaciones_running() or reset_running()

    rabbitmq_status = "UP" if consumer_ok else "DOWN"
    smtp_status = "UP" if await check_smtp_connection() else "DOWN"

    overall = "UP" if rabbitmq_status == "UP" else "DOWN"

    return {
        "status": overall,
        "service": settings.SERVICE_NAME,
        "version": "1.0.0",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "dependencies": {
            "rabbitmq": rabbitmq_status,
            "smtp": smtp_status,
        },
    }


# --- Estadisticas ---
@app.get("/notifications/stats")
async def notifications_stats():
    stats = get_stats()
    return {
        "enviados": stats["enviados"],
        "errores": stats["errores"],
    }


# --- Punto de entrada para ejecucion directa ---
if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "src.main:app",
        host="0.0.0.0",
        port=settings.PORT,
        reload=False,
    )
