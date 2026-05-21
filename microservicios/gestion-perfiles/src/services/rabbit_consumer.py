import asyncio
import json
import logging
from typing import Callable

import aio_pika

from src.config import get_settings
from src.database import connect_to_mongo
from src.schemas.perfil_schema import PerfilCreate
from src.services.perfil_service import PerfilService

logger = logging.getLogger("gestion-perfiles")

settings = get_settings()

# Bandera para controlar el consumer
_consumer_task: asyncio.Task | None = None
_should_stop = False


async def process_empleado_creado(message: aio_pika.IncomingMessage):
    """Procesar evento empleado.creado: crear perfil vacio"""
    async with message.process(ignore_processed=True):
        try:
            body = message.body.decode()
            evento = json.loads(body)
            payload = evento.get("payload", {})

            empleado_id = payload.get("empleadoId") or payload.get("id")
            email = payload.get("email", "")
            nombre = payload.get("nombre", "")
            apellido = payload.get("apellido", "")

            if not empleado_id:
                logger.error("Evento empleado.creado sin empleadoId: %s", body)
                return

            # Convertir UUID a string si es necesario
            empleado_id = str(empleado_id)

            data = PerfilCreate(
                empleadoId=empleado_id,
                email=email,
                nombre=nombre,
                apellido=apellido,
            )
            await PerfilService.crear_perfil(data)
            logger.info(f"Perfil creado desde evento empleado.creado: {empleado_id}")

        except Exception as e:
            logger.error(f"Error procesando empleado.creado: {e}", exc_info=True)


async def process_empleado_actualizado(message: aio_pika.IncomingMessage):
    """Procesar evento empleado.actualizado: sincronizar nombre, apellido y email"""
    async with message.process(ignore_processed=True):
        try:
            body = message.body.decode()
            evento = json.loads(body)
            payload = evento.get("payload", {})

            empleado_id = str(payload.get("empleadoId") or payload.get("id", ""))
            if not empleado_id:
                logger.error("Evento empleado.actualizado sin empleadoId: %s", body)
                return

            # El payload puede traer cambios en un sub-objeto "cambios"
            cambios = payload.get("cambios", payload)
            nombre = cambios.get("nombre", "")
            apellido = cambios.get("apellido", "")
            email = cambios.get("email", "")

            if nombre or apellido:
                await PerfilService.sincronizar_datos(empleado_id, nombre, apellido)

            if email:
                await PerfilService.sincronizar_email(empleado_id, email)

            if not nombre and not apellido and not email:
                logger.debug(f"Evento empleado.actualizado sin cambios relevantes: {empleado_id}")

        except Exception as e:
            logger.error(f"Error procesando empleado.actualizado: {e}", exc_info=True)

async def process_empleado_eliminado(message: aio_pika.IncomingMessage):
    """Procesar evento empleado.eliminado: archivar perfil"""
    async with message.process(ignore_processed=True):
        try:
            body = message.body.decode()
            evento = json.loads(body)
            payload = evento.get("payload", {})

            empleado_id = str(payload.get("empleadoId") or payload.get("id", ""))
            if not empleado_id:
                logger.error("Evento empleado.eliminado sin empleadoId: %s", body)
                return

            await PerfilService.archivar_perfil(empleado_id)

        except Exception as e:
            logger.error(f"Error procesando empleado.eliminado: {e}", exc_info=True)


QUEUE_CONFIGS = {
    "perfiles.empleado.creado": {
        "exchange": "empleados.exchange",
        "routing_key": "empleado.creado",
        "handler": process_empleado_creado,
    },
    "perfiles.empleado.actualizado": {
        "exchange": "empleados.exchange",
        "routing_key": "empleado.actualizado",
        "handler": process_empleado_actualizado,
    },
    "perfiles.empleado.eliminado": {
        "exchange": "empleados.exchange",
        "routing_key": "empleado.eliminado",
        "handler": process_empleado_eliminado,
    },
}


async def setup_rabbitmq(loop: asyncio.AbstractEventLoop) -> aio_pika.RobustConnection:
    """Configurar conexion RabbitMQ con reintentos"""
    max_retries = 30
    retry_delay = 1

    for attempt in range(1, max_retries + 1):
        try:
            connection = await aio_pika.connect_robust(
                settings.RABBITMQ_URL,
                loop=loop,
            )
            logger.info("Conexion RabbitMQ establecida")
            return connection
        except Exception as e:
            if attempt < max_retries:
                logger.warning(
                    f"Intento {attempt}/{max_retries} conexion RabbitMQ fallo: {e}. Reintentando en {retry_delay}s..."
                )
                await asyncio.sleep(retry_delay)
            else:
                logger.error(f"No se pudo conectar a RabbitMQ tras {max_retries} intentos")
                raise


async def start_consumer():
    """Iniciar consumidor RabbitMQ como tarea de background"""
    global _consumer_task, _should_stop

    _should_stop = False
    _consumer_task = asyncio.create_task(_consumer_loop())


async def _consumer_loop():
    """Loop principal del consumidor"""
    global _should_stop

    loop = asyncio.get_event_loop()

    try:
        connection = await setup_rabbitmq(loop)
    except Exception as e:
        logger.error(f"No se pudo iniciar consumer RabbitMQ: {e}")
        return

    try:
        async with connection:
            channel = await connection.channel()
            await channel.set_qos(prefetch_count=10)

            for queue_name, config in QUEUE_CONFIGS.items():
                # Declarar exchange
                exchange = await channel.declare_exchange(
                    config["exchange"],
                    type=aio_pika.ExchangeType.TOPIC,
                    durable=True,
                )
                # Declarar queue
                queue = await channel.declare_queue(
                    queue_name,
                    durable=True,
                )
                # Binding
                await queue.bind(exchange, routing_key=config["routing_key"])
                # Consumir
                handler: Callable = config["handler"]
                await queue.consume(handler)
                logger.info(
                    f"Consumidor listo: queue={queue_name}, "
                    f"exchange={config['exchange']}, "
                    f"routing_key={config['routing_key']}"
                )

            logger.info("Todos los consumidores RabbitMQ iniciados")

            # Mantener vivo hasta que se detenga
            while not _should_stop:
                await asyncio.sleep(1)

    except Exception as e:
        logger.error(f"Error en consumer loop: {e}", exc_info=True)
    finally:
        logger.info("Consumer RabbitMQ detenido")


def is_consumer_running() -> bool:
    """Verificar si el consumidor RabbitMQ esta activo"""
    global _consumer_task
    return _consumer_task is not None and not _consumer_task.done()


def is_consumer_running() -> bool:
    """Verificar si el consumidor RabbitMQ esta activo"""
    global _consumer_task
    return _consumer_task is not None and not _consumer_task.done()


async def stop_consumer():
    """Detener el consumidor RabbitMQ"""
    global _consumer_task, _should_stop
    _should_stop = True
    if _consumer_task:
        _consumer_task.cancel()
        try:
            await _consumer_task
        except asyncio.CancelledError:
            pass
        _consumer_task = None
        logger.info("Consumer RabbitMQ detenido correctamente")
