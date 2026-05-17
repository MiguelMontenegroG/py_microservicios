import asyncio
import logging
from pathlib import Path
from typing import Optional

from aiosmtplib import SMTP
from jinja2 import Environment, FileSystemLoader, TemplateNotFound

from src.config import settings

logger = logging.getLogger(__name__)

# --- Estadisticas en memoria ---
_stats = {"enviados": 0, "errores": 0}

def get_stats() -> dict:
    return dict(_stats)

def reset_stats() -> None:
    _stats["enviados"] = 0
    _stats["errores"] = 0

# --- Plantillas Jinja2 ---
_templates_dir = Path(__file__).parent / "templates"
_jinja_env = Environment(loader=FileSystemLoader(str(_templates_dir)))


def _render_template(template_name: str, context: dict) -> str:
    """Renderiza una plantilla HTML con Jinja2."""
    try:
        template = _jinja_env.get_template(template_name)
        return template.render(**context)
    except TemplateNotFound:
        logger.error("Plantilla no encontrada: %s", template_name)
        raise


async def send_email(
    to: str,
    subject: str,
    template: str,
    context: dict,
    *,
    _smtp_host: Optional[str] = None,
    _smtp_port: Optional[int] = None,
    _smtp_from: Optional[str] = None,
) -> bool:
    """
    Envia un email renderizando una plantilla HTML.

    Args:
        to: Direccion de correo destino.
        subject: Asunto del correo.
        template: Nombre del archivo de plantilla (ej: 'bienvenida.html').
        context: Diccionario con variables para la plantilla.

    Returns:
        True si se envio correctamente, False en caso contrario.
    """
    host = _smtp_host or settings.SMTP_HOST
    port = _smtp_port or settings.SMTP_PORT
    from_addr = _smtp_from or settings.SMTP_FROM

    try:
        html_body = _render_template(template, context)
    except Exception as e:
        logger.error("Error al renderizar plantilla '%s': %s", template, str(e))
        _stats["errores"] += 1
        return False

    message = (
        f"From: {from_addr}\r\n"
        f"To: {to}\r\n"
        f"Subject: {subject}\r\n"
        f"MIME-Version: 1.0\r\n"
        f"Content-Type: text/html; charset=UTF-8\r\n"
        f"\r\n"
        f"{html_body}"
    )

    try:
        smtp = SMTP(hostname=host, port=port, timeout=10)
        await smtp.connect()
        await smtp.sendmail(from_addr, [to], message)
        await smtp.quit()
        _stats["enviados"] += 1
        logger.info("Email enviado a %s | asunto: %s", to, subject)
        return True
    except asyncio.TimeoutError:
        logger.error("Timeout al conectar con SMTP %s:%s", host, port)
        _stats["errores"] += 1
        return False
    except Exception as e:
        logger.error("Error al enviar email a %s: %s", to, str(e))
        _stats["errores"] += 1
        return False


async def check_smtp_connection() -> bool:
    """Verifica si el servidor SMTP responde."""
    try:
        smtp = SMTP(
            hostname=settings.SMTP_HOST,
            port=settings.SMTP_PORT,
            timeout=5,
        )
        await smtp.connect()
        await smtp.quit()
        return True
    except Exception:
        return False
