"""Codifica y decodifica ficheros de androidx DataStore Preferences (preferences.proto)."""

from __future__ import annotations

import struct

KIND_FIELDS = {"bool": 1, "float": 2, "int": 3, "long": 4, "string": 5, "string_set": 6, "double": 7, "bytes": 8}
FIELD_KINDS = {field: kind for kind, field in KIND_FIELDS.items()}


def _varint(n: int) -> bytes:
    if n < 0:
        n += 1 << 64
    out = bytearray()
    while True:
        low = n & 0x7F
        n >>= 7
        if n:
            out.append(low | 0x80)
        else:
            out.append(low)
            return bytes(out)


def _read_varint(buf: bytes, pos: int) -> tuple[int, int]:
    shift = result = 0
    while True:
        byte = buf[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, pos
        shift += 7


def _signed64(n: int) -> int:
    return n - (1 << 64) if n >= 1 << 63 else n


def _fields(buf: bytes):
    pos = 0
    while pos < len(buf):
        key, pos = _read_varint(buf, pos)
        field, wire = key >> 3, key & 7
        if wire == 0:
            value, pos = _read_varint(buf, pos)
        elif wire == 1:
            value, pos = buf[pos : pos + 8], pos + 8
        elif wire == 2:
            length, pos = _read_varint(buf, pos)
            value, pos = buf[pos : pos + length], pos + length
        elif wire == 5:
            value, pos = buf[pos : pos + 4], pos + 4
        else:
            raise ValueError(f"prefs_pb: tipo de cable {wire} no soportado")
        yield field, wire, value


def _ld(field: int, payload: bytes) -> bytes:
    return _varint(field << 3 | 2) + _varint(len(payload)) + payload


def _decode_value(buf: bytes) -> tuple[str, object]:
    fields = list(_fields(buf))
    if len(fields) != 1:
        raise ValueError(f"prefs_pb: Value con {len(fields)} campos")
    field, _, raw = fields[0]
    kind = FIELD_KINDS[field]
    if kind == "bool":
        return kind, bool(raw)
    if kind in ("int", "long"):
        return kind, _signed64(raw)
    if kind == "float":
        return kind, struct.unpack("<f", raw)[0]
    if kind == "double":
        return kind, struct.unpack("<d", raw)[0]
    if kind == "string":
        return kind, bytes(raw).decode("utf-8")
    if kind == "bytes":
        return kind, bytes(raw)
    return kind, [bytes(item).decode("utf-8") for _, _, item in _fields(raw)]


def _encode_value(kind: str, value: object) -> bytes:
    field = KIND_FIELDS[kind]
    if kind == "bool":
        return _varint(field << 3) + _varint(1 if value else 0)
    if kind in ("int", "long"):
        return _varint(field << 3) + _varint(int(value))
    if kind == "float":
        return _varint(field << 3 | 5) + struct.pack("<f", value)
    if kind == "double":
        return _varint(field << 3 | 1) + struct.pack("<d", value)
    if kind == "string":
        return _ld(field, str(value).encode("utf-8"))
    if kind == "bytes":
        return _ld(field, bytes(value))
    return _ld(field, b"".join(_ld(1, item.encode("utf-8")) for item in value))


def decode(data: bytes) -> dict[str, tuple[str, object]]:
    prefs: dict[str, tuple[str, object]] = {}
    for field, wire, entry in _fields(data):
        if field != 1 or wire != 2:
            raise ValueError(f"prefs_pb: campo raíz inesperado {field}/{wire}")
        key, value = None, None
        for sub, _, raw in _fields(entry):
            if sub == 1:
                key = bytes(raw).decode("utf-8")
            elif sub == 2:
                value = _decode_value(raw)
        if key is None or value is None:
            raise ValueError("prefs_pb: entrada sin clave o sin valor")
        prefs[key] = value
    return prefs


def encode(prefs: dict[str, tuple[str, object]]) -> bytes:
    return b"".join(
        _ld(1, _ld(1, key.encode("utf-8")) + _ld(2, _encode_value(kind, value)))
        for key, (kind, value) in prefs.items()
    )
