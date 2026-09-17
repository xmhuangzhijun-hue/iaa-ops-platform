from fastapi import FastAPI

from app.api.openapi import TAGS, build_openapi
from app.api.v1 import api_router
from app.core.config import settings
from app.core.errors import install_error_handlers


def create_app() -> FastAPI:
    app = FastAPI(
        title="IAA 投放运营中台 API",
        version=settings.version,
        description="快应用 IAA 投放运营中台的前后端契约。示例数据均为虚构。",
        openapi_tags=TAGS,
        separate_input_output_schemas=False,
        docs_url="/api/docs",
        redoc_url=None,
        openapi_url="/api/openapi.json",
    )
    install_error_handlers(app)
    app.include_router(api_router, prefix="/api/v1")
    app.openapi = lambda: build_openapi(app)  # type: ignore[method-assign]
    return app


app = create_app()
