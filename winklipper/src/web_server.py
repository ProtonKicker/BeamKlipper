"""
Embedded web server for serving Fluidd/Mainsail UIs and proxying to Moonraker.
Uses Tornado for async HTTP and WebSocket proxying.
"""
import os
import logging
import asyncio
import json
from pathlib import Path
from typing import Optional, Dict
from urllib.parse import urlparse

import tornado.web
import tornado.websocket
import tornado.httpserver
import tornado.ioloop
import websockets as ws_lib

logger = logging.getLogger(__name__)


class MoonrakerProxyHandler(tornado.web.RequestHandler):
    """Proxies HTTP requests to Moonraker."""

    def initialize(self, moonraker_url: str):
        self.moonraker_url = moonraker_url.rstrip("/")

    async def proxy_request(self, method: str):
        """Forward the request to Moonraker and return the response."""
        import aiohttp

        path = self.request.path
        url = f"{self.moonraker_url}{path}"
        if self.request.query:
            url += f"?{self.request.query}"

        headers = {
            k: v for k, v in self.request.headers.get_all()
            if k.lower() not in ("host", "connection")
        }

        body = self.request.body if self.request.body else None

        try:
            async with aiohttp.ClientSession() as session:
                async with session.request(
                    method, url, headers=headers, data=body, timeout=aiohttp.ClientTimeout(total=30)
                ) as resp:
                    self.set_status(resp.status)
                    for k, v in resp.headers.items():
                        if k.lower() not in ("transfer-encoding", "connection"):
                            self.set_header(k, v)
                    data = await resp.read()
                    self.write(data)
                    await self.finish()
        except Exception as e:
            logger.error(f"Proxy error: {e}")
            self.set_status(502)
            self.write(f"Bad Gateway: {e}")

    async def get(self):
        await self.proxy_request("GET")

    async def post(self):
        await self.proxy_request("POST")

    async def put(self):
        await self.proxy_request("PUT")

    async def delete(self):
        await self.proxy_request("DELETE")


class MoonrakerWebSocketProxy(tornado.websocket.WebSocketHandler):
    """Proxies WebSocket connections to Moonraker."""

    def initialize(self, moonraker_url: str):
        self.moonraker_url = moonraker_url.rstrip("/")
        self._backend_ws = None
        self._forward_task = None

    async def open(self):
        """Open connection to Moonraker WebSocket."""
        ws_url = self.moonraker_url.replace("http", "ws")
        path = self.request.path
        if self.request.query:
            ws_url += f"{path}?{self.request.query}"
        else:
            ws_url += path

        try:
            self._backend_ws = await ws_lib.connect(ws_url)
            logger.debug(f"WebSocket proxy connected to {ws_url}")

            # Start forwarding from backend to client
            self._forward_task = asyncio.create_task(self._forward_from_backend())
        except Exception as e:
            logger.error(f"WebSocket proxy connection failed: {e}")
            self.close()

    async def on_message(self, message):
        """Forward message from client to Moonraker."""
        if self._backend_ws:
            try:
                await self._backend_ws.send(message)
            except Exception as e:
                logger.error(f"Error forwarding to backend: {e}")

    async def on_close(self):
        """Clean up connections."""
        if self._forward_task:
            self._forward_task.cancel()
        if self._backend_ws:
            await self._backend_ws.close()

    async def _forward_from_backend(self):
        """Forward messages from Moonraker to client."""
        try:
            async for message in self._backend_ws:
                if self.ws_connection:
                    self.write_message(message)
        except Exception as e:
            logger.debug(f"Backend forward error: {e}")


class StaticFileHandlerWithFallback(tornado.web.StaticFileHandler):
    """Serves static files, falling back to index.html for SPA routing."""

    def initialize(self, path: str, default_filename: str = "index.html"):
        self.default_filename = default_filename
        super().initialize(path=path)

    async def get(self, path: str = "", include_body: bool = True):
        try:
            await super().get(path, include_body)
        except tornado.web.HTTPError as e:
            if e.status_code == 404:
                # SPA fallback - serve index.html for client-side routing
                await super().get(self.default_filename, include_body)
            else:
                raise


class WebUIManager:
    """Manages the embedded web server for a printer instance."""

    def __init__(
        self,
        instance_id: int,
        port: int,
        moonraker_port: int,
        ui_path: str,
        fluidd_path: Optional[Path] = None,
        mainsail_path: Optional[Path] = None,
    ):
        self.instance_id = instance_id
        self.port = port
        self.moonraker_port = moonraker_port
        self.ui_path = ui_path  # "fluidd" or "mainsail"
        self.fluidd_path = fluidd_path
        self.mainsail_path = mainsail_path

        self._app: Optional[tornado.web.Application] = None
        self._server: Optional[tornado.httpserver.HTTPServer] = None
        self._running = False

        self.moonraker_url = f"http://127.0.0.1:{moonraker_port}"

    def _get_ui_root(self) -> Optional[Path]:
        """Get the path to the selected UI's static files."""
        if self.ui_path == "fluidd" and self.fluidd_path:
            return self.fluidd_path
        elif self.ui_path == "mainsail" and self.mainsail_path:
            return self.mainsail_path
        return None

    def start(self):
        """Start the web server."""
        if self._running:
            return

        ui_root = self._get_ui_root()
        if not ui_root or not ui_root.exists():
            logger.error(f"UI root not found: {ui_root}")
            return

        handlers = [
            # Proxy to Moonraker API
            (r"/server/api/(.*)", MoonrakerProxyHandler, {"moonraker_url": self.moonraker_url}),
            (r"/websocket", MoonrakerWebSocketProxy, {"moonraker_url": self.moonraker_url}),
            # Direct Moonraker proxy for common paths
            (r"/printer/api/(.*)", MoonrakerProxyHandler, {"moonraker_url": self.moonraker_url}),
            # Static UI files
            (r"/(.*)", StaticFileHandlerWithFallback, {
                "path": str(ui_root),
                "default_filename": "index.html",
            }),
        ]

        self._app = tornado.web.Application(
            handlers,
            debug=False,
        )

        try:
            self._server = self._app.listen(self.port)
            self._running = True
            logger.info(f"Web UI server started on http://127.0.0.1:{self.port} (UI: {self.ui_path})")
        except Exception as e:
            logger.error(f"Failed to start web server: {e}")

    def stop(self):
        """Stop the web server."""
        if not self._running:
            return

        self._running = False
        if self._server:
            self._server.stop()
            logger.info(f"Web UI server stopped on port {self.port}")

    @property
    def url(self) -> str:
        return f"http://127.0.0.1:{self.port}"
