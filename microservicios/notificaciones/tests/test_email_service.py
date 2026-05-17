import pytest
from unittest.mock import AsyncMock, patch, MagicMock
from pathlib import Path

from src.email_service import send_email, get_stats, reset_stats, _render_template, check_smtp_connection


@pytest.fixture(autouse=True)
def clean_stats():
    reset_stats()
    yield
    reset_stats()


@pytest.mark.asyncio
async def test_send_email_bienvenida():
    """Verifica que send_email envia el email de bienvenida correctamente."""
    mock_smtp = MagicMock()
    mock_smtp.connect = AsyncMock()
    mock_smtp.sendmail = AsyncMock()
    mock_smtp.quit = AsyncMock()

    with patch("src.email_service.SMTP", return_value=mock_smtp):
        result = await send_email(
            to="juan@test.com",
            subject="Bienvenido",
            template="bienvenida.html",
            context={
                "nombre": "Juan Perez",
                "username": "juan@test.com",
                "passwordTemporal": "Abc12345",
                "url_acceso": "http://localhost:8080",
            },
            _smtp_host="localhost",
            _smtp_port=1025,
            _smtp_from="noreply@test.com",
        )

    assert result is True
    mock_smtp.connect.assert_called_once()
    mock_smtp.sendmail.assert_called_once()
    args, _ = mock_smtp.sendmail.call_args
    assert args[0] == "noreply@test.com"
    assert args[1] == ["juan@test.com"]

    stats = get_stats()
    assert stats["enviados"] == 1
    assert stats["errores"] == 0


@pytest.mark.asyncio
async def test_send_email_vacaciones():
    """Verifica que send_email envia el email de vacaciones correctamente."""
    mock_smtp = MagicMock()
    mock_smtp.connect = AsyncMock()
    mock_smtp.sendmail = AsyncMock()
    mock_smtp.quit = AsyncMock()

    with patch("src.email_service.SMTP", return_value=mock_smtp):
        result = await send_email(
            to="juan@test.com",
            subject="Vacaciones Confirmadas",
            template="vacaciones.html",
            context={
                "nombre": "Juan Perez",
                "fechaInicio": "2024-07-01",
                "fechaFin": "2024-07-15",
                "dias_totales": 15,
            },
            _smtp_host="localhost",
            _smtp_port=1025,
            _smtp_from="noreply@test.com",
        )

    assert result is True
    mock_smtp.sendmail.assert_called_once()

    stats = get_stats()
    assert stats["enviados"] == 1
    assert stats["errores"] == 0


@pytest.mark.asyncio
async def test_send_email_desactivacion():
    """Verifica que send_email envia el email de desactivacion correctamente."""
    mock_smtp = MagicMock()
    mock_smtp.connect = AsyncMock()
    mock_smtp.sendmail = AsyncMock()
    mock_smtp.quit = AsyncMock()

    with patch("src.email_service.SMTP", return_value=mock_smtp):
        result = await send_email(
            to="juan@test.com",
            subject="Cuenta Desactivada",
            template="desactivacion.html",
            context={
                "nombre": "Juan Perez",
                "motivo": "RETIRO",
                "timestamp": "2024-06-01T09:00:00Z",
            },
            _smtp_host="localhost",
            _smtp_port=1025,
            _smtp_from="noreply@test.com",
        )

    assert result is True
    mock_smtp.sendmail.assert_called_once()

    stats = get_stats()
    assert stats["enviados"] == 1
    assert stats["errores"] == 0


@pytest.mark.asyncio
async def test_send_email_smtp_timeout():
    """Verifica que send_email maneja timeout de SMTP sin excepcion."""
    mock_smtp = MagicMock()
    mock_smtp.connect = AsyncMock(side_effect=TimeoutError("Connection timed out"))

    with patch("src.email_service.SMTP", return_value=mock_smtp):
        result = await send_email(
            to="juan@test.com",
            subject="Test",
            template="bienvenida.html",
            context={
                "nombre": "Juan",
                "username": "juan@test.com",
                "passwordTemporal": "Abc123",
                "url_acceso": "http://localhost:8080",
            },
            _smtp_host="localhost",
            _smtp_port=1025,
            _smtp_from="noreply@test.com",
        )

    assert result is False
    stats = get_stats()
    assert stats["enviados"] == 0
    assert stats["errores"] == 1


@pytest.mark.asyncio
async def test_send_email_smtp_error():
    """Verifica que send_email maneja error general de SMTP sin excepcion."""
    mock_smtp = MagicMock()
    mock_smtp.connect = AsyncMock(side_effect=Exception("Connection refused"))

    with patch("src.email_service.SMTP", return_value=mock_smtp):
        result = await send_email(
            to="juan@test.com",
            subject="Test",
            template="bienvenida.html",
            context={
                "nombre": "Juan",
                "username": "juan@test.com",
                "passwordTemporal": "Abc123",
                "url_acceso": "http://localhost:8080",
            },
            _smtp_host="localhost",
            _smtp_port=1025,
            _smtp_from="noreply@test.com",
        )

    assert result is False
    stats = get_stats()
    assert stats["enviados"] == 0
    assert stats["errores"] == 1


def test_render_bienvenida_template():
    """Verifica que la plantilla bienvenida.html se renderiza correctamente."""
    context = {
        "nombre": "Juan Perez",
        "username": "juan@test.com",
        "passwordTemporal": "Abc12345",
        "url_acceso": "http://localhost:8080",
    }
    html = _render_template("bienvenida.html", context)
    assert "Juan Perez" in html
    assert "juan@test.com" in html
    assert "Abc12345" in html
    assert "http://localhost:8080" in html
    assert "Bienvenido" in html


def test_render_vacaciones_template():
    """Verifica que la plantilla vacaciones.html se renderiza correctamente."""
    context = {
        "nombre": "Juan Perez",
        "fechaInicio": "2024-07-01",
        "fechaFin": "2024-07-15",
        "dias_totales": 15,
    }
    html = _render_template("vacaciones.html", context)
    assert "Juan Perez" in html
    assert "2024-07-01" in html
    assert "2024-07-15" in html
    assert "15" in html


def test_render_desactivacion_template():
    """Verifica que la plantilla desactivacion.html se renderiza correctamente."""
    context = {
        "nombre": "Juan Perez",
        "motivo": "RETIRO",
        "timestamp": "2024-06-01T09:00:00Z",
    }
    html = _render_template("desactivacion.html", context)
    assert "Juan Perez" in html
    assert "RETIRO" in html
    assert "2024-06-01T09:00:00Z" in html


def test_get_stats_empty():
    """Verifica que las estadisticas empiezan en cero."""
    stats = get_stats()
    assert stats["enviados"] == 0
    assert stats["errores"] == 0


def test_render_template_not_found():
    """Verifica que _render_template lanza TemplateNotFound para plantilla inexistente."""
    from jinja2 import TemplateNotFound
    try:
        _render_template("no-existe.html", {})
        assert False, "Deberia haber lanzado TemplateNotFound"
    except TemplateNotFound:
        pass


def test_reset_stats():
    """Verifica que reset_stats limpia los contadores."""
    from src.email_service import _stats
    _stats["enviados"] = 10
    _stats["errores"] = 5
    reset_stats()
    stats = get_stats()
    assert stats["enviados"] == 0
    assert stats["errores"] == 0


@pytest.mark.asyncio
async def test_check_smtp_connection_ok():
    """Verifica que check_smtp_connection retorna True cuando SMTP responde."""
    mock_smtp = MagicMock()
    mock_smtp.connect = AsyncMock()
    mock_smtp.quit = AsyncMock()

    with patch("src.email_service.SMTP", return_value=mock_smtp):
        result = await check_smtp_connection()

    assert result is True


@pytest.mark.asyncio
async def test_check_smtp_connection_fail():
    """Verifica que check_smtp_connection retorna False cuando SMTP falla."""
    mock_smtp = MagicMock()
    mock_smtp.connect = AsyncMock(side_effect=Exception("Connection refused"))

    with patch("src.email_service.SMTP", return_value=mock_smtp):
        result = await check_smtp_connection()

    assert result is False


@pytest.mark.asyncio
async def test_send_email_render_error():
    """Verifica que error al renderizar plantilla se registra como error."""
    with patch("src.email_service._render_template", side_effect=Exception("Template error")):
        result = await send_email(
            to="juan@test.com",
            subject="Test",
            template="inexistente.html",
            context={},
            _smtp_host="localhost",
            _smtp_port=1025,
            _smtp_from="noreply@test.com",
        )

    assert result is False
    stats = get_stats()
    assert stats["errores"] == 1


@pytest.mark.asyncio
async def test_send_email_uses_default_settings():
    """Verifica que send_email usa configuracion por defecto cuando no se pasan params."""
    mock_smtp = MagicMock()
    mock_smtp.connect = AsyncMock()
    mock_smtp.sendmail = AsyncMock()
    mock_smtp.quit = AsyncMock()

    with patch("src.email_service.SMTP", return_value=mock_smtp):
        with patch("src.email_service.settings.SMTP_HOST", "default-host"):
            with patch("src.email_service.settings.SMTP_PORT", 1025):
                with patch("src.email_service.settings.SMTP_FROM", "default@test.com"):
                    result = await send_email(
                        to="juan@test.com",
                        subject="Test",
                        template="bienvenida.html",
                        context={
                            "nombre": "Juan",
                            "username": "juan@test.com",
                            "passwordTemporal": "Abc123",
                            "url_acceso": "http://localhost:8080",
                        },
                    )

    assert result is True
