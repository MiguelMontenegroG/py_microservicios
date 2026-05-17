import logging
import sys
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from src.config import get_settings
from src.database import connect_to_mongo, close_mongo_connection
from src.routers import perfil_router
from src.services.rabbit_consumer import start_consumer, stop_consumer

settings = get_settings()

# ── Configurar logging estructurado JSON ──
from pythonjsonlogger import jsonlogger

log_handler = logging.StreamHandler(sys.stdout)
formatter = jsonlogger.JsonFormatter(
    fmt="%(timestamp)s %(level)s %(name)s %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%SZ",
)
log_handler.setFormatter(formatter)

root_logger = logging.getLogger()
root_logger.addHandler(log_handler)
root_logger.setLevel(logging.INFO)

# Configurar logger propio del servicio
logger = logging.getLogger("gestion-perfiles")
logger.setLevel(logging.INFO)
logger.propagate = False
logger.addHandler(log_handler)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Manejo del ciclo de vida de la aplicacion"""
    # ── Startup ──
    logger.info("Iniciando servicio gestion-perfiles")

    # Conectar a MongoDB
    try:
        await connect_to_mongo()
        logger.info("Conexion a MongoDB establecida")
    except Exception as e:
        logger.error(f"Error conectando a MongoDB: {e}", exc_info=True)
        raise

    # Iniciar consumidor RabbitMQ
    try:
        await start_consumer()
        logger.info("Consumidor RabbitMQ iniciado")
    except Exception as e:
        logger.error(f"Error iniciando consumidor RabbitMQ: {e}", exc_info=True)
        # No fallamos el startup, el consumer se reintenta

    yield

    # ── Shutdown ──
    logger.info("Deteniendo servicio gestion-perfiles")
    await stop_consumer()
    await close_mongo_connection()
    logger.info("Servicio gestion-perfiles detenido")


# ── Crear aplicacion FastAPI ──
app = FastAPI(
    title="Gestion de Perfiles",
    description="Microservicio para gestion de perfiles de empleados",
    version="1.0.0",
    lifespan=lifespan,
)

# ── Metricas Prometheus ──
Instrumentator().instrument(app).expose(app, endpoint="/metrics")

# ── Rutas ──
app.include_router(perfil_router.router)


if __name__ == "__main__":
    uvicorn.run(
        "src.main:app",
        host="0.0.0.0",
        port=settings.PORT,
        reload=False,
    )
