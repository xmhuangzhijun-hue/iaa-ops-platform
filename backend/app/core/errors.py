from typing import NoReturn

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.schemas.common import Error, ErrorCode, FieldError

PROBLEM_MEDIA_TYPE = "application/problem+json"
STATUS_CODES: dict[int, ErrorCode] = {
    401: "AUTH_REQUIRED", 403: "FORBIDDEN", 404: "NOT_FOUND", 405: "METHOD_NOT_ALLOWED",
    409: "CONFLICT", 413: "PAYLOAD_TOO_LARGE", 422: "VALIDATION_FAILED", 500: "INTERNAL_ERROR",
    501: "NOT_IMPLEMENTED",
}


class ApiError(Exception):
    def __init__(self, status: int, code: ErrorCode, title: str, detail: str | None = None) -> None:
        super().__init__(title)
        self.problem = Error(status=status, code=code, title=title, detail=detail)


def problem_response(problem: Error) -> JSONResponse:
    return JSONResponse(
        problem.model_dump(mode="json", exclude_none=True),
        status_code=problem.status,
        media_type=PROBLEM_MEDIA_TYPE,
    )


def not_implemented(phase: int) -> NoReturn:
    raise ApiError(501, "NOT_IMPLEMENTED", "接口尚未实现", f"契约已确定，计划在第 {phase} 阶段实现。")


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiError)
    async def handle_api_error(_: Request, exc: ApiError) -> JSONResponse:
        return problem_response(exc.problem)

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        errors = [
            FieldError(
                field=".".join(str(part) for part in error["loc"][1:]) or str(error["loc"][0]),
                message=error["msg"],
            )
            for error in exc.errors()
        ]
        return problem_response(
            Error(status=422, code="VALIDATION_FAILED", title="请求参数校验失败", errors=errors)
        )

    @app.exception_handler(StarletteHTTPException)
    async def handle_http_error(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        code = STATUS_CODES.get(exc.status_code, "HTTP_ERROR")
        return problem_response(Error(status=exc.status_code, code=code, title=str(exc.detail)))
