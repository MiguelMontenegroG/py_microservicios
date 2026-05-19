import asyncio
import pytest
from typing import AsyncGenerator

from httpx import AsyncClient, ASGITransport
from src.main import app


@pytest.fixture
async def client() -> AsyncGenerator[AsyncClient, None]:
    """
    Fixture que proporciona un cliente HTTP async para testear endpoints.
    Usa ASGITransport para evitar levantar un servidor real.
    """
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.fixture(autouse=True)
def reset_email_stats():
    """Resetea estadisticas de email antes de cada test."""
    from src.email_service import reset_stats
    reset_stats()
    yield
    reset_stats()
