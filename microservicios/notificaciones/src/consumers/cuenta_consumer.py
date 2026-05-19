import asyncio
import json
import logging

import aio_pika

from src.config import settings
from src.email_service import send_email

logger = logging.getLogger(__name__)

_cuenta_activada_running = False
_cuenta_desactivada_running = False


def is_consumer_running() -> bool:
    return _cuenta_activada_running or _cuenta_desactivada_running


async def _connect_with_retry(service_name: str):
    """Conectar a RabbitMQ con reintentos. Retorna la conexion o None."""
    for attempt in range(10):
        try:
            connection = await aio_pika.connect_robust(settings.RABBITMQ_URL)
            logger.info("[%s] Conectado a RabbitMQ (intento %d/10)", service_name, attempt + 1)
            return connection
        except Exception as e:
            logger.warning(
                "[%s] Intento %d/10 fallido: %s", service_name, attempt + 1, str(e)
            )
            await asyncio.sleep(3)
    logger.error("[%s] No se pudo conectar a RabbitMQ tras 10 intentos", service_name)
    return None


async def _procesar_cuenta_activada(evento: dict) -> None:
    """Procesa un evento de cuenta activada y envia email de bienvenida si es primer acceso."""
    payload = evento.get("payload", evento)
    email = payload.get("email")
    nombre = payload.get("nombre", "Usuario")
    username = payload.get("email", "---")
    password_temporal = payload.get("passwordTemporal", "")
    es_primer_acceso = payload.get("esPrimerAcceso", False)

    if not email:
        logger.warning("[cuenta-activada] Evento sin email, ignorando")
        return

    if not es_primer_acceso:
        logger.info("[cuenta-activada] esPrimerAcceso=false para %s, omitiendo email", email)
        return

    await send_email(
        to=email,
        subject="Bienvenido a la Empresa - Credenciales de Acceso",
        template="bienvenida.html",
        context={
            "nombre": nombre,
            "username": username,
            "passwordTemporal": password_temporal,
            "url_acceso": "http://localhost:8080",
        },
    )
    logger.info("[cuenta-activada] Email de bienvenida enviado a %s", email)


async def _procesar_cuenta_desactivada(evento: dict) -> None:
    """Procesa un evento de cuenta desactivada y envia email de notificacion."""
    payload = evento.get("payload", evento)
    email = payload.get("email")
    nombre = payload.get("nombre", "Usuario")
    motivo = payload.get("motivo", "No especificado")
    timestamp = payload.get("timestamp", "")

    if not email:
        logger.warning("[cuenta-desactivada] Evento sin email, ignorando")
        return

    await send_email(
        to=email,
        subject="Notificacion: Cuenta Desactivada",
        template="desactivacion.html",
        context={
            "nombre": nombre,
            "motivo": motivo,
            "timestamp": timestamp,
        },
    )
    logger.info("[cuenta-desactivada] Email de desactivacion enviado a %s", email)


async def _on_message(message: aio_pika.IncomingMessage) -> None:
    """Callback para procesar mensajes de RabbitMQ de eventos de cuenta."""
    async with message.process(ignore_processed=True):
        try:
            body = json.loads(message.body.decode())
            event_type = body.get("eventType", "")

            if event_type == "cuenta.activada":
                await _procesar_cuenta_activada(body)
            elif event_type == "cuenta.desactivada":
                await _procesar_cuenta_desactivada(body)
            else:
                logger.warning("[cuenta-consumer] Evento desconocido: %s", event_type)
        except json.JSONDecodeError as e:
            logger.error("[cuenta-consumer] Error decodificando JSON: %s", str(e))
        except Exception as e:
            logger.error("[cuenta-consumer] Error procesando mensaje: %s", str(e))


async def start_cuenta_activada_consumer():
    """Consumer independiente para notif.cuenta.activada."""
    global _cuenta_activada_running
    connection = await _connect_with_retry("cuenta-activada")
    if not connection:
        return
    try:
        channel = await connection.channel()
        await channel.set_qos(prefetch_count=1)

        exchange = await channel.get_exchange("cuentas.exchange")
        queue = await channel.declare_queue("notif.cuenta.activada", durable=True)
        await queue.bind(exchange, routing_key="cuenta.activada")

        _cuenta_activada_running = True
        logger.info("[cuenta-activada] Consumer iniciado, esperando mensajes...")

        async with queue.consume(_on_message):
            await asyncio.Future()
    except Exception as e:
        logger.error("[cuenta-activada] Consumer caido: %s", str(e))
    finally:
        _cuenta_activada_running = False
        if connection and not connection.is_closed:
            await connection.close()


async def start_cuenta_desactivada_consumer():
    """Consumer independiente para notif.cuenta.desactivada."""
    global _cuenta_desactivada_running
    connection = await _connect_with_retry("cuenta-desactivada")
    if not connection:
        return
    try:
        channel = await connection.channel()
        await channel.set_qos(prefetch_count=1)

        exchange = await channel.get_exchange("cuentas.exchange")
        queue = await channel.declare_queue("notif.cuenta.desactivada", durable=True)
        await queue.bind(exchange, routing_key="cuenta.desactivada")

        _cuenta_desactivada_running = True
        logger.info("[cuenta-desactivada] Consumer iniciado, esperando mensajes...")

        async with queue.consume(_on_message):
            await asyncio.Future()
    except Exception as e:
        logger.error("[cuenta-desactivada] Consumer caido: %s", str(e))
    finally:
        _cuenta_desactivada_running = False
        if connection and not connection.is_closed:
            await connection.close()


async def start_all_cuenta_consumers():
    """Lanza ambos consumers en paralelo con asyncio.gather.
    return_exceptions=True evita que uno cancele al otro."""
    await asyncio.gather(
        start_cuenta_activada_consumer(),
        start_cuenta_desactivada_consumer(),
        return_exceptions=True,
    )