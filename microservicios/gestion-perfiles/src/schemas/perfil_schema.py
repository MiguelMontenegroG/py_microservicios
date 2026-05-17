from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field


class DireccionSchema(BaseModel):
    calle: Optional[str] = None
    ciudad: Optional[str] = None
    codigoPostal: Optional[str] = None
    pais: Optional[str] = None


class RedesSocialesSchema(BaseModel):
    linkedin: Optional[str] = None
    github: Optional[str] = None


class PerfilCreate(BaseModel):
    """Schema para crear un perfil vacio (uso interno desde eventos)"""
    empleadoId: str
    email: str
    nombre: str = ""
    apellido: str = ""


class PerfilUpdate(BaseModel):
    """Schema para actualizar perfil (usuario autenticado)"""
    foto: Optional[str] = None
    biografia: Optional[str] = None
    telefono: Optional[str] = None
    direccion: Optional[DireccionSchema] = None
    redesSociales: Optional[RedesSocialesSchema] = None


class PerfilResponse(BaseModel):
    """Schema para respuesta HTTP"""
    empleadoId: str
    email: str
    nombre: str
    apellido: str
    foto: Optional[str] = None
    biografia: Optional[str] = None
    telefono: Optional[str] = None
    direccion: Optional[DireccionSchema] = None
    redesSociales: Optional[RedesSocialesSchema] = None
    archivado: bool
    createdAt: datetime
    updatedAt: datetime


class SuccessResponse(BaseModel):
    success: bool = True
    data: PerfilResponse
    timestamp: str = Field(default_factory=lambda: datetime.utcnow().isoformat() + "Z")


class ErrorDetail(BaseModel):
    code: str
    message: str
    details: Optional[dict] = None


class ErrorResponse(BaseModel):
    success: bool = False
    error: ErrorDetail
    timestamp: str = Field(default_factory=lambda: datetime.utcnow().isoformat() + "Z")
