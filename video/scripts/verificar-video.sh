#!/usr/bin/env bash
set -euo pipefail

f="$1"
video=$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height,r_frame_rate -of default=nw=1 "$f")
audio=$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of default=nw=1:nk=1 "$f")
duracion=$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$f")

for esperado in codec_name=h264 width=1920 height=1080 r_frame_rate=30/1; do
  grep -qx "$esperado" <<<"$video" || { echo "$f: falta $esperado en: $video" >&2; exit 1; }
done
[ "$audio" = "aac" ] || { echo "$f: audio '$audio', se esperaba aac" >&2; exit 1; }
awk -v d="$duracion" 'BEGIN { exit !(d >= 45 && d <= 60) }' || { echo "$f: dura $duracion s, fuera de 45-60" >&2; exit 1; }
echo "ok $f: h264 1920x1080 30 fps, aac, $duracion s"
