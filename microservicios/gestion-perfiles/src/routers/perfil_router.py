import logging
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Header, HTTPException, Request

from src.database import get_collection
from src.schemas.perfil_schema import (
    DireccionSchema,
    ErrorDetail,
    ErrorResponse,
    PerfilCreate,
    PerfilResponse,
    PerfilUpdate,
    RedesSocialesSchema,
    SuccessResponse,
)
from src.services.perfil_service import PerfilService
from src.services.rabbit_consumer import is_consumer_running

logger = logging.getLogger("gestion-perfiles")

router = APIRouter()


def _build_perfil_response(perfil) -> PerfilResponse:
    """Construir PerfilResponse a partir de un PerfilDB"""
    # Convertir objetos del modelo a schemas usando model_dump y creacion directa
    direccion_data = perfil.direccion
    if isinstance(direccion_data, dict):
        direccion = DireccionSchema(**direccion_data)
    elif not isinstance(direccion_data, DireccionSchema):
        direccion = DireccionSchema(**direccion_data.model_dump())
    else:
        direccion = direccion_data

    redes_data = perfil.redesSociales
    if isinstance(redes_data, dict):
        redes_sociales = RedesSocialesSchema(**redes_data)
    elif not isinstance(redes_data, RedesSocialesSchema):
        redes_sociales = RedesSocialesSchema(**redes_data.model_dump())
    else:
        redes_sociales = redes_data

    return PerfilResponse(
        empleadoId=perfil.empleadoId,
        email=perfil.email,
        nombre=perfil.nombre,
        apellido=perfil.apellido,
        foto=perfil.foto,
        biografia=perfil.biografia,
        telefono=perfil.telefono,
        direccion=direccion,
        redesSociales=redes_sociales,
        archivado=perfil.archivado,
        createdAt=perfil.createdAt,
        updatedAt=perfil.updatedAt,
    )


@router.get("/health")
async def health_check():
    """Health check del servicio"""
    dependencies_status = {}

    # Verificar MongoDB
    try:
        collection = get_collection()
        await collection.count_documents({})
        dependencies_status["mongodb"] = "UP"
    except Exception as e:
        logger.error(f"Health check - MongoDB fallo: {e}")
        dependencies_status["mongodb"] = "DOWN"

    # Verificar RabbitMQ
    try:
        if is_consumer_running():
            dependencies_status["rabbitmq"] = "UP"
        else:
            dependencies_status["rabbitmq"] = "DOWN"
    except Exception:
        dependencies_status["rabbitmq"] = "UNKNOWN"

    overall_status = "UP" if all(v == "UP" for v in dependencies_status.values()) else "DEGRADED"

    return {
        "status": overall_status,
        "service": "gestion-perfiles",
        "version": "1.0.0",
        "timestamp": datetime.now(timezone.utc).isoformat() + "Z",
        "dependencies": dependencies_status,
    }


@router.post("/profiles", status_code=201)
async def crear_perfil(data: PerfilCreate):
    """Crear un perfil vacio (uso interno desde eventos)"""
    try:
        perfil = await PerfilService.crear_perfil(data)
        return SuccessResponse(
            data=_build_perfil_response(perfil),
            timestamp=datetime.now(timezone.utc).isoformat() + "Z",
        )
    except Exception as e:
        error_msg = str(e)
        if "duplicate key" in error_msg.lower() or "E11000" in error_msg:
            raise HTTPException(
                status_code=409,
                detail=ErrorResponse(
                    error=ErrorDetail(
                        code="PERFIL_DUPLICADO",
                        message=f"Ya existe un perfil para el empleado {data.empleadoId}",
                    ),
                    timestamp=datetime.now(timezone.utc).isoformat() + "Z",
                ).model_dump(),
            )
        logger.error(f"Error creando perfil: {e}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=ErrorResponse(
                error=ErrorDetail(
                    code="ERROR_INTERNO",
                    message="Error al crear el perfil",
                ),
                timestamp=datetime.now(timezone.utc).isoformat() + "Z",
            ).model_dump(),
        )


@router.get("/profiles/{empleado_id}")
async def obtener_perfil(empleado_id: str):
    """Obtener perfil por empleadoId"""
    perfil = await PerfilService.obtener_perfil(empleado_id)
    if not perfil:
        raise HTTPException(
            status_code=404,
            detail=ErrorResponse(
                error=ErrorDetail(
                    code="PERFIL_NO_ENCONTRADO",
                    message=f"No se encontro perfil para el empleado {empleado_id}",
                ),
                timestamp=datetime.now(timezone.utc).isoformat() + "Z",
            ).model_dump(),
        )
    return SuccessResponse(
        data=_build_perfil_response(perfil),
        timestamp=datetime.now(timezone.utc).isoformat() + "Z",
    )


@router.put("/profiles/{empleado_id}")
async def actualizar_perfil(
    empleado_id: str,
    data: PerfilUpdate,
    x_empleado_id: str = Header(None, alias="X-Empleado-Id"),
):
    """Actualizar perfil del empleado autenticado"""
    # Verificar que el empleado solo edite su propio perfil
    if x_empleado_id and x_empleado_id != empleado_id:
        raise HTTPException(
            status_code=403,
            detail=ErrorResponse(
                error=ErrorDetail(
                    code="ACCESO_DENEGADO",
                    message="No puedes modificar el perfil de otro empleado",
                ),
                timestamp=datetime.now(timezone.utc).isoformat() + "Z",
            ).model_dump(),
        )

    perfil = await PerfilService.actualizar_perfil(empleado_id, data)
    if not perfil:
        raise HTTPException(
            status_code=404,
            detail=ErrorResponse(
                error=ErrorDetail(
                    code="PERFIL_NO_ENCONTRADO",
                    message=f"No se encontro perfil para el empleado {empleado_id}",
                ),
                timestamp=datetime.now(timezone.utc).isoformat() + "Z",
            ).model_dump(),
        )
    return SuccessResponse(
        data=_build_perfil_response(perfil),
        timestamp=datetime.now(timezone.utc).isoformat() + "Z",
    )
