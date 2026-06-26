import re


class ErrorGroup:
    Internal = 1
    Delimiting = 100
    PartHeaders = 200
    UnexpectedPart = 300


class _Parser:
    def __init__(self, delimiter, ender, strict):
        self._delimiter = delimiter
        self._ender = ender
        self._strict = strict
        self._targets = {}
        self._buffer = b""
        self._in_part = False
        self._current_name = None
        self._current_targets = None
        self._unexpected_name = None

    def register(self, name, target):
        if self._in_part:
            return
        if name not in self._targets:
            self._targets[name] = []
        self._targets[name].append(target)

    @property
    def unexpected_part_name(self):
        return self._unexpected_name

    def data_received(self, data):
        self._buffer += data
        result = 0

        while True:
            if not self._in_part:
                idx = self._buffer.find(self._delimiter)
                if idx == -1:
                    break
                after = idx + len(self._delimiter)
                if self._buffer[after - 2:after] == b"--":
                    self._buffer = b""
                    return 0
                rest = self._buffer[after:]
                hdr_end = rest.find(b"\r\n\r\n")
                if hdr_end == -1:
                    break
                headers_raw = rest[:hdr_end].decode("utf-8", errors="replace")
                self._buffer = rest[hdr_end + 4:]
                name, filename, content_type = self._parse_headers(headers_raw)
                if name is None:
                    if self._strict:
                        self._unexpected_name = name
                        return ErrorGroup.UnexpectedPart
                    self._current_targets = []
                else:
                    self._current_targets = self._targets.get(name, [])
                self._current_name = name
                for t in self._current_targets:
                    if filename is not None:
                        t.multipart_filename = filename
                    if content_type is not None:
                        t.multipart_content_type = content_type
                    t.start()
                self._in_part = True
            else:
                idx = self._buffer.find(b"\r\n" + self._delimiter[2:])
                if idx == -1:
                    partial = self._partial_boundary_match(self._buffer)
                    if partial > 0:
                        chunk = self._buffer[:-partial]
                    else:
                        chunk = self._buffer
                        self._buffer = b""
                    for t in self._current_targets:
                        t.data_received(chunk)
                    if partial > 0:
                        self._buffer = self._buffer[-partial:]
                    else:
                        self._buffer = b""
                    break
                chunk = self._buffer[:idx]
                for t in self._current_targets:
                    t.data_received(chunk)
                for t in self._current_targets:
                    t.finish()
                after = idx + 2 + len(self._delimiter) - 2
                self._buffer = self._buffer[after:]
                if len(self._buffer) >= 2 and self._buffer[:2] == b"--":
                    self._buffer = b""
                    self._in_part = False
                    return 0
                self._in_part = False

        return result

    def _partial_boundary_match(self, buf):
        for i in range(1, min(len(buf) + 1, len(self._delimiter) + 2)):
            if buf[-i:] == (b"\r\n" + self._delimiter[2:])[:i]:
                return i
        return 0

    def _parse_headers(self, headers_raw):
        name = None
        filename = None
        content_type = None
        for line in headers_raw.split("\r\n"):
            line = line.strip()
            if line.lower().startswith("content-disposition:"):
                val = line[len("content-disposition:"):].strip()
                for part in val.split(";"):
                    part = part.strip()
                    if part.lower().startswith('name="'):
                        name = part[6:-1]
                    elif part.lower().startswith("name='"):
                        name = part[6:-1]
                    elif part.lower().startswith("filename="):
                        fv = part[9:]
                        if fv.startswith('"') and fv.endswith('"'):
                            fv = fv[1:-1]
                        filename = fv
            elif line.lower().startswith("content-type:"):
                content_type = line[len("content-type:"):].strip()
        return name, filename, content_type
