import asyncio
import json
import logging
from datetime import datetime
from typing import Optional

import aio_pika

from src.config import settings
from src.email_service import send_email

logger = logging.getLogger(__name__)

_running = False


def is_consumer_running() -> bool:
    return _running


async def _procesar_vacaciones_programadas(body: dict) -> None:
    """Procesa evento vacaciones.programadas: envia email de confirmacion."""
    payload = body.get("payload", body)
    email = payload.get("email")
    nombre = payload.get("nombre", "Usuario")
    fecha_inicio = payload.get("fechaInicio", "")
    fecha_fin = payload.get("fechaFin", "")

    if not email:
        logger.warning("Evento vacaciones.programadas sin email, ignorando")
        return

    # Calcular dias totales
    try:
        inicio = datetime.strptime(fecha_inicio, "%Y-%m-%d")
        fin = datetime.strptime(fecha_fin, "%Y-%m-%d")
        dias_totales = (fin - inicio).days + 1
    except (ValueError, TypeError):
        dias_totales = "---"

    context = {
        "nombre": nombre,
        "fechaInicio": fecha_inicio,
        "fechaFin": fecha_fin,
        "dias_totales": dias_totales,
    }

    await send_email(
        to=email,
        subject="Confirmacion de Vacaciones",
        template="vacaciones.html",
        context=context,
    )


async def _on_message(message: aio_pika.IncomingMessage) -> None:
    async with message.process(ignore_processed=True):
        try:
            body = json.loads(message.body.decode())
            event_type = body.get("eventType", "")
            logger.info("Evento recibido: %s", event_type)

            if event_type == "vacaciones.programadas":
                await _procesar_vacaciones_programadas(body)
            else:
                logger.warning("Tipo de evento desconocido: %s", event_type)
        except json.JSONDecodeError as e:
            logger.error("Error decodificando JSON: %s", str(e))
        except Exception as e:
            logger.error("Error procesando mensaje: %s", str(e))


async def start_vacaciones_consumer() -> None:
    """Inicia el consumidor de eventos de vacaciones.programadas."""
    global _running

    connection: Optional[aio_pika.Connection] = None
    for attempt in range(10):
        try:
            connection = await aio_pika.connect_robust(settings.RABBITMQ_URL)
            logger.info("Conectado a RabbitMQ (intento %d/10)", attempt + 1)
            break
        except Exception as e:
            logger.warning(
                "Intento %d/10: no se pudo conectar a RabbitMQ: %s",
                attempt + 1, str(e),
            )
            await asyncio.sleep(3)
    else:
        logger.error("No se pudo conectar a RabbitMQ tras 10 intentos")
        return

    try:
        channel = await connection.channel()
        await channel.set_qos(prefetch_count=10)

        exchange = await channel.get_exchange("vacaciones.exchange")

        queue = await channel.declare_queue("notif.vacaciones.programadas", durable=True)
        await queue.bind(exchange, routing_key="vacaciones.programadas")

        _running = True
        logger.info("Consumer de vacaciones iniciado - esperando mensajes...")

        await queue.consume(_on_message)
        # Mantener vivo
        while True:
            await asyncio.sleep(1)
    except Exception as e:
        logger.error("Error en consumer de vacaciones: %s", str(e))
    finally:
        if connection and not connection.is_closed:
            await connection.close()
        _running = False


async def start_all_consumers() -> None:
    """Inicia todos los consumers en paralelo."""
    from src.consumers.cuenta_consumer import start_cuenta_consumer

    await asyncio.gather(
        start_cuenta_consumer(),
        start_vacaciones_consumer(),
    )
