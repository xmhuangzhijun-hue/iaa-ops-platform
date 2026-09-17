from fastapi import APIRouter

from app.api.v1 import admin, auth, reports, system

api_router = APIRouter()
for module in (system, auth, reports, admin):
    api_router.include_router(module.router)
