"""Configuracion global de pytest para gestion-perfiles"""
import sys
from pathlib import Path

# Agregar la raiz del proyecto al path para imports absolutos
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))
