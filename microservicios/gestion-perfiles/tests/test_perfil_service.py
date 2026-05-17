import pytest
from unittest.mock import AsyncMock, patch, MagicMock
from datetime import datetime, timezone

from src.models.perfil import PerfilDB, Direccion, RedesSociales
from src.schemas.perfil_schema import PerfilCreate, PerfilUpdate
from src.services.perfil_service import PerfilService


@pytest.fixture
def mock_collection():
    with patch("src.services.perfil_service.get_collection") as mock_get_collection:
        collection = AsyncMock()
        mock_get_collection.return_value = collection
        yield collection


@pytest.mark.asyncio
async def test_crear_perfil(mock_collection):
    mock_collection.insert_one = AsyncMock()

    data = PerfilCreate(
        empleadoId="550e8400-e29b-41d4-a716-446655440001",
        email="juan.perez@empresa.com",
        nombre="Juan",
        apellido="Perez",
    )

    perfil = await PerfilService.crear_perfil(data)

    assert perfil.empleadoId == "550e8400-e29b-41d4-a716-446655440001"
    assert perfil.email == "juan.perez@empresa.com"
    assert perfil.nombre == "Juan"
    assert perfil.apellido == "Perez"
    assert perfil.archivado is False
    assert isinstance(perfil.direccion, Direccion)
    assert isinstance(perfil.redesSociales, RedesSociales)

    mock_collection.insert_one.assert_awaited_once()


@pytest.mark.asyncio
async def test_obtener_perfil_existente(mock_collection):
    empleado_id = "550e8400-e29b-41d4-a716-446655440001"
    mock_doc = {
        "empleadoId": empleado_id,
        "email": "juan.perez@empresa.com",
        "nombre": "Juan",
        "apellido": "Perez",
        "foto": None,
        "biografia": "Desarrollador",
        "telefono": None,
        "direccion": {"calle": None, "ciudad": None, "codigoPostal": None, "pais": None},
        "redesSociales": {"linkedin": None, "github": None},
        "archivado": False,
        "createdAt": datetime.now(timezone.utc),
        "updatedAt": datetime.now(timezone.utc),
    }
    mock_collection.find_one = AsyncMock(return_value=dict(mock_doc))

    perfil = await PerfilService.obtener_perfil(empleado_id)

    assert perfil is not None
    assert perfil.empleadoId == empleado_id
    assert perfil.nombre == "Juan"
    assert perfil.biografia == "Desarrollador"

    mock_collection.find_one.assert_awaited_once_with({"empleadoId": empleado_id})


@pytest.mark.asyncio
async def test_obtener_perfil_inexistente(mock_collection):
    mock_collection.find_one = AsyncMock(return_value=None)

    perfil = await PerfilService.obtener_perfil("no-existe-id")

    assert perfil is None
    mock_collection.find_one.assert_awaited_once_with({"empleadoId": "no-existe-id"})


@pytest.mark.asyncio
async def test_actualizar_perfil(mock_collection):
    empleado_id = "550e8400-e29b-41d4-a716-446655440001"

    mock_doc_actualizado = {
        "empleadoId": empleado_id,
        "email": "juan.perez@empresa.com",
        "nombre": "Juan",
        "apellido": "Perez",
        "foto": "https://fotos.com/juan.jpg",
        "biografia": "Desarrollador senior",
        "telefono": "+52 55 1234 5678",
        "direccion": {"calle": "Av. Principal 123", "ciudad": "CDMX", "codigoPostal": "06600", "pais": "Mexico"},
        "redesSociales": {"linkedin": "https://linkedin.com/in/juanperez", "github": "https://github.com/juanperez"},
        "archivado": False,
        "createdAt": datetime.now(timezone.utc),
        "updatedAt": datetime.now(timezone.utc),
    }
    mock_collection.find_one_and_update = AsyncMock(return_value=dict(mock_doc_actualizado))

    update_data = PerfilUpdate(
        foto="https://fotos.com/juan.jpg",
        biografia="Desarrollador senior",
        telefono="+52 55 1234 5678",
        direccion={"calle": "Av. Principal 123", "ciudad": "CDMX", "codigoPostal": "06600", "pais": "Mexico"},
        redesSociales={"linkedin": "https://linkedin.com/in/juanperez", "github": "https://github.com/juanperez"},
    )

    perfil = await PerfilService.actualizar_perfil(empleado_id, update_data)

    assert perfil is not None
    assert perfil.foto == "https://fotos.com/juan.jpg"
    assert perfil.biografia == "Desarrollador senior"
    assert perfil.telefono == "+52 55 1234 5678"
    assert perfil.direccion.calle == "Av. Principal 123"

    mock_collection.find_one_and_update.assert_awaited_once()


@pytest.mark.asyncio
async def test_actualizar_perfil_inexistente(mock_collection):
    mock_collection.find_one_and_update = AsyncMock(return_value=None)

    update_data = PerfilUpdate(biografia="Nueva bio")
    perfil = await PerfilService.actualizar_perfil("no-existe", update_data)

    assert perfil is None


@pytest.mark.asyncio
async def test_archivar_perfil(mock_collection):
    empleado_id = "550e8400-e29b-41d4-a716-446655440001"
    mock_doc_archivado = {
        "empleadoId": empleado_id,
        "email": "juan.perez@empresa.com",
        "nombre": "Juan",
        "apellido": "Perez",
        "foto": None,
        "biografia": None,
        "telefono": None,
        "direccion": {"calle": None, "ciudad": None, "codigoPostal": None, "pais": None},
        "redesSociales": {"linkedin": None, "github": None},
        "archivado": True,
        "createdAt": datetime.now(timezone.utc),
        "updatedAt": datetime.now(timezone.utc),
    }
    mock_collection.find_one_and_update = AsyncMock(return_value=dict(mock_doc_archivado))

    perfil = await PerfilService.archivar_perfil(empleado_id)

    assert perfil is not None
    assert perfil.archivado is True

    mock_collection.find_one_and_update.assert_awaited_once()
    args, kwargs = mock_collection.find_one_and_update.call_args
    assert args[0] == {"empleadoId": empleado_id}
    assert kwargs["return_document"] is True
    # $set esta en args[1] (segundo argumento posicional)
    assert args[1]["$set"]["archivado"] is True
    assert "updatedAt" in args[1]["$set"]


@pytest.mark.asyncio
async def test_archivar_perfil_inexistente(mock_collection):
    mock_collection.find_one_and_update = AsyncMock(return_value=None)

    perfil = await PerfilService.archivar_perfil("no-existe")

    assert perfil is None


@pytest.mark.asyncio
async def test_sincronizar_datos(mock_collection):
    empleado_id = "550e8400-e29b-41d4-a716-446655440001"
    mock_doc = {
        "empleadoId": empleado_id,
        "email": "juan.perez@empresa.com",
        "nombre": "Juan Carlos",
        "apellido": "Perez Garcia",
        "foto": None,
        "biografia": None,
        "telefono": None,
        "direccion": {"calle": None, "ciudad": None, "codigoPostal": None, "pais": None},
        "redesSociales": {"linkedin": None, "github": None},
        "archivado": False,
        "createdAt": datetime.now(timezone.utc),
        "updatedAt": datetime.now(timezone.utc),
    }
    mock_collection.find_one_and_update = AsyncMock(return_value=dict(mock_doc))

    perfil = await PerfilService.sincronizar_datos(empleado_id, "Juan Carlos", "Perez Garcia")

    assert perfil is not None
    assert perfil.nombre == "Juan Carlos"
    assert perfil.apellido == "Perez Garcia"

    mock_collection.find_one_and_update.assert_awaited_once()
    args, kwargs = mock_collection.find_one_and_update.call_args
    assert args[0] == {"empleadoId": empleado_id}
    assert kwargs["return_document"] is True
    # $set esta en args[1] (segundo argumento posicional)
    assert args[1]["$set"]["nombre"] == "Juan Carlos"
    assert args[1]["$set"]["apellido"] == "Perez Garcia"
    assert "updatedAt" in args[1]["$set"]


@pytest.mark.asyncio
async def test_sincronizar_datos_inexistente(mock_collection):
    mock_collection.find_one_and_update = AsyncMock(return_value=None)

    perfil = await PerfilService.sincronizar_datos("no-existe", "Test", "Test")

    assert perfil is None
